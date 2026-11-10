package dev.wonderdesk.packrat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import dev.wonderdesk.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : WonderActivity() {
    @Composable override fun Content() = PackratApp()
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); incoming.value = intent }
    companion object { val incoming = mutableStateOf<Intent?>(null) }
}

private const val ABOUT = "Packrat opens, creates and extracts archives. Browse a ZIP, TAR or TAR.GZ like a folder, preview files without extracting, " +
    "pull out just what you need, test an archive for damage, and pack files or whole folders into a new ZIP."

data class Recent(val uri: String, val name: String, val at: Long)

private fun Context.recents(): List<Recent> = runCatching {
    JSONArray(prefs("packrat").getString("recent", "[]")).let { a -> (0 until a.length()).map { a.getJSONObject(it).let { o -> Recent(o.getString("uri"), o.getString("name"), o.getLong("at")) } } }
}.getOrDefault(emptyList())

private fun Context.addRecent(r: Recent) {
    val list = (listOf(r) + recents().filter { it.uri != r.uri }).take(12)
    prefs("packrat").edit().putString("recent", JSONArray(list.map { JSONObject().put("uri", it.uri).put("name", it.name).put("at", it.at) }).toString()).apply()
}

class Opened(val uri: Uri, val name: String, val format: Format, val entries: List<Entry>, val bytes: Long)

@Composable
fun PackratApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var opened by remember { mutableStateOf<Opened?>(null) }
    var loading by remember { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val toast = remember { mutableStateOf<(String) -> Unit>({}) }
    fun open(uri: Uri) {
        loading = context.displayName(uri)
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val name = context.displayName(uri)
                    val head = context.contentResolver.openInputStream(uri)!!.use { s -> ByteArray(512).also { var o = 0; while (o < 512) { val n = s.read(it, o, 512 - o); if (n < 0) break; o += n } } }
                    val fmt = Archives.detect(name, head) ?: throw ArchiveError("Packrat can open ZIP, JAR, APK, TAR, TAR.GZ and GZ files. ${name.substringAfterLast('.', "This").uppercase()} isn't supported yet.")
                    val size = runCatching { context.contentResolver.openFileDescriptor(uri, "r")!!.use { it.statSize } }.getOrDefault(-1L)
                    Opened(uri, name, fmt, Archives.list(context, uri, fmt, name), size)
                }
            }
            loading = null
            r.onSuccess { opened = it; runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; context.addRecent(Recent(uri.toString(), it.name, System.currentTimeMillis())) }
                .onFailure { toast.value(it.message ?: "Couldn't open that archive") }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::open) }
    val incoming by MainActivity.incoming
    LaunchedEffect(incoming) {
        val i = incoming ?: (context as ComponentActivity).intent
        MainActivity.incoming.value = null; (context as ComponentActivity).intent = Intent()
        val uri = i?.data ?: if (i?.action == Intent.ACTION_SEND) @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri else null
        if (uri != null) open(uri)
    }
    val o = opened
    when {
        creating -> CreateScreen(onClose = { creating = false })
        o != null -> ArchiveScreen(o, onClose = { opened = null })
        else -> WonderApp(appName = "Packrat", about = ABOUT, actions = {
            ToolbarButton(WonderIcons.FolderOpen, "Open archive", { picker.launch(arrayOf("*/*")) })
        }) {
            toast.value = LocalToast.current
            Home(onOpen = { picker.launch(arrayOf("application/zip", "application/x-tar", "application/gzip", "application/java-archive", "application/vnd.android.package-archive", "application/octet-stream", "*/*")) },
                onCreate = { creating = true }, onRecent = { open(Uri.parse(it.uri)) })
            loading?.let { n -> WonderDialog(onDismissRequest = {}, title = { Text("Opening $n") }, text = { AquaProgress(null, Modifier.fillMaxWidth()) }, confirmButton = {}) }
        }
    }
}

@Composable
private fun Home(onOpen: () -> Unit, onCreate: () -> Unit, onRecent: (Recent) -> Unit) {
    val context = LocalContext.current
    val p = wonderPalette()
    var recents by remember { mutableStateOf(context.recents()) }
    Page {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigAction("Open archive", "Browse and extract", WonderIcons.Unarchive, Color(0xFFE67E22), Modifier.weight(1f), onOpen)
            BigAction("Create ZIP", "Pack files or folders", WonderIcons.Archive, Color(0xFF1E7FE8), Modifier.weight(1f), onCreate)
        }
        if (recents.isNotEmpty()) Panel(title = "Recent archives", trailing = { TextButton(onClick = { context.prefs("packrat").edit().remove("recent").apply(); recents = emptyList() }) { Text("Clear") } }) {
            recents.forEachIndexed { i, r ->
                if (i > 0) PanelDivider()
                ListRow(r.name, subtitle = "Opened " + relativeTime(r.at), icon = WonderIcons.FolderZip, tint = Color(0xFFE67E22), chevron = true, onClick = { onRecent(r) })
            }
        } else Panel { EmptyState("No archives yet", "Open a ZIP from here, or tap one in Wayfinder.", modifier = Modifier.padding(0.dp)) }
        Panel(title = "Works with Wayfinder") {
            ListRow("Browse files in Wayfinder", subtitle = "Tap any archive there to open it here", icon = WonderIcons.FolderOpen, tint = Color(0xFF4A90E2), chevron = true, onClick = {
                context.packageManager.getLaunchIntentForPackage("dev.wonderdesk.wayfinder")?.let { context.launchSafely(it) {} }
                    ?: context.launchSafely(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/thoroftroy/Wayfinder/releases"))) {}
            })
        }
        Text("Supports ZIP, JAR, APK, TAR, TAR.GZ and GZ. Extraction blocks unsafe paths and stops at 50,000 entries or 8 GB.", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
    }
}

@Composable
private fun BigAction(title: String, sub: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val p = wonderPalette()
    val shape = RoundedCornerShape(p.panelRadius + 4.dp)
    Column(modifier.clip(shape).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(tint.lighten(.2f), tint, tint.darken(.12f)))).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(36.dp))
        Column {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(sub, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/* ------------------------------------------------- archive view ---------------------------------------------- */

private fun treeToPath(uri: Uri): String? = runCatching {
    val id = DocumentsContract.getTreeDocumentId(uri)
    if (id.startsWith("primary:")) "/storage/emulated/0/" + id.removePrefix("primary:") else null
}.getOrNull()

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchiveScreen(a: Opened, onClose: () -> Unit) {
    val context = LocalContext.current
    val p = wonderPalette()
    val scope = rememberCoroutineScope()
    var dir by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var progress by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<Pair<Int, Uri>?>(null) }
    var info by remember { mutableStateOf(false) }
    val toastRef = remember { mutableStateOf<(String) -> Unit>({}) }
    // Build the folder view: direct children of [dir], including folders implied by deeper paths.
    val children = remember(a, dir) {
        val prefix = if (dir.isEmpty()) "" else "$dir/"
        val map = LinkedHashMap<String, Entry>()
        a.entries.forEach { e ->
            val p0 = e.path.trimEnd('/')
            if (!p0.startsWith(prefix) || p0 == dir) return@forEach
            val rest = p0.removePrefix(prefix)
            val first = rest.substringBefore('/')
            val isDir = rest.contains('/') || e.dir
            val key = prefix + first
            if (isDir) { val old = map[key]; map[key] = Entry("$key/", (old?.size ?: 0) + if (e.dir) 0 else e.size, 0, maxOf(old?.time ?: 0, e.time), true) }
            else map[key] = e
        }
        map.values.sortedWith(compareByDescending<Entry> { it.dir }.thenBy { it.name.lowercase() })
    }
    var pendingOnly by remember { mutableStateOf<Set<String>?>(null) }
    val destPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        context.prefs("packrat").edit().putString("lastDest", tree.toString()).apply()
        val only = pendingOnly
        progress = "Extracting…"
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val root = DocumentFile.fromTreeUri(context, tree)!!
                    val folderName = a.name.substringBeforeLast(".tar").substringBeforeLast('.')
                    val target = if (only == null) (root.findFile(folderName)?.takeIf { it.isDirectory } ?: root.createDirectory(folderName) ?: root) else root
                    Archives.extract(context, a.uri, a.format, a.name, target, only) { n, path -> scope.launch { progress = "Extracting $n · ${path.substringAfterLast('/')}" } } to tree
                }
            }
            progress = null; selected = emptySet()
            r.onSuccess { done = it }.onFailure { toastRef.value(it.message ?: "Extraction failed") }
        }
    }
    fun preview(e: Entry) {
        progress = "Opening ${e.name}…"
        scope.launch {
            val f = withContext(Dispatchers.IO) {
                runCatching {
                    val dirF = File(context.cacheDir, "preview").apply { deleteRecursively(); mkdirs() }
                    val out = File(dirF, e.name)
                    out.outputStream().use { Archives.readOne(context, a.uri, a.format, a.name, e.path, it) }
                    out
                }
            }
            progress = null
            f.onSuccess { file ->
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
                context.launchSafely(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open ${e.name}")) { toastRef.value(it) }
            }.onFailure { toastRef.value(it.message ?: "Couldn't open that file") }
        }
    }
    val files = a.entries.filter { !it.dir }
    val total = files.sumOf { it.size.coerceAtLeast(0) }
    WonderApp(appName = "Packrat", about = ABOUT, title = if (selected.isNotEmpty()) "${selected.size} selected" else a.name, subtitle = if (selected.isEmpty()) "${a.format.label} · ${files.size} files · ${formatBytes(total)}" else null,
        onBack = { if (selected.isNotEmpty()) selected = emptySet() else if (dir.isNotEmpty()) dir = dir.substringBeforeLast('/', "") else onClose() },
        actions = {
            ToolbarButton(WonderIcons.Unarchive, if (selected.isEmpty()) "Extract all" else "Extract selected", { pendingOnly = selected.takeIf { it.isNotEmpty() }; destPicker.launch(null) })
            ToolbarButton(WonderIcons.Info, "Archive info", { info = true })
        },
        menu = listOf(
            WonderMenuItem("Test archive", WonderIcons.Verified) {
                progress = "Testing…"
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Archives.test(context, a.uri, a.format, a.name) { n -> scope.launch { progress = "Testing… $n files read" } } } }
                    progress = null
                    toastRef.value(r.fold({ "All $it entries read without errors" }, { "Problem found: ${it.message}" }))
                }
            },
            WonderMenuItem("Share archive", WonderIcons.Share) { context.share(a.uri, "application/octet-stream") { toastRef.value(it) } },
            WonderMenuItem("Select everything here", WonderIcons.SelectAll) { selected = children.map { it.path.trimEnd('/') }.toSet() },
        )
    ) {
        toastRef.value = LocalToast.current
        Column(Modifier.fillMaxSize()) {
            if (dir.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val parts = listOf("") + dir.split('/').runningReduce { acc, s -> "$acc/$s" }
                parts.forEachIndexed { i, path ->
                    if (i > 0) Icon(WonderIcons.ChevronRight, null, tint = p.secondaryText, modifier = Modifier.size(18.dp))
                    Text(if (path.isEmpty()) a.name.substringBeforeLast('.') else path.substringAfterLast('/'), Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (path == dir) p.accent.copy(alpha = .14f) else Color.Transparent).clickable { dir = path }.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = if (path == dir) p.accent else p.text, style = MaterialTheme.typography.labelLarge)
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                itemsIndexed(children, key = { _, e -> e.path }) { i, e ->
                    val key = e.path.trimEnd('/')
                    val sel = key in selected
                    val (icon, tint) = when {
                        e.dir -> WonderIcons.Folder to Color(0xFF4A90E2)
                        e.name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp") -> WonderIcons.Image to Color(0xFF2E9E44)
                        e.name.substringAfterLast('.').lowercase() in setOf("mp4", "mkv", "webm", "mov") -> WonderIcons.Movie to Color(0xFFE5483C)
                        e.name.substringAfterLast('.').lowercase() in setOf("mp3", "flac", "ogg", "m4a", "wav") -> WonderIcons.MusicNote to Color(0xFF8E44AD)
                        e.name.endsWith(".pdf", true) -> WonderIcons.PictureAsPdf to Color(0xFFD63031)
                        e.name.substringAfterLast('.').lowercase() in setOf("txt", "md", "json", "xml", "kt", "py", "html", "csv") -> WonderIcons.TextSnippet to Color(0xFF7A7F87)
                        else -> WonderIcons.InsertDriveFile to Color(0xFF95A5A6)
                    }
                    ListRow(e.name, Modifier.panelItem(i, children.size), selected = sel, icon = icon, tint = tint,
                        subtitle = if (e.dir) "Folder · ${formatBytes(e.size)}" else formatBytes(e.size) + (if (e.compressed in 1 until e.size) " → ${formatBytes(e.compressed)}" else "") +
                            (if (e.time > 0) " · " + DateUtils.formatDateTime(context, e.time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR) else ""),
                        trailing = if (selected.isNotEmpty()) ({ Checkbox(sel, { on -> selected = if (on) selected + key else selected - key }) }) else null,
                        onLongClick = { selected = selected + key },
                        onClick = { when { selected.isNotEmpty() -> selected = if (sel) selected - key else selected + key; e.dir -> dir = key; else -> preview(e) } })
                }
            }
        }
    }
    progress?.let { msg -> WonderDialog(onDismissRequest = {}, title = { Text(msg.substringBefore(" ·")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { AquaProgress(null, Modifier.fillMaxWidth()); if (msg.contains("·")) Text(msg.substringAfter("· "), style = MaterialTheme.typography.bodySmall, color = p.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }, confirmButton = {}) }
    done?.let { (n, tree) ->
        WonderDialog(onDismissRequest = { done = null }, title = { Text("Extracted $n file${if (n == 1) "" else "s"}") }, text = { Text("Everything is in the folder you chose.") }, confirmButton = {
            val path = treeToPath(tree)
            TextButton(onClick = {
                done = null
                val i = Intent("dev.wonderdesk.wayfinder.SHOW").setPackage("dev.wonderdesk.wayfinder").putExtra("path", path ?: "/storage/emulated/0")
                context.launchSafely(i) { toastRef.value("Install Wayfinder to browse the files") }
            }) { Text("Show in Wayfinder") }
        }, dismissButton = { TextButton(onClick = { done = null }) { Text("Done") } })
    }
    if (info) WonderDialog(onDismissRequest = { info = false }, title = { Text(a.name) }, text = {
        val ratio = if (total > 0 && a.bytes > 0) 100 - (a.bytes * 100 / total) else 0
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Format" to a.format.label, "Files" to "%,d".format(files.size), "Folders" to "%,d".format(a.entries.count { it.dir }.coerceAtLeast(children.count { it.dir })),
                "Archive size" to if (a.bytes > 0) formatBytes(a.bytes) else "Unknown", "Unpacked size" to formatBytes(total), "Space saved" to if (ratio > 0) "$ratio%" else "—",
                "Largest file" to (files.maxByOrNull { it.size }?.let { "${it.name} (${formatBytes(it.size)})" } ?: "—")).forEach { (k, v) ->
                Row { Text(k, Modifier.width(120.dp), color = p.secondaryText); Text(v) }
            }
        }
    }, confirmButton = { TextButton(onClick = { info = false }) { Text("Done") } })
}

/* ---------------------------------------------------- create ------------------------------------------------- */

@Composable
private fun CreateScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val p = wonderPalette()
    val scope = rememberCoroutineScope()
    val sources = remember { mutableStateListOf<DocumentFile>() }
    var level by rememberSaveable { mutableIntStateOf(1) }
    var name by rememberSaveable { mutableStateOf("Archive") }
    var progress by remember { mutableStateOf<String?>(null) }
    val toastRef = remember { mutableStateOf<(String) -> Unit>({}) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list -> list.mapNotNull { DocumentFile.fromSingleUri(context, it) }.forEach { if (sources.none { s -> s.uri == it.uri }) sources += it } }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { t -> t?.let { DocumentFile.fromTreeUri(context, it) }?.let { sources += it; if (name == "Archive") name = it.name ?: name } }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { out ->
        if (out == null) return@rememberLauncherForActivityResult
        progress = "Packing…"
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Archives.create(context, sources.toList(), out, listOf(0, 6, 9)[level]) { n, path -> scope.launch { progress = "Packing $n · ${path.substringAfterLast('/')}" } } } }
            progress = null
            r.onSuccess { toastRef.value("Archive saved"); context.addRecent(Recent(out.toString(), context.displayName(out), System.currentTimeMillis())); onClose() }
                .onFailure { toastRef.value(it.message ?: "Couldn't create the archive") }
        }
    }
    WonderApp(appName = "Packrat", about = ABOUT, title = "Create ZIP", onBack = onClose) {
        toastRef.value = LocalToast.current
        Page {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WButton("Add files", { files.launch(arrayOf("*/*")) }, Modifier.weight(1f), icon = WonderIcons.NoteAdd)
                WButton("Add folder", { folder.launch(null) }, Modifier.weight(1f), icon = WonderIcons.CreateNewFolder)
            }
            if (sources.isEmpty()) Panel { EmptyState("Nothing added yet", "Add files or a whole folder to pack them into one ZIP.", icon = WonderIcons.Archive) }
            else Panel(title = "${sources.size} item${if (sources.size == 1) "" else "s"}") {
                sources.forEachIndexed { i, d ->
                    if (i > 0) PanelDivider()
                    ListRow(d.name ?: "Item", subtitle = if (d.isDirectory) "Folder" else formatBytes(d.length()), icon = if (d.isDirectory) WonderIcons.Folder else WonderIcons.InsertDriveFile,
                        tint = if (d.isDirectory) Color(0xFF4A90E2) else Color(0xFF95A5A6), trailing = { IconButton(onClick = { sources.removeAt(i) }) { Icon(WonderIcons.Close, "Remove", tint = p.secondaryText) } })
                }
            }
            Panel(title = "Options") {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(name, { name = it }, "Archive name")
                    Text("Compression", style = MaterialTheme.typography.titleSmall)
                    Segmented(listOf("Fastest", "Normal", "Smallest"), level) { level = it }
                }
            }
            WButton("Create ZIP", { save.launch(name.removeSuffix(".zip") + ".zip") }, Modifier.fillMaxWidth(), icon = WonderIcons.Archive, kind = ButtonKind.Primary, enabled = sources.isNotEmpty())
        }
    }
    progress?.let { msg -> WonderDialog(onDismissRequest = {}, title = { Text(msg.substringBefore(" ·")) }, text = { AquaProgress(null, Modifier.fillMaxWidth()) }, confirmButton = {}) }
}
