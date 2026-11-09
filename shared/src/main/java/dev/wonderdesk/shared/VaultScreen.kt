package dev.wonderdesk.shared

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@Composable
fun VaultScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val store = remember { EncryptedVault(File(context.filesDir, "vault-v2.json")) }
    val op = rememberOperation()
    var revision by remember { mutableIntStateOf(0) }
    var master by remember { mutableStateOf("") } // Never rememberSaveable: no plaintext password in saved state.
    var confirmation by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf<JSONObject?>(null) }
    var editIndex by remember { mutableIntStateOf(-1) }
    var deleteIndex by remember { mutableStateOf<Int?>(null) }
    var rotate by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var incoming by remember { mutableStateOf<Uri?>(null) }
    var incomingPassword by remember { mutableStateOf("") }
    var clipToken by remember { mutableStateOf<String?>(null) }
    var lastActivity by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    fun clearClipboard() {
        val token = clipToken
        runCatching {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            if (token != null && clipboard.primaryClipDescription?.label?.toString() == token) clipboard.clearPrimaryClip()
        }
        clipToken = null
    }
    fun locked() {
        store.lock(); master = ""; confirmation = ""; editor = null; rotate = false
        currentPassword = ""; newPassword = ""; repeatPassword = ""; incomingPassword = ""; deleteIndex = null; query = ""
        clearClipboard(); revision++
    }
    fun touch() { lastActivity = SystemClock.elapsedRealtime() }
    fun copySecret(value: String) {
        val token = "Wonderdesk secret " + UUID.randomUUID()
        val clip = ClipData.newPlainText(token, value)
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        clipToken = token; touch()
        op.notice("Password copied with Android's sensitive marker; best-effort clearing in 20 seconds. Other apps may still read allowed clipboard content.")
    }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) locked() }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer); store.lock(); clearClipboard() }
    }
    LaunchedEffect(Unit) { while (true) { delay(1000); if (store.unlocked && SystemClock.elapsedRealtime() - lastActivity >= 300_000) locked() } }
    LaunchedEffect(clipToken) { if (clipToken != null) { delay(20_000); clearClipboard() } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) op.run("Exporting encrypted v2 backup", {
            val raw = store.exportEncrypted()
            context.contentResolver.openOutputStream(uri, "w")!!.use { it.write(raw) }
        }) { op.notice("Encrypted backup exported. Keep its master password separately; no plaintext was exported.") }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { incomingPassword = ""; incoming = uri }
    }
    @Suppress("UNUSED_VARIABLE") val changed = revision
    val unlocked = store.unlocked
    val entries = store.entries()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("AES-256-GCM · local encrypted v2 format · independent security audit pending · no password recovery", style = MaterialTheme.typography.bodySmall)
        OperationStatus(op)
        if (!unlocked) {
            Text(if (store.exists) "Unlock your vault" else "Create a local vault", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(master, { master = it; touch() }, Modifier.fillMaxWidth(), label = { Text("Master password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, enabled = !op.busy)
            if (!store.exists) OutlinedTextField(confirmation, { confirmation = it; touch() }, Modifier.fillMaxWidth(), label = { Text("Repeat master password (12+ characters)") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, enabled = !op.busy)
            AquaButton(onClick = {
                val password = master; val existing = store.exists
                if (!existing && password != confirmation) { op.notice("Passwords do not match"); return@AquaButton }
                master = ""; confirmation = ""
                op.run(if (existing) "Unlocking authenticated vault" else "Creating encrypted vault", { if (existing) store.unlock(password) else store.create(password) }) { revision++; touch(); op.notice("Unlocked. Locks when backgrounded or after five minutes without activity.") }
            }, enabled = !op.busy) { Text(if (store.exists) "Unlock" else "Create vault") }
        } else {
            OutlinedTextField(query, { query = it; touch() }, Modifier.fillMaxWidth(), label = { Text("Search entry title or username") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AquaButton(onClick = { editIndex = -1; editor = JSONObject().put("title", "").put("user", "").put("pw", ""); touch() }, enabled = !op.busy) { Text("Add entry") }
                AquaSecondaryButton(onClick = { locked(); op.notice("Locked") }) { Text("Lock") }
                TextButton(onClick = { rotate = true; touch() }, enabled = !op.busy) { Text("Change master…") }
            }
            if (entries.length() == 0) Text("No entries yet. Add one; credentials are encrypted before they reach disk.")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed((0 until entries.length()).filter { index -> val entry = entries.getJSONObject(index); entry.getString("title").contains(query, true) || entry.getString("user").contains(query, true) }) { _, index ->
                    val entry = entries.getJSONObject(index)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(entry.getString("title"), style = MaterialTheme.typography.titleMedium)
                            Text(entry.getString("user"), style = MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(onClick = { copySecret(entry.getString("pw")) }) { Text("Copy password") }
                                TextButton(onClick = { editIndex = index; editor = JSONObject(entry.toString()); touch() }, enabled = !op.busy) { Text("Edit") }
                                TextButton(onClick = { deleteIndex = index; touch() }, enabled = !op.busy) { Text("Delete…") }
                            }
                        }
                    }
                }
            }
        }
        if (!unlocked) Spacer(Modifier.weight(1f))
        Row {
            TextButton(onClick = { export.launch("Vault-encrypted-v2.json") }, enabled = store.exists && !op.busy) { Text("Export encrypted backup") }
            TextButton(onClick = { importPicker.launch(arrayOf("application/json", "application/octet-stream")) }, enabled = !store.exists && !op.busy) { Text("Import into empty vault…") }
        }
        Text("Linux v2 backups are supported. No autofill, sync, notes, biometrics or audit claim. Screenshot protection is enabled; clipboard expiry depends on Android granting access.", style = MaterialTheme.typography.bodySmall)
    }
    editor?.let { value ->
        var title by remember(value) { mutableStateOf(value.getString("title")) }
        var user by remember(value) { mutableStateOf(value.getString("user")) }
        var password by remember(value) { mutableStateOf(value.getString("pw")) }
        AlertDialog(onDismissRequest = { if (!op.busy) editor = null }, title = { Text(if (editIndex < 0) "New credential" else "Edit credential") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(1000); touch() }, label = { Text("Title") })
                OutlinedTextField(user, { user = it.take(1000); touch() }, label = { Text("Username") })
                OutlinedTextField(password, { password = it.take(10000); touch() }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                TextButton(onClick = { password = Passwords.generate(24, true); touch() }) { Text("Generate 24-character password") }
            }
        }, confirmButton = { TextButton(onClick = {
            if (title.isBlank()) { op.notice("Use a non-empty title"); return@TextButton }
            val next = store.entries(); val entry = JSONObject().put("title", title).put("user", user).put("pw", password)
            if (editIndex < 0) next.put(entry) else next.put(editIndex, entry)
            op.run("Encrypting and saving credential", { store.save(next) }) { editor = null; revision++; touch(); op.notice("Saved atomically; existing writers are detected.") }
        }, enabled = !op.busy) { Text("Save") } }, dismissButton = { TextButton(onClick = { editor = null }, enabled = !op.busy) { Text("Cancel") } })
    }
    deleteIndex?.let { index -> AlertDialog(onDismissRequest = { deleteIndex = null }, title = { Text("Delete credential?") }, text = { Text("This removes only the selected entry. Export a backup first if you need recovery.") }, confirmButton = { TextButton(onClick = {
        val next = store.entries(); next.remove(index)
        op.run("Saving vault without selected entry", { store.save(next) }) { deleteIndex = null; revision++; touch(); op.notice("Credential removed from this vault; older encrypted backups are unchanged.") }
    }, enabled = !op.busy) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleteIndex = null }) { Text("Cancel") } }) }
    if (rotate) AlertDialog(onDismissRequest = { if (!op.busy) rotate = false }, title = { Text("Change master password") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(currentPassword, { currentPassword = it; touch() }, label = { Text("Current master password") }, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(newPassword, { newPassword = it; touch() }, label = { Text("New master password (12+ characters)") }, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(repeatPassword, { repeatPassword = it; touch() }, label = { Text("Repeat new master password") }, visualTransformation = PasswordVisualTransformation())
            Text("Existing exported backups retain their original password.")
        }
    }, confirmButton = { TextButton(onClick = {
        if (newPassword != repeatPassword) { op.notice("New passwords do not match"); return@TextButton }
        val current = currentPassword; val replacement = newPassword
        currentPassword = ""; newPassword = ""; repeatPassword = ""
        op.run("Re-authenticating and rotating encryption key", { store.rotate(current, replacement) }) { rotate = false; revision++; touch(); op.notice("Password changed with a fresh salt and key.") }
    }, enabled = !op.busy) { Text("Change password") } }, dismissButton = { TextButton(onClick = { rotate = false; currentPassword = ""; newPassword = ""; repeatPassword = "" }) { Text("Cancel") } })
    incoming?.let { uri -> AlertDialog(onDismissRequest = { incoming = null; incomingPassword = "" }, title = { Text("Import encrypted v2 vault") }, text = {
        Column { Text("Only an empty destination is allowed. Existing credentials are never overwritten. Enter the backup's master password to authenticate it before import."); OutlinedTextField(incomingPassword, { incomingPassword = it }, label = { Text("Backup master password") }, visualTransformation = PasswordVisualTransformation()) }
    }, confirmButton = { TextButton(onClick = {
        val password = incomingPassword; incomingPassword = ""
        op.run("Authenticating encrypted backup", {
            val raw = context.contentResolver.openInputStream(uri)!!.use { input ->
                val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size() + count <= EncryptedVault.MAX_BYTES) { "Backup too large" }; output.write(buffer, 0, count) }
                output.toByteArray()
            }
            store.importNew(raw, password)
        }) { incoming = null; revision++; touch(); op.notice("Authenticated backup imported. Source document was not changed.") }
    }, enabled = !op.busy) { Text("Import") } }, dismissButton = { TextButton(onClick = { incoming = null; incomingPassword = "" }) { Text("Cancel") } }) }
}
