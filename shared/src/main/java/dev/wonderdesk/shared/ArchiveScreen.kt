package dev.wonderdesk.shared

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ArchiveScreen(backup: Boolean) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val op = rememberOperation()
    var sources by remember { mutableStateOf(listOf<Uri>()) }
    var archive by remember { mutableStateOf<Uri?>(activity.intent.data) }
    var entries by remember { mutableStateOf(listOf<ZipItem>()) }
    var confirm by remember { mutableStateOf(false) }
    val select = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { values -> sources = values; op.notice("${values.size} source documents selected; no files copied yet.") }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { value -> archive = value; value?.let { context.keepRead(it) } }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { value ->
        if (value != null) op.run("Writing and verifying ZIP", {
            try {
                ZipEngine.create(context, sources, context.contentResolver.openOutputStream(value, "w")!!)
                ZipEngine.inspect(context.contentResolver.openInputStream(value)!!)
            } catch (e: Exception) {
                val removed = runCatching { DocumentsContract.deleteDocument(context.contentResolver, value) }.getOrDefault(false)
                throw IllegalStateException("${e.message}. ${if (removed) "Incomplete output removed" else "An incomplete output may remain; delete it before use"}", e)
            }
        }) { result -> archive = value; entries = result; op.notice("ZIP written and read back; CRC checked for ${result.size} entries. ${if (backup) "A clean-emulator restore test is still required." else "Originals unchanged."}") }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { destination ->
        val source = archive
        if (destination != null && source != null) op.run("Extracting into a new folder and checking each file", { ZipEngine.extract(context, source, destination) }) {
            op.notice("${it.second} files restored to ${it.first}. SHA-256 read-back checks passed. Existing files were not overwritten.")
        }
    }
    LaunchedEffect(archive) {
        archive?.let { value -> op.run("Reading ZIP and checking CRCs", { ZipEngine.inspect(context.contentResolver.openInputStream(value)!!) }) {
            entries = it; op.notice("ZIP checked: ${it.size} entries. Choose a destination to ${if (backup) "test restore" else "extract"}.")
        } }
    }
    Text(if (backup) "Back up documents you can access, not the Android system or other apps' private data." else "ZIP archives · create, inspect, verify and extract", style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AquaButton(onClick = { select.launch(arrayOf("*/*")) }, enabled = !op.busy) { Text("Select files") }
        AquaSecondaryButton(onClick = { create.launch(if (backup) "Wonderdesk-backup.zip" else "Archive.zip") }, enabled = sources.isNotEmpty() && !op.busy) { Text(if (backup) "Save backup" else "Create ZIP") }
    }
    if (sources.isNotEmpty()) Text("${sources.size} selected documents; duplicate names are rejected, never silently merged.", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AquaSecondaryButton(onClick = { open.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, enabled = !op.busy) { Text("Open ZIP") }
        AquaSecondaryButton(onClick = { confirm = true }, enabled = archive != null && entries.isNotEmpty() && !op.busy) { Text(if (backup) "Restore…" else "Extract…") }
    }
    if (archive != null) TextButton(onClick = { archive?.let { context.share(it, "application/zip", op::notice) } }, enabled = !op.busy) { Text("Share this ZIP") }
    OperationStatus(op)
    Text("Safety limit: 2,000 entries, 64 MiB per file, 256 MiB total. ZIP only; encryption and other formats are not supported.", style = MaterialTheme.typography.bodySmall)
    LazyColumn(Modifier.fillMaxSize()) {
        items(entries) { entry -> ListItem(headlineContent = { Text(entry.path) }, supportingContent = { Text(if (entry.directory) "Folder" else "${entry.bytes} bytes · CRC checked") }) }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Choose restore destination") }, text = { Text("A new ‘Wonderdesk restored’ folder will be created inside the folder you choose. Existing files will not be overwritten. Original ZIP is never deleted. On failure the app tries to remove only its new partial folder.") }, confirmButton = {
        TextButton(onClick = { confirm = false; restore.launch(null) }) { Text("Choose destination") }
    }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
