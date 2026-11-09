package dev.wonderdesk.shared

import android.net.Uri
import android.content.Intent
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoreEngineTest {
    @Test fun centralCatalogIsPackagedAndAliasesAreShared() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val aliases = SharedCatalog.aliases(context)
        assertTrue(aliases["abacus"]!!.contains("calculator"))
        assertTrue(aliases["wayfinder"]!!.contains("browse files"))
        assertTrue(aliases["controlroom"]!!.contains("settings"))
        assertTrue(SharedCatalog.ecosystemVersion(context).matches(Regex("0\\.\\d+\\.\\d+")))
    }
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { stream -> entries.forEach { (name, data) -> stream.putNextEntry(ZipEntry(name)); stream.write(data); stream.closeEntry() } }
        return output.toByteArray()
    }
    private fun rejects(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (e: IllegalArgumentException) { assertTrue(e.message?.isNotBlank() == true) } }

    @Test fun calculatorPrecedenceAndErrors() {
        assertEquals(14.0, Expression.evaluate("2+3*4"), 1e-10)
        assertEquals(-4.0, Expression.evaluate("-2^2"), 1e-10)
        assertEquals(512.0, Expression.evaluate("2^3^2"), 1e-10)
        assertEquals(5.0, Expression.evaluate("sqrt(9)+abs(-2)"), 1e-10)
        rejects { Expression.evaluate("1/0") }
        rejects { Expression.evaluate("sqrt(-1)") }
        rejects { Expression.evaluate("1+2garbage") }
        rejects { Expression.evaluate("(".repeat(70) + "1" + ")".repeat(70)) }
    }

    @Test fun unsafeArchiveNamesAndConflictsRejected() {
        listOf("../escape", "/absolute", "C:/drive", "ok/../../escape", "a\\b", "a//b", "./local").forEach { rejects { ZipEngine.safePath(it) } }
        rejects { ZipEngine.inspect(ByteArrayInputStream(zip("../escape.txt" to byteArrayOf(1)))) }
        rejects { ZipEngine.inspect(ByteArrayInputStream(zip("folder" to byteArrayOf(1), "folder/file.txt" to byteArrayOf(2)))) }
    }

    @Test fun crcCorruptionAndEntryLimitsRejected() {
        val out = ByteArrayOutputStream()
        val bytes = "fixture-content-for-crc".toByteArray()
        ZipOutputStream(out).use { stream ->
            val crc = CRC32().apply { update(bytes) }.value
            val entry = ZipEntry("stored.txt").apply { method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = size; this.crc = crc }
            stream.putNextEntry(entry); stream.write(bytes); stream.closeEntry()
        }
        val corrupted = out.toByteArray()
        val index = corrupted.toList().windowed(bytes.size).indexOfFirst { it == bytes.toList() }
        assertTrue(index > 0); corrupted[index] = (corrupted[index].toInt() xor 1).toByte()
        try { ZipEngine.inspect(ByteArrayInputStream(corrupted)); fail("CRC corruption accepted") } catch (_: java.util.zip.ZipException) { }
        val tooMany = zip(*(0..ZipEngine.MAX_ENTRIES).map { "$it.txt" to ByteArray(0) }.toTypedArray())
        rejects { ZipEngine.inspect(ByteArrayInputStream(tooMany)) }
    }

    @Test fun realSafRestoreReadBackAndCollisionAvoidance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rootUri = DocumentsContract.buildTreeDocumentUri("dev.wonderdesk.fixture.documents", "root")
        // A tree URI is not itself an access grant. Mirror the read/write prefix
        // grant delivered by ACTION_OPEN_DOCUMENT_TREE; do not bypass canWrite.
        InstrumentationRegistry.getInstrumentation().context.grantUriPermission(context.packageName, rootUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        val root = DocumentFile.fromTreeUri(context, rootUri)!!
        assertTrue("Fixture tree must carry a real write grant", root.canWrite())
        val original = "Round-trip fixture\nUnicode: café Ω\n".toByteArray()
        val archive = root.createFile("application/zip", "roundtrip-${System.nanoTime()}.zip")!!
        context.contentResolver.openOutputStream(archive.uri, "w")!!.use { it.write(zip("nested/note.txt" to original)) }
        val first = ZipEngine.extract(context, archive.uri, rootUri)
        val restored = root.findFile(first.first)!!.findFile("nested")!!.findFile("note.txt")!!
        assertArrayEquals(original, context.contentResolver.openInputStream(restored.uri)!!.use { it.readBytes() })
        val second = ZipEngine.extract(context, archive.uri, rootUri)
        assertNotEquals(first.first, second.first)
        assertTrue(root.findFile(first.first)!!.exists())
        // Test the real ZIP writer as well as Java/Android extraction through ContentResolver.
        val made = ByteArrayOutputStream()
        ZipEngine.create(context, listOf(restored.uri), made)
        val inspected = ZipEngine.inspect(ByteArrayInputStream(made.toByteArray()))
        assertEquals("note.txt", inspected.single().path)
        assertEquals(original.size.toLong(), inspected.single().bytes)
        assertTrue(root.findFile(first.first)!!.delete()); assertTrue(root.findFile(second.first)!!.delete()); assertTrue(archive.delete())
    }

    @Test fun passwordGeneratorUsesRequestedClassesAndLength() {
        repeat(32) {
            val password = Passwords.generate(24, true)
            assertEquals(24, password.length)
            assertTrue(password.any { it.isUpperCase() }); assertTrue(password.any { it.isLowerCase() }); assertTrue(password.any { it.isDigit() }); assertTrue(password.any { !it.isLetterOrDigit() })
        }
        rejects { Passwords.generate(2, true) }
    }
}
