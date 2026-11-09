@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.wonderdesk.shared

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun VideoScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val prefs = remember { context.getSharedPreferences("video", Context.MODE_PRIVATE) }
    val op = rememberOperation()
    var uriText by rememberSaveable { mutableStateOf(activity.intent.data?.toString() ?: prefs.getString("last", null)) }
    var speed by remember { mutableFloatStateOf(prefs.getFloat("speed", 1f)) }
    val player = remember { ExoPlayer.Builder(context).setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true).setHandleAudioBecomingNoisy(true).build() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { value ->
        value?.let { context.keepRead(it); uriText = it.toString(); prefs.edit().putString("last", uriText).apply() }
    }
    LaunchedEffect(uriText) {
        uriText?.let {
            player.setMediaItem(MediaItem.fromUri(Uri.parse(it)), prefs.getLong("position:$it", 0L))
            player.setPlaybackSpeed(speed)
            player.prepare()
            op.notice("Video loaded. Tap play in the video controls. Codec support depends on the device.")
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { op.notice("Playback failed: ${error.errorCodeName}. File may be unsupported, missing or access revoked.") }
        }
        player.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
                uriText?.let { prefs.edit().putLong("position:$it", player.currentPosition).apply() }
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AquaButton(onClick = { picker.launch(arrayOf("video/*")) }) { Text("Open video") }
            AquaSecondaryButton(onClick = { uriText?.let { context.share(Uri.parse(it), "video/*", op::notice) } }, enabled = uriText != null) { Text("Share") }
        }
        AndroidView(factory = { PlayerView(it).apply { this.player = player; setShowSubtitleButton(true); setShowNextButton(false); setShowPreviousButton(false) } }, modifier = Modifier.weight(1f).fillMaxWidth())
        OperationStatus(op)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0.5f, 1f, 1.5f, 2f).forEach { value ->
                TextButton(onClick = { speed = value; player.setPlaybackSpeed(value); prefs.edit().putFloat("speed", value).apply() }) { Text(if (speed == value) "• ${value}×" else "${value}×") }
            }
        }
        Text("Touch controls: play/pause, seek and track/subtitle settings. Leaves originals unchanged. Playback pauses when you leave; no background video, clipping or editor yet.", style = MaterialTheme.typography.bodySmall)
    }
}
