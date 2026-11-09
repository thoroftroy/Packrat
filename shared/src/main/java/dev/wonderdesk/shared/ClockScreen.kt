package dev.wonderdesk.shared

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.AlarmClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun elapsedLabel(millis: Long): String = String.format(Locale.ROOT, "%02d:%02d.%01d", millis / 60000, millis / 1000 % 60, millis / 100 % 10)

@Composable
fun ClockScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("stopwatch", Context.MODE_PRIVATE) }
    val op = rememberOperation()
    var now by remember { mutableStateOf(Instant.now()) }
    var started by remember { mutableLongStateOf(prefs.getLong("started", 0L).let { if (it > SystemClock.elapsedRealtime()) 0L else it }) }
    var accumulated by remember { mutableLongStateOf(prefs.getLong("accumulated", 0L)) }
    var elapsed by remember { mutableLongStateOf(accumulated) }
    var timerMinutes by rememberSaveable { mutableStateOf("5") }
    var zoneText by rememberSaveable { mutableStateOf("Europe/London") }
    var zones by remember { mutableStateOf(prefs.getStringSet("zones", setOf(ZoneId.systemDefault().id))!!.sorted()) }
    fun saveStopwatch() { prefs.edit().putLong("started", started).putLong("accumulated", accumulated).apply() }
    LaunchedEffect(started, accumulated) {
        while (true) {
            now = Instant.now()
            elapsed = accumulated + if (started > 0) (SystemClock.elapsedRealtime() - started).coerceAtLeast(0) else 0
            delay(if (started > 0) 100 else 1000)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("World clocks", style = MaterialTheme.typography.titleMedium)
        zones.forEach { zone ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text(zone); Text(now.atZone(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm:ss")), style = MaterialTheme.typography.titleLarge) }
                TextButton(onClick = { zones = zones - zone; prefs.edit().putStringSet("zones", zones.toSet()).apply() }) { Text("Remove") }
            }
        }
        OutlinedTextField(zoneText, { zoneText = it }, Modifier.fillMaxWidth(), label = { Text("Time zone (e.g. America/New_York)") }, singleLine = true)
        TextButton(onClick = {
            runCatching { ZoneId.of(zoneText) }.onSuccess { zones = (zones + it.id).distinct(); prefs.edit().putStringSet("zones", zones.toSet()).apply(); op.notice("Clock added") }.onFailure { op.notice("Unknown time zone; use a region/city such as Asia/Tokyo.") }
        }) { Text("Add world clock") }
        HorizontalDivider()
        Text("Stopwatch", style = MaterialTheme.typography.titleMedium)
        Text(elapsedLabel(elapsed), style = MaterialTheme.typography.displayMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AquaButton(onClick = { if (started == 0L) started = SystemClock.elapsedRealtime() else { accumulated = elapsed; started = 0L }; saveStopwatch() }) { Text(if (started == 0L) "Start" else "Pause") }
            AquaSecondaryButton(onClick = { started = 0L; accumulated = 0L; elapsed = 0L; saveStopwatch() }) { Text("Reset") }
        }
        HorizontalDivider()
        TextButton(onClick = { context.launchSafely(Intent(AlarmClock.ACTION_SHOW_ALARMS), op::notice) }) { Text("Open system alarms") }
        OutlinedTextField(timerMinutes, { timerMinutes = it.take(5) }, Modifier.fillMaxWidth(), label = { Text("Timer minutes (1–1440)") }, singleLine = true)
        AquaSecondaryButton(onClick = {
            val minutes = timerMinutes.toIntOrNull()
            if (minutes == null || minutes !in 1..1440) op.notice("Enter 1 to 1440 minutes")
            else context.launchSafely(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60).putExtra(AlarmClock.EXTRA_MESSAGE, "Wonderdesk timer").putExtra(AlarmClock.EXTRA_SKIP_UI, false), op::notice)
        }) { Text("Set in system clock…") }
        OperationStatus(op)
        Text("Alarms and timer delivery use the installed system clock app, with its own confirmation and permissions. Stopwatch uses monotonic time; it is not a background alarm and resets its running start after a device reboot.", style = MaterialTheme.typography.bodySmall)
    }
}
