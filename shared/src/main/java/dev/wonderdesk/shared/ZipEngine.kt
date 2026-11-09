package dev.wonderdesk.shared

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ZipItem(val path: String, val bytes: Long, val directory: Boolean)

/** Uses java.util.zip, not a home-made codec. Every entry is validated before any extraction. */
object ZipEngine {
    const val MAX_ENTRIES = 2000
    const val MAX_BYTES = 256L * 1024 * 1024
    const val MAX_ENTRY = 64L * 1024 * 1024

    fun safePath(name: String): List<String> {
        require(name.length in 1..1024 && !name.startsWith('/') && '\\' !in name && ':' !in name && !name.any { it.code < 32 }) { "Unsafe archive path" }
        val path = name.removeSuffix("/").split('/')
        require(path.size <= 32 && path.all { it.isNotEmpty() && it != "." && it != ".." }) { "Unsafe archive path" }
        return path
    }

    fun inspect(input: InputStream): List<ZipItem> {
        val entries = mutableListOf<ZipItem>()
        val names = mutableSetOf<String>()
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            val buffer = ByteArray(32768)
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = safePath(entry.name).joinToString("/")
                require(names.add(name)) { "Duplicate archive path" }
                require(entries.size < MAX_ENTRIES) { "Archive has too many entries" }
                var size = 0L
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    size += n; total += n
                    require(size <= MAX_ENTRY && total <= MAX_BYTES) { "Archive exceeds extraction limits" }
                }
                zip.closeEntry() // validates the ZIP entry CRC, including data descriptors
                entries += ZipItem(name, size, entry.isDirectory)
            }
        }
        require(entries.isNotEmpty()) { "Empty, unsupported or invalid ZIP" }
        val files = entries.filter { !it.directory }.map { it.path }.toSet()
        require(entries.none { entry ->
            val segments = entry.path.split('/')
            (1 until segments.size).any { segments.take(it).joinToString("/") in files }
        }) { "File/directory archive path conflict" }
        return entries
    }

    fun create(context: Context, sources: List<Uri>, output: OutputStream) {
        require(sources.isNotEmpty() && sources.size <= MAX_ENTRIES) { "Choose between 1 and $MAX_ENTRIES documents" }
        val names = mutableSetOf<String>()
        var total = 0L
        ZipOutputStream(output.buffered()).use { zip ->
            for (uri in sources) {
                val name = context.displayName(uri)
                require(safePath(name).size == 1 && names.add(name)) { "Duplicate or unsafe name: $name. Rename one source first." }
                zip.putNextEntry(ZipEntry(name))
                var size = 0L
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        size += n; total += n
                        require(size <= MAX_ENTRY && total <= MAX_BYTES) { "Selected files exceed archive limits" }
                        zip.write(buffer, 0, n)
                    }
                }
                zip.closeEntry()
            }
        }
    }

    fun extract(context: Context, source: Uri, destination: Uri): Pair<String, Int> {
        val resolver = context.contentResolver
        val reviewed = inspect(resolver.openInputStream(source)!!)
        val parent = DocumentFile.fromTreeUri(context, destination) ?: error("Destination unavailable")
        require(parent.canWrite()) { "Destination is not writable" }
        var suffix = 1
        var name = "Wonderdesk restored"
        while (parent.findFile(name) != null) { name = "Wonderdesk restored ${suffix++}"; require(suffix < 10000) }
        val root = parent.createDirectory(name) ?: error("Cannot create a new destination folder")
        try {
            ZipInputStream(resolver.openInputStream(source)!!.buffered()).use { zip ->
                var index = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val parts = safePath(entry.name)
                    val expected = reviewed.getOrNull(index++) ?: error("Archive changed during extraction")
                    require(parts.joinToString("/") == expected.path && entry.isDirectory == expected.directory) { "Archive changed during extraction" }
                    var current = root
                    for (segment in parts.dropLast(1)) current = current.findFile(segment) ?: current.createDirectory(segment) ?: error("Cannot create subfolder")
                    if (entry.isDirectory) {
                        if (current.findFile(parts.last()) == null) check(current.createDirectory(parts.last()) != null)
                    } else {
                        require(current.findFile(parts.last()) == null) { "Destination collision" }
                        val file = current.createFile("application/octet-stream", parts.last()) ?: error("Cannot create destination")
                        val writtenHash = MessageDigest.getInstance("SHA-256")
                        var size = 0L
                        resolver.openOutputStream(file.uri, "w")!!.use { out ->
                            val buffer = ByteArray(32768)
                            while (true) {
                                val n = zip.read(buffer); if (n < 0) break
                                size += n
                                require(size <= expected.bytes && size <= MAX_ENTRY) { "Archive changed or expanded beyond limits" }
                                writtenHash.update(buffer, 0, n); out.write(buffer, 0, n)
                            }
                        }
                        require(size == expected.bytes) { "Incomplete extracted file" }
                        val savedHash = MessageDigest.getInstance("SHA-256")
                        resolver.openInputStream(file.uri)!!.use { input ->
                            val buffer = ByteArray(32768)
                            while (true) { val n = input.read(buffer); if (n < 0) break; savedHash.update(buffer, 0, n) }
                        }
                        require(writtenHash.digest().contentEquals(savedHash.digest())) { "Restored file verification failed" }
                    }
                    zip.closeEntry()
                }
                require(index == reviewed.size) { "Archive changed during extraction" }
            }
        } catch (e: Exception) {
            val removed = runCatching { root.delete() }.getOrDefault(false)
            throw IllegalStateException("Restore failed. ${if (removed) "New partial folder removed." else "Partial folder may remain: $name."} ${e.message}", e)
        }
        return name to reviewed.count { !it.directory }
    }
}
