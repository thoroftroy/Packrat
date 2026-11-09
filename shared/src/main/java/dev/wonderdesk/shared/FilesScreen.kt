package dev.wonderdesk.shared

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile

private fun Context.documentFlags(uri: Uri): Long = contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use {
    if (it.moveToFirst()) it.getLong(0) else 0L
} ?: 0L

private data class FileRow(val file: DocumentFile, val name: String, val folder: Boolean, val bytes: Long, val flags: Long)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("files", Context.MODE_PRIVATE) }
    val op = rememberOperation()
    var stack by remember { mutableStateOf(listOf<DocumentFile>()) }
    var rows by remember { mutableStateOf(listOf<FileRow>()) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<FileRow?>(null) }
    var edit by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var pendingCopy by remember { mutableStateOf<Uri?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val folder = stack.lastOrNull()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                .onFailure { context.keepRead(uri) }
            prefs.edit().putString("root", uri.toString()).apply()
            stack = listOfNotNull(DocumentFile.fromTreeUri(context, uri))
        }
    }
    val copy = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val source = pendingCopy
        val dest = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && source != null && dest != null) {
            op.run("Copying to a new document", {
                try {
                    context.contentResolver.openInputStream(source)!!.use { input ->
                        context.contentResolver.openOutputStream(dest, "w")!!.use { output -> input.copyTo(output) }
                    }
                } catch (e: Exception) {
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, dest) }
                    throw e
                }
            }) { op.notice("Copy written. Original unchanged."); reload++ }
        }
        pendingCopy = null
    }
    LaunchedEffect(Unit) {
        prefs.getString("root", null)?.let { saved ->
            runCatching { DocumentFile.fromTreeUri(context, Uri.parse(saved)) }.getOrNull()?.let { stack = listOf(it) }
        }
    }
    LaunchedEffect(folder?.uri, reload) {
        if (folder != null) op.run("Reading folder", {
            require(folder.exists() && folder.canRead()) { "Folder unavailable; choose it again" }
            folder.listFiles().map { FileRow(it, it.name ?: "Unnamed", it.isDirectory, it.length(), context.documentFlags(it.uri)) }
                .sortedWith(compareBy<FileRow> { !it.folder }.thenBy { it.name.lowercase() })
        }) { rows = it; op.notice("${it.size} items in ${folder.name ?: "folder"}") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AquaButton(onClick = { picker.launch(null) }, enabled = !op.busy) { Text("Choose folder") }
        AquaSecondaryButton(onClick = { stack = stack.dropLast(1) }, enabled = stack.size > 1 && !op.busy) { Text("Up") }
    }
    if (folder == null) Text("Choose a folder to browse. Android controls which folders are accessible. No storage permission is needed.")
    if (folder != null) {
        Text(stack.joinToString(" / ") { it.name ?: "Folder" }, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { reload++ }, enabled = !op.busy) { Text("Refresh") }
            TextButton(onClick = { name = ""; edit = "folder" }, enabled = !op.busy && folder.canWrite()) { Text("New folder") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Filter this folder") }, singleLine = true)
    }
    OperationStatus(op)
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(rows.filter { it.name.contains(query, true) }, key = { it.file.uri.toString() }) { row ->
            ElevatedCard(Modifier.fillMaxWidth().combinedClickable(enabled = !op.busy, onLongClick = { selected = row }, onClick = {
                if (row.folder) { rows = emptyList(); stack = stack + row.file }
                else context.launchSafely(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(row.file.uri, row.file.type ?: "application/octet-stream").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open with"), op::notice)
            })) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text((if (row.folder) "Folder · " else "") + row.name)
                        if (!row.folder) Text(android.text.format.Formatter.formatShortFileSize(context, row.bytes), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { selected = row }, Modifier.heightIn(min = 48.dp)) { Text("Actions") }
                }
            }
        }
    }
    selected?.let { row ->
        if (edit == null) AlertDialog(onDismissRequest = { selected = null }, title = { Text(row.name) }, text = {
            Column {
                if (!row.folder) {
                    TextButton(onClick = { context.share(row.file.uri, row.file.type ?: "application/octet-stream", op::notice); selected = null }) { Text("Share") }
                    TextButton(onClick = {
                        pendingCopy = row.file.uri
                        copy.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(row.file.type ?: "application/octet-stream").putExtra(Intent.EXTRA_TITLE, row.name))
                        selected = null
                    }) { Text("Copy to new document…") }
                    if (row.name.endsWith(".zip", true)) TextButton(onClick = {
                        context.launchSafely(Intent(Intent.ACTION_VIEW).setClassName("dev.wonderdesk.packrat", "dev.wonderdesk.packrat.MainActivity").setDataAndType(row.file.uri, "application/zip").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), op::notice)
                        selected = null
                    }) { Text("Open ZIP in Packrat") }
                }
                if (row.flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME.toLong() != 0L) TextButton(onClick = { name = row.name; edit = "rename" }) { Text("Rename…") }
                if (row.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE.toLong() != 0L) TextButton(onClick = { edit = "delete" }) { Text("Permanently delete…") }
                Text("${row.file.type ?: "Folder"}\n${if (row.file.canRead()) "Readable" else "Access unavailable"}", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } })
    }
    if (edit != null) AlertDialog(onDismissRequest = { edit = null }, title = { Text(when (edit) { "delete" -> "Permanently delete?"; "rename" -> "Rename"; else -> "New folder" }) }, text = {
        if (edit == "delete") Text("Delete ${selected?.name} and any contents? This provider operation has no Wonderdesk undo or trash.")
        else OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
    }, confirmButton = {
        TextButton(onClick = {
            val action = edit; val row = selected; val parent = folder; val newName = name.trim()
            edit = null; selected = null
            op.run("Updating folder", {
                if (action != "delete") require(newName.isNotBlank() && newName !in listOf(".", "..") && '/' !in newName && '\\' !in newName) { "Use a non-empty name without slashes" }
                when (action) {
                    "delete" -> check(row!!.file.delete()) { "Provider refused deletion" }
                    "rename" -> { require(parent?.findFile(newName) == null || newName == row!!.name) { "An item already has that name" }; check(row!!.file.renameTo(newName)) { "Provider refused rename" } }
                    else -> { require(parent!!.findFile(newName) == null) { "An item already has that name" }; check(parent.createDirectory(newName) != null) { "Provider refused folder creation" } }
                }
            }) { op.notice("${if (action == "delete") "Deletion" else "Change"} completed."); reload++ }
        }) { Text(if (edit == "delete") "Delete permanently" else "Apply") }
    }, dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancel") } })
}
