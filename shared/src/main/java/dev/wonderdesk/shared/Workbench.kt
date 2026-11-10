package dev.wonderdesk.shared

import android.content.ClipData
import android.graphics.drawable.BitmapDrawable
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(val id: String, val title: String, val description: String, val limits: String)

object Catalog {
    val apps = listOf(
        AppInfo("wayfinder", "Wayfinder", "Browse and manage a folder you choose using Android's document picker.", "No unrestricted storage access, trash, remote protocols, tabs or transfer queue yet. Provider support varies."),
        AppInfo("packrat", "Packrat", "Create, inspect, verify and safely extract ZIP archives.", "ZIP only. No encrypted archives, TAR, 7z or RAR. Extraction is limited to 2,000 entries and 256 MiB."),
        AppInfo("spyglass", "Spyglass", "Open, zoom, pan, rotate and share selected images.", "Viewing only; rotation is a preview. No crop, destructive editing, RAW or EXIF editor yet."),
        AppInfo("memories", "Memories", "Build a local collection of photos you choose; filter, view and share them.", "References only, not copies or backups. No automatic MediaStore library scan, album editor or metadata editing yet."),
        AppInfo("cinema", "Cinema", "Play selected videos using Android Media3 with touch playback controls.", "Device-supported codecs only. No editing, clipping or background playback. Videos pause when you leave."),
        AppInfo("melody", "Melody", "A local-first music library and background music player.", "This is not yet BlissPlayer parity. Loudness scanning, gain boost, per-output presets, skip segments and tag writing remain unavailable."),
        AppInfo("pages", "Pages", "Read selected PDF documents with page navigation, zoom and sharing.", "PDF only. No text search, annotations, password support or text selection on this renderer yet."),
        AppInfo("scribble", "Scribble", "Write a recovered local draft or import and export a UTF-8 text document.", "Export creates a new document, never silently overwrites the source. One draft; no notebooks, rich text or syntax highlighting yet."),
        AppInfo("shutter", "Shutter", "Annotate an image you select and save a new PNG copy.", "Screen capture and recording are not implemented. Use Android's screenshot controls, then open the image here. No silent capture."),
        AppInfo("pulse", "Pulse", "Inspect live battery, memory, storage and device information exposed by Android.", "No unrestricted process list, process killing, GPU/CPU utilization or sensor claims. Unavailable is not zero."),
        AppInfo("abacus", "Abacus", "Calculate scientific expressions and convert length, mass and temperature units.", "Trigonometry uses selectable radians or degrees. Conversion results are approximate. No symbolic algebra yet."),
        AppInfo("chronicle", "Chronicle", "Browse a local month calendar and create events in your chosen Android calendar app.", "Event editing, accounts, reminders and synchronization belong to the external calendar app. No private calendar database yet."),
        AppInfo("timepiece", "Timepiece", "World clocks and a stopwatch, with official system alarm and timer integration.", "Alarm and timer delivery is delegated to the system clock app, if installed. No Wonderdesk widgets yet."),
        AppInfo("safekeeper", "Safekeeper", "Export selected accessible documents to a verified ZIP and restore into a new folder.", "Not a device/system backup. No private app data, scheduled backups, encryption or incremental snapshots. Test restore before trusting any backup."),
        AppInfo("beacon", "Beacon", "Search launchable apps and Wonderdesk aliases, or calculate an expression.", "No file, note or private-content indexing. Optional web search opens your browser only on request."),
        AppInfo("vault", "Vault", "Store local credentials with authenticated AES-256-GCM, generate passwords, rotate the master password and exchange encrypted v2 backups with Linux.", "Independent security audit pending. No recovery, autofill, sync, biometrics or notes. Locks in background/after five minutes idle; clipboard expiry is best effort. Existing vaults are never replaced by import."),
        AppInfo("controlroom", "Control Room", "Open official Android settings for display, sound, connections, accessibility and apps.", "Cannot replace protected system settings, the lock screen or notification shade. Theme choices here apply to this app only."),
        AppInfo("apphub", "App Hub", "Inspect launchable installed apps, open their Android details and request uninstall.", "Not an app store or updater. No remote catalog, silent installation or update-all claims.")
    )
    fun get(id: String) = apps.first { it.id == id }
}

open class WorkbenchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = packageName.substringAfterLast('.')
        if (id == "vault") window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            AppFrame(Catalog.get(id)) {
                when (id) {
                    "wayfinder" -> FilesScreen()
                    "packrat", "safekeeper" -> ArchiveScreen(id == "safekeeper")
                    "spyglass", "memories" -> PhotosScreen(id == "memories")
                    "cinema" -> VideoScreen()
                    "pages" -> PdfScreen()
                    "scribble" -> EditorScreen()
                    "shutter" -> AnnotationScreen()
                    "pulse" -> MonitorScreen()
                    "abacus" -> CalculatorScreen()
                    "chronicle" -> CalendarScreen()
                    "timepiece" -> ClockScreen()
                    "beacon" -> AppSearchScreen()
                    "vault" -> VaultScreen()
                    "controlroom" -> SettingsScreen()
                    "apphub" -> AppSearchScreen(manage = true)
                }
            }
        }
    }
}

enum class WonderLook(val label: String, val dark: Boolean) {
    AQUA("Aqua", false), BLISS("Bliss", false), DARK("Dark Bliss", true), SUPER("Super", true)
}

enum class WonderMotion(val label: String) { FULL("Full"), REDUCED("Reduced"), OFF("Off") }

@Composable
fun WonderTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    var look by remember {
        mutableStateOf(runCatching { WonderLook.valueOf(prefs.getString("look", "AQUA") ?: "AQUA") }.getOrDefault(WonderLook.AQUA))
    }
    var motion by remember {
        mutableStateOf(runCatching { WonderMotion.valueOf(prefs.getString("motion", "FULL") ?: "FULL") }.getOrDefault(WonderMotion.FULL))
    }
    val choose: (WonderLook) -> Unit = { look = it; prefs.edit().putString("look", it.name).apply() }
    val chooseMotion: (WonderMotion) -> Unit = { motion = it; prefs.edit().putString("motion", it.name).apply() }
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !look.dark
                isAppearanceLightNavigationBars = !look.dark
            }
        }
    }
    val colors = when (look) {
        WonderLook.AQUA -> lightColorScheme(primary = Color(0xFF096DE1), secondary = Color(0xFF4F637A), background = Color(0xFFE8E8EC), surface = Color(0xFFF8FBFE), onSurface = Color(0xFF192C42), onBackground = Color(0xFF192C42))
        WonderLook.BLISS -> lightColorScheme(primary = Color(0xFF0058E6), secondary = Color(0xFF3C9C3C), background = Color(0xFFECE9D8), surface = Color(0xFFF8F6EA), onSurface = Color.Black, onBackground = Color.Black)
        WonderLook.DARK -> darkColorScheme(primary = Color(0xFF93B9FF), secondary = Color(0xFFB1CCF7), background = Color(0xFF23262B), surface = Color(0xFF2E333A), onSurface = Color(0xFFE8E8E8), onBackground = Color(0xFFE8E8E8))
        WonderLook.SUPER -> darkColorScheme(primary = Color(0xFF00F0FF), secondary = Color(0xFFFF2E9A), background = Color(0xFF0D0A1A), surface = Color(0xFF1A1230), onSurface = Color(0xFFF2ECFF), onBackground = Color(0xFFF2ECFF))
    }
    CompositionLocalProvider(LocalLook provides look, LocalLookPreference provides choose, LocalMotion provides motion, LocalMotionPreference provides chooseMotion,
        LocalIsDark provides look.dark, LocalDarkPreference provides { value -> choose(if (value) WonderLook.DARK else WonderLook.AQUA) }) {
        MaterialTheme(colorScheme = colors, typography = wonderTypography(look)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
        }
    }
}

val LocalLook = staticCompositionLocalOf { WonderLook.AQUA }
val LocalLookPreference = staticCompositionLocalOf<(WonderLook) -> Unit> { {} }
val LocalMotion = staticCompositionLocalOf { WonderMotion.FULL }
val LocalMotionPreference = staticCompositionLocalOf<(WonderMotion) -> Unit> { {} }
val LocalDarkPreference = staticCompositionLocalOf<(Boolean) -> Unit> { {} }
val LocalIsDark = staticCompositionLocalOf { false }

@Composable
fun AppFrame(app: AppInfo, transparentBackground: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    WonderTheme {
        val context = LocalContext.current
        var about by remember { mutableStateOf(false) }
        var licenses by remember { mutableStateOf(false) }
        val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown" }
        val ecosystem = remember { SharedCatalog.ecosystemVersion(context) }
        val dark = LocalIsDark.current
        val look = LocalLook.current
        val motion = LocalMotion.current
        val density = LocalDensity.current.density
        val fx = remember { WonderMotionField() }
        Box(Modifier.fillMaxSize().wonderFx(fx, motion != WonderMotion.OFF, look, motion == WonderMotion.FULL, density).background(if (transparentBackground) Color.Transparent else MaterialTheme.colorScheme.background)) {
          if (!transparentBackground || look == WonderLook.AQUA || look == WonderLook.SUPER) WonderBackdrop(look, motion, transparentBackground)
          if (transparentBackground) {
              val bar = if (dark) Color(0xFF172636) else Color(0xFFF1F6FA)
              Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).align(Alignment.TopCenter).background(bar))
              Box(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).align(Alignment.BottomCenter).background(bar))
          }
          Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            CompositionLocalProvider(LocalContentColor provides if (look == WonderLook.BLISS || look == WonderLook.SUPER) Color.White else MaterialTheme.colorScheme.onSurface) {
                Row(Modifier.fillMaxWidth().aquaBrushedMetal(dark).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    val appIcon = remember { runCatching { context.packageManager.getApplicationIcon(context.packageName).toBitmap(64, 64) }.getOrNull() }
                    if (appIcon != null) Image(
                        appIcon.asImageBitmap(), "App icon",
                        Modifier.size(26.dp).align(Alignment.CenterVertically)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(app.title, style = MaterialTheme.typography.headlineSmall)
                        Text("App $version · Wonderdesk $ecosystem · preview", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { about = true }) { Text("About") }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
          }
          WonderFxOverlay(fx)
        }
        if (about) {
            WonderDialog(onDismissRequest = { about = false }, title = { Text("${app.title} $version") }, text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AboutBody(app, version)
                    TextButton(onClick = { licenses = true }) { Text("Third-party licenses") }
                }
            }, confirmButton = { TextButton(onClick = { about = false }) { Text("Done") } })
        }
        if (licenses) WonderDialog(onDismissRequest = { licenses = false }, title = { Text("Third-party licenses") }, text = {
            val text = remember { listOf("NOTICE.txt", "APACHE-2.0.txt").joinToString("\n\n") { name -> context.assets.open("licenses/$name").bufferedReader().use { it.readText() } } }
            Text(text, Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
        }, confirmButton = { TextButton(onClick = { licenses = false }) { Text("Close") } })
    }
}

class Operation(private val scope: CoroutineScope) {
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set
    fun notice(text: String) { message = text }
    fun <T> run(label: String, work: () -> T, done: (T) -> Unit = {}) {
        if (busy) return
        busy = true
        message = "$label…"
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { work() }
                done(result)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { message = "Could not complete: ${e.localizedMessage ?: e.javaClass.simpleName}. Access may have been revoked; choose the item again." }
            finally { busy = false }
        }
    }
}

@Composable fun rememberOperation(): Operation {
    val scope = rememberCoroutineScope()
    return remember(scope) { Operation(scope) }
}

@Composable fun OperationStatus(op: Operation) {
    if (op.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (op.message.isNotEmpty()) Text(op.message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
}

fun Context.keepRead(uri: Uri) {
    // Some share/photo providers offer only a temporary grant. Never pretend it is persistent.
    runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}

fun Context.displayName(uri: Uri): String = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
    if (it.moveToFirst()) it.getString(0) else null
} ?: "Document"

fun Context.launchSafely(intent: Intent, report: (String) -> Unit) {
    try { startActivity(intent) } catch (e: Exception) { report("No compatible app, or Android blocked this action. ${e.localizedMessage ?: ""}") }
}

fun Context.share(uri: Uri, type: String, report: (String) -> Unit) {
    launchSafely(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        this.type = type
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("Shared document", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share with"), report)
}

fun Context.copyText(label: String, text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
}
