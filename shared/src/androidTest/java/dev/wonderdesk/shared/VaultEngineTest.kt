package dev.wonderdesk.shared

import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Public test secrets only; fixture data never includes owner credentials. */
@RunWith(AndroidJUnit4::class)
class VaultEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val password = "test-only interop Ω café password"
    private val replacement = "rotated public test-only master"
    private fun contents(title: String = "fixture Ω") = JSONArray().put(JSONObject().put("title", title).put("user", "fixture-café").put("pw", "public test-only secret"))
    private fun rejects(block: () -> Unit) { var failed = false; try { block() } catch (_: Exception) { failed = true }; assertTrue("Operation should be refused", failed) }
    private fun fixture(block: (File, EncryptedVault) -> Unit) {
        val directory = File(context.cacheDir, "vault-test-" + UUID.randomUUID()).apply { mkdirs() }
        try { val file = File(directory, "vault.json"); block(file, EncryptedVault(file)) } finally { directory.deleteRecursively() }
    }

    @Test fun authenticatedRoundTripPrivateModeAndFreshNonce() = fixture { file, store ->
        store.create(password); store.save(contents())
        val original = file.readBytes()
        assertFalse(original.toString(Charsets.UTF_8).contains("public test-only secret"))
        assertEquals(384, Os.stat(file.path).st_mode and 511) // 0600
        store.save(contents())
        assertNotEquals(JSONObject(original.toString(Charsets.UTF_8)).getString("nonce"), JSONObject(file.readText()).getString("nonce"))
        store.lock(); assertFalse(store.unlocked); assertEquals(0, store.entries().length())
        store.unlock(password)
        assertEquals("fixture-café", store.entries().getJSONObject(0).getString("user"))
    }

    @Test fun wrongPasswordAndTamperingNeverWriteOrRemainUnlocked() = fixture { file, store ->
        store.create(password); store.save(contents()); val original = file.readBytes()
        rejects { store.unlock("incorrect master password") }
        assertFalse(store.unlocked); assertArrayEquals(original, file.readBytes())
        val altered = JSONObject(original.toString(Charsets.UTF_8)).put("iterations", EncryptedVault.ITERATIONS + 1).toString().toByteArray()
        file.writeBytes(altered)
        rejects { store.unlock(password) }; assertFalse(store.unlocked); assertArrayEquals(altered, file.readBytes())
    }

    @Test fun masterRotationReauthenticatesAndPreservesContents() = fixture { file, store ->
        store.create(password); store.save(contents()); val original = file.readBytes()
        rejects { store.rotate("wrong master", replacement) }; assertArrayEquals(original, file.readBytes())
        store.rotate(password, replacement)
        assertNotEquals(JSONObject(original.toString(Charsets.UTF_8)).getString("salt"), JSONObject(file.readText()).getString("salt"))
        rejects { store.unlock(password) }; store.unlock(replacement)
        assertEquals("public test-only secret", store.entries().getJSONObject(0).getString("pw"))
    }

    @Test fun competingWriterIsDetectedAndCurrentBytesSurvive() = fixture { file, first ->
        first.create(password); first.save(contents())
        val second = EncryptedVault(file); second.unlock(password); second.save(contents("latest writer"))
        val current = file.readBytes(); rejects { first.save(contents("stale overwrite")) }
        assertArrayEquals(current, file.readBytes())
        first.lock(); first.unlock(password); assertEquals("latest writer", first.entries().getJSONObject(0).getString("title"))
    }

    @Test fun linuxV2ImportAndAndroidExportInteroperate() = fixture { file, store ->
        val raw = InstrumentationRegistry.getInstrumentation().context.assets.open("linux-vault-v2.json").use { it.readBytes() }
        store.importNew(raw, password)
        assertEquals("Interop fixture Ω", store.entries().getJSONObject(0).getString("title"))
        val before = file.readBytes(); rejects { store.importNew(raw, password) }; assertArrayEquals(before, file.readBytes())
        // Re-encrypt with Android's own nonce/cipher/AAD implementation. The
        // external runner decrypts this using Linux's actual VaultStore.
        store.save(store.entries())
        val directory = File(context.getExternalFilesDir(null), "vault-interop").apply { mkdirs() }
        File(directory, "android-vault-v2.json").writeBytes(store.exportEncrypted())
        File(directory, "fixture-info.json").writeText(JSONObject().put("test_only", true).put("package", context.packageName).put("password", password).toString(2))
    }

    @Test fun invalidCreationAndNonemptyImportRefused() = fixture { file, store ->
        rejects { store.create("short") }; assertFalse(file.exists())
        store.create(password); val raw = file.readBytes()
        rejects { store.create(replacement) }; assertArrayEquals(raw, file.readBytes())
        rejects { store.importNew(raw, password) }; assertArrayEquals(raw, file.readBytes())
    }
}
