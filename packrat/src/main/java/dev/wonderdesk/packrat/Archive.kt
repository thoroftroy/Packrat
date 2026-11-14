package dev.wonderdesk.packrat

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class Format(val label: String) { Zip("ZIP"), Tar("TAR"), TarGz("TAR.GZ"), Gz("GZIP"), SevenZ("7Z"), Rar("RAR") }

data class Entry(val path: String, val size: Long, val compressed: Long, val time: Long, val dir: Boolean) {
    val name get() = path.trimEnd('/').substringAfterLast('/')
    val parent get() = path.trimEnd('/').substringBeforeLast('/', "")
}

class ArchiveError(msg: String) : Exception(msg)

/** The archive is encrypted and the password is missing or wrong. */
class PasswordNeeded(val wrong: Boolean) : Exception(if (wrong) "That password didn't work" else "This archive needs a password")

const val MAX_ENTRIES = 50_000
const val MAX_TOTAL = 8L * 1024 * 1024 * 1024

object Archives {
    /** Password for the archive being opened (asked for when needed). */
    @Volatile var password: String? = null

    fun detect(name: String, head: ByteArray): Format? {
        val n = name.lowercase()
        return when {
            head.size >= 6 && head[0] == '7'.code.toByte() && head[1] == 'z'.code.toByte() && head[2] == 0xBC.toByte() -> Format.SevenZ
            head.size >= 4 && head[0] == 'R'.code.toByte() && head[1] == 'a'.code.toByte() && head[2] == 'r'.code.toByte() && head[3] == '!'.code.toByte() -> Format.Rar
            n.endsWith(".7z") -> Format.SevenZ
            n.endsWith(".rar") -> Format.Rar
            head.size >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() -> Format.Zip
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> Format.TarGz
            head.size >= 2 && head[0] == 0x1f.toByte() && head[1] == 0x8b.toByte() -> if (n.endsWith(".gz")) Format.Gz else Format.TarGz
            n.endsWith(".tar") || (head.size >= 262 && String(head, 257, 5) == "ustar") -> Format.Tar
            n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".apk") -> Format.Zip
            else -> null
        }
    }

    /** Streams every entry; [onEntry] may read the current entry's data from the given stream. */
    fun walk(input: InputStream, format: Format, gzName: String = "file", onEntry: (Entry, InputStream) -> Unit) {
        var count = 0
        var total = 0L
        fun check(e: Entry) {
            if (++count > MAX_ENTRIES) throw ArchiveError("This archive has more than ${"%,d".format(MAX_ENTRIES)} entries")
            total += e.size
            if (total > MAX_TOTAL) throw ArchiveError("This archive expands to more than 8 GB")
            if (e.path.startsWith("/") || e.path.split('/').any { it == ".." }) throw ArchiveError("Blocked an unsafe path: ${e.path}")
        }
        when (format) {
            Format.Zip -> net.lingala.zip4j.io.inputstream.ZipInputStream(BufferedInputStream(input), password?.toCharArray()).use { z ->
                while (true) {
                    val ze = try { z.nextEntry } catch (ex: net.lingala.zip4j.exception.ZipException) {
                        if (ex.type == net.lingala.zip4j.exception.ZipException.Type.WRONG_PASSWORD || ex.message?.contains("password", true) == true) throw PasswordNeeded(password != null) else throw ex
                    } ?: break
                    if (ze.isEncrypted && password == null) throw PasswordNeeded(false)
                    val e = Entry(ze.fileName, ze.uncompressedSize.coerceAtLeast(0), ze.compressedSize.coerceAtLeast(0), net.lingala.zip4j.util.Zip4jUtil.dosToExtendedEpochTme(ze.lastModifiedTime), ze.isDirectory)
                    check(e); onEntry(e, z)
                }
            }
            Format.SevenZ -> {
                // 7z needs to jump around the file, so it's read from a temporary copy.
                val tmp = java.io.File.createTempFile("packrat", ".7z"); try {
                    tmp.outputStream().use { input.copyTo(it) }
                    val f = try { org.apache.commons.compress.archivers.sevenz.SevenZFile.builder().setFile(tmp).apply { password?.let { setPassword(it.toCharArray()) } }.get() }
                        catch (ex: org.apache.commons.compress.PasswordRequiredException) { throw PasswordNeeded(false) }
                    f.use { z -> while (true) {
                        val ze = try { z.nextEntry } catch (ex: org.apache.commons.compress.PasswordRequiredException) { throw PasswordNeeded(false) } catch (ex: java.io.IOException) { if (password != null) throw PasswordNeeded(true) else throw ex } ?: break
                        val e = Entry(ze.name, ze.size, ze.size, if (ze.hasLastModifiedDate) ze.lastModifiedDate.time else 0, ze.isDirectory)
                        check(e); onEntry(e, object : InputStream() { override fun read() = z.read(); override fun read(b: ByteArray, o: Int, l: Int) = z.read(b, o, l) })
                    } }
                } finally { tmp.delete() }
            }
            Format.Rar -> {
                val a = try { com.github.junrar.Archive(input, password) } catch (ex: com.github.junrar.exception.UnsupportedRarV5Exception) { throw ArchiveError("This is a RAR 5 archive. Packrat can open RAR 4 and older; RAR 5 support is coming") }
                a.use { r ->
                    if (r.isEncrypted && password == null) throw PasswordNeeded(false)
                    for (h in r.fileHeaders) {
                        if (h.isEncrypted && password == null) throw PasswordNeeded(false)
                        val e = Entry(h.fileName.replace('\\', '/'), h.fullUnpackSize, h.fullPackSize, h.mTime?.time ?: 0, h.isDirectory)
                        check(e); onEntry(e, r.getInputStream(h))
                    }
                }
            }
            Format.Gz -> GZIPInputStream(BufferedInputStream(input)).use { g ->
                val e = Entry(gzName.removeSuffix(".gz"), -1, -1, 0, false); check(e.copy(size = 0)); onEntry(e, g)
            }
            Format.Tar, Format.TarGz -> {
                val raw = if (format == Format.TarGz) GZIPInputStream(BufferedInputStream(input)) else BufferedInputStream(input)
                raw.use { t -> readTar(t) { e, s -> check(e); onEntry(e, s) } }
            }
        }
    }

    private fun readTar(input: InputStream, onEntry: (Entry, InputStream) -> Unit) {
        val header = ByteArray(512)
        var longName: String? = null
        while (true) {
            if (!readFully(input, header)) break
            if (header.all { it == 0.toByte() }) break
            fun str(off: Int, len: Int) = String(header, off, len, Charsets.UTF_8).substringBefore('\u0000').trim()
            val size = str(124, 12).trim().toLongOrNull(8) ?: 0L
            val type = header[156].toInt().toChar()
            val prefix = str(345, 155)
            var name = longName ?: (if (prefix.isNotEmpty()) "$prefix/" else "") + str(0, 100)
            longName = null
            val mtime = (str(136, 12).toLongOrNull(8) ?: 0L) * 1000
            val body = BoundedStream(input, size)
            when (type) {
                'L' -> { longName = body.readBytes().toString(Charsets.UTF_8).trimEnd('\u0000'); body.drain(); skipPad(input, size); continue }
                'x', 'g' -> { body.drain(); skipPad(input, size); continue }
            }
            val dir = type == '5' || name.endsWith("/")
            if (dir && !name.endsWith("/")) name += "/"
            if (type == '0' || type == '\u0000' || dir) onEntry(Entry(name, size, size, mtime, dir), body)
            body.drain(); skipPad(input, size)
        }
    }

    private fun skipPad(i: InputStream, size: Long) { val pad = (512 - size % 512) % 512; var left = pad; while (left > 0) { val s = i.skip(left); if (s <= 0) { if (i.read() < 0) break; left-- } else left -= s } }
    private fun readFully(i: InputStream, b: ByteArray): Boolean { var off = 0; while (off < b.size) { val n = i.read(b, off, b.size - off); if (n < 0) return false; off += n }; return true }

    private class BoundedStream(val inner: InputStream, var left: Long) : InputStream() {
        override fun read(): Int = if (left <= 0) -1 else inner.read().also { if (it >= 0) left-- }
        override fun read(b: ByteArray, off: Int, len: Int): Int { if (left <= 0) return -1; val n = inner.read(b, off, minOf(len.toLong(), left).toInt()); if (n > 0) left -= n; return n }
        fun drain() { val buf = ByteArray(8192); while (read(buf) > 0) {} }
        override fun close() {}
    }

    fun list(context: Context, uri: Uri, format: Format, name: String): List<Entry> {
        val out = mutableListOf<Entry>()
        context.contentResolver.openInputStream(uri)!!.use { walk(it, format, name) { e, s ->
            if (format == Format.Gz) { var n = 0L; val b = ByteArray(65536); while (true) { val r = s.read(b); if (r < 0) break; n += r }; out += e.copy(size = n) } else out += e
        } }
        return out
    }

    /** Reads everything, so a damaged archive fails here instead of halfway through an extract. */
    fun test(context: Context, uri: Uri, format: Format, name: String, onProgress: (Int) -> Unit): Int {
        var n = 0
        val b = ByteArray(65536)
        context.contentResolver.openInputStream(uri)!!.use { walk(it, format, name) { _, s -> while (s.read(b) >= 0) {}; n++; if (n % 50 == 0) onProgress(n) } }
        return n
    }

    /** Extracts all entries, or only those under [only] paths, into a folder the user picked. */
    fun extract(context: Context, uri: Uri, format: Format, name: String, dest: DocumentFile, only: Set<String>?, onProgress: (Int, String) -> Unit): Int {
        val dirs = HashMap<String, DocumentFile>()
        dirs[""] = dest
        fun folder(path: String): DocumentFile {
            if (path.isEmpty()) return dest
            dirs[path]?.let { return it }
            val parent = folder(path.substringBeforeLast('/', ""))
            val n = path.substringAfterLast('/')
            val d = parent.findFile(n)?.takeIf { it.isDirectory } ?: parent.createDirectory(n) ?: throw ArchiveError("Couldn't create folder $n")
            dirs[path] = d; return d
        }
        var count = 0
        context.contentResolver.openInputStream(uri)!!.use { input ->
            walk(input, format, name) { e, s ->
                if (only != null && only.none { e.path == it || e.path.startsWith(it.trimEnd('/') + "/") }) return@walk
                if (e.dir) { folder(e.path.trimEnd('/')); return@walk }
                val parent = folder(e.parent)
                parent.findFile(e.name)?.delete()
                val f = parent.createFile("application/octet-stream", e.name) ?: throw ArchiveError("Couldn't create ${e.name}")
                context.contentResolver.openOutputStream(f.uri)!!.use { s.copyTo(it, 128 * 1024) }
                count++; onProgress(count, e.path)
            }
        }
        return count
    }

    /** Copies one entry to [out]. */
    fun readOne(context: Context, uri: Uri, format: Format, name: String, path: String, out: java.io.OutputStream): Boolean {
        var found = false
        context.contentResolver.openInputStream(uri)!!.use { input -> walk(input, format, name) { e, s -> if (!found && e.path == path) { s.copyTo(out); found = true } } }
        return found
    }

    /** Writes a new ZIP from picked documents (files or whole folders). */
    fun create(context: Context, sources: List<DocumentFile>, out: Uri, level: Int, onProgress: (Int, String) -> Unit) {
        var n = 0
        context.contentResolver.openOutputStream(out, "wt")!!.use { os ->
            ZipOutputStream(os.buffered()).use { z ->
                z.setLevel(level)
                fun add(d: DocumentFile, path: String) {
                    if (d.isDirectory) { z.putNextEntry(ZipEntry("$path/")); z.closeEntry(); d.listFiles().forEach { add(it, "$path/${it.name}") } }
                    else {
                        z.putNextEntry(ZipEntry(path).apply { time = d.lastModified() })
                        context.contentResolver.openInputStream(d.uri)!!.use { it.copyTo(z, 128 * 1024) }
                        z.closeEntry(); n++; onProgress(n, path)
                    }
                }
                sources.forEach { add(it, it.name ?: "file") }
            }
        }
    }
}
