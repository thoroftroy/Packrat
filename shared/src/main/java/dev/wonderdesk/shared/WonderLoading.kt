package dev.wonderdesk.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Every wait in the suite says what is happening: a spinner with the current step, "step 2 of 4"
 * when there are steps, a bar and a time estimate when progress can be measured.
 *
 * @param step what is happening right now, e.g. "Reading 1,240 photos…"
 * @param progress 0..1 when measurable; null shows an indeterminate bar
 * @param detail extra line such as "12 MB of 40 MB"
 * @param stepIndex/stepCount optional "Step x of y"
 * @param startedAt when the work started (ms), for the time estimate; 0 = when this first showed
 */
@Composable
fun LoadingStatus(
    step: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    detail: String? = null,
    stepIndex: Int = 0,
    stepCount: Int = 0,
    startedAt: Long = 0L,
    compact: Boolean = false,
) {
    val p = wonderPalette()
    val start = remember { if (startedAt > 0) startedAt else System.currentTimeMillis() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val eta = etaText(start, now, progress)
    val elapsed = (now - start) / 1000
    if (compact) {
        Row(modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), color = p.accent, strokeWidth = 2.5.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(step, style = MaterialTheme.typography.bodyMedium, color = p.text, maxLines = 2)
                val sub = listOfNotNull(
                    if (stepCount > 0) "Step $stepIndex of $stepCount" else null,
                    detail, eta?.let { "about $it left" },
                ).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = p.secondaryText, maxLines = 1)
                if (progress != null) AquaProgress(progress, Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
        return
    }
    Column(modifier.widthIn(max = 420.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(40.dp), color = p.accent, strokeWidth = 3.5.dp)
        if (stepCount > 0) Text("Step $stepIndex of $stepCount", style = MaterialTheme.typography.labelMedium, color = p.secondaryText)
        Text(step, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = p.text, textAlign = TextAlign.Center)
        if (progress != null) AquaProgress(progress, Modifier.fillMaxWidth())
        val sub = listOfNotNull(
            progress?.let { "${(it * 100).toInt()}%" }, detail,
            eta?.let { "about $it left" } ?: if (elapsed >= 4) "${elapsed}s so far" else null,
        ).joinToString(" · ")
        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = p.secondaryText, textAlign = TextAlign.Center)
    }
}

/** Centred full-screen version. */
@Composable
fun LoadingPane(step: String, progress: Float? = null, detail: String? = null, stepIndex: Int = 0, stepCount: Int = 0, startedAt: Long = 0L) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingStatus(step, progress = progress, detail = detail, stepIndex = stepIndex, stepCount = stepCount, startedAt = startedAt)
    }
}

/** A time estimate once there's enough progress to trust one; null otherwise. */
fun etaText(startMs: Long, nowMs: Long, progress: Float?): String? {
    if (progress == null || progress <= 0.03f || progress >= 1f) return null
    val elapsed = (nowMs - startMs) / 1000.0
    if (elapsed < 2.0) return null
    val left = (elapsed / progress * (1 - progress)).toLong()
    return when {
        left < 1 -> null
        left < 60 -> "${left}s"
        left < 3600 -> "${left / 60} min"
        else -> "${left / 3600} h ${(left % 3600) / 60} min"
    }
}
