package dev.wonderdesk.shared

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File

/** A real DocumentsProvider fixture for provider-independent SAF engine tests. */
class FixtureDocumentsProvider : DocumentsProvider() {
    private val root: File get() = File(context!!.filesDir, "saf-fixtures").apply { mkdirs() }
    private fun file(id: String): File {
        val result = if (id == "root") root else File(root, id.removePrefix("root/")).canonicalFile
        require(result == root.canonicalFile || result.toPath().startsWith(root.canonicalFile.toPath()))
        return result
    }
    private fun id(file: File) = if (file == root) "root" else "root/" + file.relativeTo(root).invariantSeparatorsPath
    override fun onCreate() = true
    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS)).apply {
        val row = newRow()
        for (column in columnNames) row.add(when (column) { Root.COLUMN_ROOT_ID -> "test"; Root.COLUMN_DOCUMENT_ID -> "root"; Root.COLUMN_TITLE -> "Wonderdesk test fixtures"; Root.COLUMN_FLAGS -> Root.FLAG_SUPPORTS_CREATE; else -> null })
    }
    private fun cursor(files: List<File>, projection: Array<out String>?): Cursor = MatrixCursor(projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_FLAGS)).apply {
        files.forEach { file ->
            val row = newRow()
            for (column in columnNames) row.add(when (column) {
                Document.COLUMN_DOCUMENT_ID -> id(file)
                Document.COLUMN_DISPLAY_NAME -> file.name
                Document.COLUMN_MIME_TYPE -> if (file.isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream"
                Document.COLUMN_SIZE -> file.length()
                Document.COLUMN_FLAGS -> Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME or if (file.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else 0
                else -> null
            })
        }
    }
    override fun queryDocument(documentId: String, projection: Array<out String>?) = cursor(listOf(file(documentId)), projection)
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) = cursor(file(parentDocumentId).listFiles()?.toList().orEmpty(), projection)
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?) = ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        require(ZipEngine.safePath(displayName).size == 1)
        val result = File(file(parentDocumentId), displayName)
        require(!result.exists())
        check(if (mimeType == Document.MIME_TYPE_DIR) result.mkdir() else result.createNewFile())
        return id(result)
    }
    override fun deleteDocument(documentId: String) { val target = file(documentId); require(target != root); check(target.deleteRecursively()) }
    override fun isChildDocument(parentDocumentId: String, documentId: String) = file(documentId).toPath().startsWith(file(parentDocumentId).toPath())
}
