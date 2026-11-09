package dev.wonderdesk.shared

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen() {
    val context = LocalContext.current
    val op = rememberOperation()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var title by rememberSaveable { mutableStateOf("") }
    val month = YearMonth.parse(monthText)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { monthText = month.minusMonths(1).toString() }) { Text("Previous") }
            Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy")), Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { monthText = month.plusMonths(1).toString() }) { Text("Next") }
        }
        Row(Modifier.fillMaxWidth()) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f).padding(8.dp), style = MaterialTheme.typography.labelSmall) } }
        val leading = month.atDay(1).dayOfWeek.value - 1
        val cells = (List(leading) { 0 } + (1..month.lengthOfMonth()).toList()).chunked(7)
        cells.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (week + List(7 - week.size) { 0 }).forEach { day ->
                    if (day == 0) Spacer(Modifier.weight(1f))
                    else TextButton(onClick = { selectedText = month.atDay(day).toString() }, Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(0.dp)) { Text(if (selectedText == month.atDay(day).toString()) "[$day]" else "$day") }
                }
            }
        }
        Text("Selected: $selectedText · ${ZoneId.systemDefault().id}")
        OutlinedTextField(title, { title = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("Event title") }, singleLine = true)
        AquaButton(onClick = {
            val start = LocalDate.parse(selectedText).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            context.launchSafely(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 60 * 60 * 1000L), op::notice)
        }, enabled = title.isNotBlank()) { Text("Create in calendar app…") }
        OperationStatus(op)
        Text("Review and save the event in your installed calendar app. Chronicle does not claim it was saved, request calendar access or upload anything. Calendar accounts/reminders are managed by that app.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { monthText = YearMonth.now().toString(); selectedText = LocalDate.now().toString() }) { Text("Today") }
    }
}
