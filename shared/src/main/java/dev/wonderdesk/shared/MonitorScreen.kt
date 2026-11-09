package dev.wonderdesk.shared

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.StatFs
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

fun monitorReport(context: Context): String {
    val memory = ActivityManager.MemoryInfo()
    context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
    val storage = StatFs(context.filesDir.absolutePath)
    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val temperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
    fun bytes(value: Long) = Formatter.formatFileSize(context, value)
    return """
        Device: ${Build.MANUFACTURER} ${Build.MODEL}
        Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        CPU cores available to app: ${Runtime.getRuntime().availableProcessors()}

        System memory total: ${bytes(memory.totalMem)}
        System memory available: ${bytes(memory.availMem)}
        Android low-memory state: ${memory.lowMemory}
        This app PSS: ${bytes(Debug.getPss() * 1024L)}

        App volume total: ${bytes(storage.totalBytes)}
        App volume available: ${bytes(storage.availableBytes)}

        Battery: ${if (level >= 0 && scale > 0) "${level * 100 / scale}%" else "Unavailable"}
        Battery temperature: ${if (temperature != Int.MIN_VALUE) "${temperature / 10.0} °C (reported by Android)" else "Unavailable"}

        CPU/GPU utilization and temperature: unavailable in this build.
        Other apps' processes and task killing: restricted; not implemented.
        No root, device identifiers, installed-app list or personal file paths collected.
    """.trimIndent()
}

@Composable
fun MonitorScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val op = rememberOperation()
    var report by remember { mutableStateOf("Reading Android statistics…") }
    var active by remember { mutableStateOf(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var export by remember { mutableStateOf("") }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val snapshot = export
            op.run("Exporting the displayed diagnostic report", { context.contentResolver.openOutputStream(uri, "w")!!.use { it.write(snapshot.toByteArray()) } }) { op.notice("Report saved locally. Nothing was uploaded.") }
        }
    }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, _ -> active = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(active) {
        while (active) {
            report = withContext(Dispatchers.IO) { runCatching { monitorReport(context) }.getOrElse { "Statistics unavailable: ${it.message}" } }
            delay(2000)
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Live Android readings, refreshed every 2 seconds while visible. Unavailable does not mean zero.", style = MaterialTheme.typography.bodySmall)
        AquaSecondaryButton(onClick = { export = report; save.launch("Wonderdesk-device-report.txt") }) { Text("Export displayed report") }
        OperationStatus(op)
        Text(report, Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium)
    }
}
