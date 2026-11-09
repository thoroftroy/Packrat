package dev.wonderdesk.shared

import android.system.Os
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.channels.OverlappingFileLockException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Same authenticated v2 envelope as apps/vault/vault_storage.py. Not independently audited. */
class EncryptedVault(private val file: File) {
    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        const val ITERATIONS = 600_000
        private val random = SecureRandom()
        private fun encode(value: ByteArray) = Base64.encodeToString(value, Base64.NO_WRAP)
        private fun decode(value: String, bytes: Int? = null): ByteArray {
            require(value.length % 4 == 0 && value.matches(Regex("[A-Za-z0-9+/]*={0,2}"))) { "Invalid vault encoding" }
            val decoded = Base64.decode(value, Base64.NO_WRAP)
            require(encode(decoded) == value && (bytes == null || decoded.size == bytes)) { "Invalid vault field length/encoding" }
            return decoded
        }
        private fun digest(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
        private fun password(value: String) { require(value.length >= 12 && value.isNotBlank()) { "Use a master password of at least 12 characters; no recovery exists" } }
        private fun derive(value: String, salt: ByteArray, iterations: Int): ByteArray {
            val spec = PBEKeySpec(value.toCharArray(), salt, iterations, 256)
            return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        }
        private fun header(salt: ByteArray) = JSONObject().put("cipher", "AES-256-GCM").put("iterations", ITERATIONS)
            .put("kdf", "PBKDF2-HMAC-SHA256").put("salt", encode(salt)).put("version", 2)
        private fun aad(header: JSONObject): ByteArray {
            // Android's JSON writer may escape '/', while Python's canonical
            // writer does not. All interpolated fields are validated ASCII.
            return ("{\"cipher\":\"AES-256-GCM\",\"iterations\":" + header.getInt("iterations") +
                ",\"kdf\":\"PBKDF2-HMAC-SHA256\",\"salt\":\"" + header.getString("salt") + "\",\"version\":2}").toByteArray(Charsets.UTF_8)
        }
        private fun validateEntries(entries: JSONArray): JSONArray {
            require(entries.length() <= 10_000) { "Too many vault entries" }
            for (index in 0 until entries.length()) {
                val entry = entries.getJSONObject(index)
                require(entry.keys().asSequence().toSet() == setOf("title", "user", "pw")) { "Unsupported vault entry" }
                for (field in listOf("title", "user", "pw")) require(entry.get(field) is String && entry.getString(field).length <= 100_000) { "Invalid vault entry" }
            }
            require(entries.toString().toByteArray(Charsets.UTF_8).size < MAX_BYTES / 2) { "Vault exceeds its safety limit" }
            return JSONArray(entries.toString())
        }
        private fun seal(entries: JSONArray, key: ByteArray, header: JSONObject): ByteArray {
            val nonce = ByteArray(12).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad(header))
            val plain = JSONObject().put("entries", validateEntries(entries)).toString().toByteArray(Charsets.UTF_8)
            return JSONObject(header.toString()).put("nonce", encode(nonce)).put("ciphertext", encode(cipher.doFinal(plain))).toString().toByteArray(Charsets.UTF_8)
        }
        private data class Opened(val entries: JSONArray, val key: ByteArray, val header: JSONObject)
        private fun open(raw: ByteArray, master: String): Opened {
            require(raw.size <= MAX_BYTES) { "Vault exceeds its safety limit" }
            val obj = JSONObject(raw.toString(Charsets.UTF_8))
            require(obj.keys().asSequence().toSet() == setOf("version", "cipher", "kdf", "iterations", "salt", "nonce", "ciphertext")) { "Unsupported vault format; original remains unchanged" }
            require(obj.get("version") is Int && obj.getInt("version") == 2 && obj.getString("cipher") == "AES-256-GCM" && obj.getString("kdf") == "PBKDF2-HMAC-SHA256") { "Unsupported vault cryptography" }
            require(obj.get("iterations") is Int && obj.getInt("iterations") in ITERATIONS..2_000_000) { "Unsupported key derivation parameters" }
            val salt = decode(obj.getString("salt"), 16)
            // Insertion order is deliberate: matches Python's sorted, compact AAD.
            val metadata = JSONObject().put("cipher", obj.getString("cipher")).put("iterations", obj.getInt("iterations"))
                .put("kdf", obj.getString("kdf")).put("salt", obj.getString("salt")).put("version", 2)
            val key = derive(master, salt, obj.getInt("iterations"))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, decode(obj.getString("nonce"), 12)))
            cipher.updateAAD(aad(metadata))
            val plain = try { cipher.doFinal(decode(obj.getString("ciphertext"))) } catch (error: AEADBadTagException) {
                key.fill(0); throw IllegalArgumentException("Wrong password or altered/corrupt vault; no data changed", error)
            }
            val data = JSONObject(plain.toString(Charsets.UTF_8))
            plain.fill(0)
            require(data.keys().asSequence().toSet() == setOf("entries")) { "Unsupported authenticated vault contents" }
            return Opened(validateEntries(data.getJSONArray("entries")), key, metadata)
        }
    }

    private val atomic = AtomicFile(file)
    private var key: ByteArray? = null
    private var metadata: JSONObject? = null
    private var expected: String? = null
    private var contents = JSONArray()
    val exists get() = file.exists() || File(file.path + ".bak").exists()
    val unlocked get() = key != null
    @Synchronized fun entries(): JSONArray = JSONArray(contents.toString())

    private fun <T> disk(block: () -> T): T {
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()) { "Vault directory unavailable" }
        val lockFile = File(file.parentFile, file.name + ".lock")
        return java.io.RandomAccessFile(lockFile, "rw").use { handle ->
            val lock = try { handle.channel.tryLock() } catch (_: OverlappingFileLockException) { null }
            check(lock != null) { "Another vault operation is active; retry after it finishes" }
            lock.use { block() }
        }
    }

    private fun read(): ByteArray = atomic.openRead().use { input ->
        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size() + count <= MAX_BYTES) { "Vault exceeds safety limit" }; output.write(buffer, 0, count) }
        output.toByteArray()
    }

    private fun publish(raw: ByteArray, before: String?) {
        val current = if (exists) digest(read()) else null
        check(current == before) { "Vault changed on disk; lock and reopen before saving. Original preserved" }
        val stream = atomic.startWrite()
        try { stream.write(raw); atomic.finishWrite(stream); Os.chmod(file.path, 0x180) /* 0600 */ }
        catch (error: Exception) { atomic.failWrite(stream); throw error }
        expected = digest(raw)
    }

    @Synchronized fun lock() { key?.fill(0); key = null; metadata = null; expected = null; contents = JSONArray() }
    @Synchronized fun create(master: String) = disk {
        password(master); check(!exists) { "Vault already exists; it was not replaced" }
        val header = header(ByteArray(16).also { random.nextBytes(it) })
        val derived = derive(master, decode(header.getString("salt"), 16), ITERATIONS)
        publish(seal(JSONArray(), derived, header), null)
        key = derived; metadata = header; contents = JSONArray()
    }
    @Synchronized fun unlock(master: String) = disk {
        lock()
        val raw = read(); val value = open(raw, master)
        key = value.key; metadata = value.header; contents = value.entries; expected = digest(raw)
    }
    @Synchronized fun save(entries: JSONArray) = disk {
        val derived = key ?: error("Vault is locked")
        val candidate = validateEntries(entries)
        publish(seal(candidate, derived, metadata!!), expected)
        contents = candidate
    }
    @Synchronized fun rotate(current: String, replacement: String) = disk {
        check(unlocked) { "Unlock the vault first" }; password(replacement)
        val raw = read(); val authenticated = open(raw, current); authenticated.key.fill(0)
        check(digest(raw) == expected) { "Vault changed; lock and reopen before rotating" }
        val header = header(ByteArray(16).also { random.nextBytes(it) })
        val derived = derive(replacement, decode(header.getString("salt"), 16), ITERATIONS)
        publish(seal(contents, derived, header), expected)
        key?.fill(0); key = derived; metadata = header
    }
    @Synchronized fun exportEncrypted(): ByteArray = disk { check(exists) { "No vault to export" }; read() }
    @Synchronized fun importNew(raw: ByteArray, master: String) = disk {
        check(!exists) { "Import requires an empty vault; existing credentials are never replaced" }
        val value = open(raw, master) // Authenticate before writing any destination bytes.
        publish(raw, null); key = value.key; metadata = value.header; contents = value.entries
    }
}
