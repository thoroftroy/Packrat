package dev.wonderdesk.shared

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

@Composable
fun EditorScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("draft", Context.MODE_PRIVATE) }
    val op = rememberOperation()
    var text by remember { mutableStateOf(prefs.getString("text", "") ?: "") }
    var fileName by remember { mutableStateOf(prefs.getString("name", "Note.txt") ?: "Note.txt") }
    var origin by remember { mutableStateOf("Local recovered draft") }
    var saved by remember { mutableStateOf(true) }
    var pendingExport by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var replace by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { value ->
        if (value != null) {
            val exportText = pendingExport
            op.run("Exporting UTF-8 text to a new document", {
                context.contentResolver.openOutputStream(value, "w")!!.use { it.write(exportText.toByteArray(Charsets.UTF_8)) }
            }) { op.notice("New document written. The original source was not overwritten.") }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { value -> pendingImport = value }
    LaunchedEffect(text, fileName) {
        saved = false
        delay(250)
        val currentText = text; val currentName = fileName
        saved = withContext(Dispatchers.IO) { prefs.edit().putString("text", currentText).putString("name", currentName).commit() }
        if (!saved) op.notice("Draft could not be saved. Export it before leaving.")
    }
    LaunchedEffect(Unit) {
        val intent = (context as ComponentActivity).intent
        val shared = if (intent.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
        if (shared != null) {
            prefs.edit().putString("previous", text).apply()
            text = shared.take(1_000_000); origin = "Shared text in local draft"
        } else pendingImport = intent.data
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AquaButton(onClick = { open.launch(arrayOf("text/*", "application/json", "application/xml")) }, enabled = !op.busy) { Text("Import file") }
            AquaSecondaryButton(onClick = { pendingExport = text; create.launch(fileName.ifBlank { "Note.txt" }) }, enabled = !op.busy) { Text("Export copy") }
            TextButton(onClick = { replace = true }, enabled = !op.busy) { Text("Find") }
        }
        OutlinedTextField(fileName, { fileName = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Export file name") }, singleLine = true)
        Text("$origin · ${if (saved) "Draft saved locally" else "Saving draft…"} · ${text.length} characters", style = MaterialTheme.typography.bodySmall)
        OperationStatus(op)
        OutlinedTextField(text, { if (it.length <= 1_000_000) text = it else op.notice("Draft limit is 1,000,000 characters. Export and start another draft.") }, Modifier.fillMaxWidth().weight(1f), label = { Text("Plain text / Markdown") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                context.launchSafely(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share text"), op::notice)
            }) { Text("Share text") }
            TextButton(onClick = { text = prefs.getString("previous", text) ?: text; origin = "Previous imported draft recovered" }, enabled = prefs.contains("previous")) { Text("Recover previous") }
        }
    }
    pendingImport?.let { uri ->
        AlertDialog(onDismissRequest = { pendingImport = null }, title = { Text("Replace local draft with imported text?") }, text = { Text("Your current draft will be kept as the previous draft. Imports are limited to 1 MiB of valid UTF-8. This will not modify the source file.") }, confirmButton = {
            TextButton(onClick = {
                pendingImport = null
                op.run("Reading UTF-8 file", {
                    val data = context.contentResolver.openInputStream(uri)!!.use { input ->
                        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) { val n = input.read(buffer); if (n < 0) break; require(output.size() + n <= 1024 * 1024) { "File exceeds 1 MiB" }; output.write(buffer, 0, n) }
                        output.toByteArray()
                    }
                    require(data.size <= 1024 * 1024) { "File exceeds 1 MiB" }
                    val decoded = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString().removePrefix("\uFEFF")
                    decoded to context.displayName(uri)
                }) {
                    prefs.edit().putString("previous", text).apply()
                    text = it.first; fileName = it.second; origin = "Imported copy in local draft"; op.notice("Imported. Export copy writes a new document; the source is read-only here.")
                }
            }) { Text("Import copy") }
        }, dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } })
    }
    if (replace) AlertDialog(onDismissRequest = { replace = false }, title = { Text("Find and replace (case-sensitive)") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(find, { find = it }, label = { Text("Find") })
            OutlinedTextField(replacement, { replacement = it }, label = { Text("Replace with") })
            if (find.isNotEmpty()) Text("${text.windowed(find.length).count { it == find }} matching positions")
        }
    }, confirmButton = { TextButton(onClick = { if (find.isNotEmpty()) { prefs.edit().putString("previous", text).apply(); text = text.replace(find, replacement) }; replace = false }) { Text("Replace all") } }, dismissButton = { TextButton(onClick = { replace = false }) { Text("Close") } })
}
