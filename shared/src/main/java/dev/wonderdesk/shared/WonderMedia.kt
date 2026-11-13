package dev.wonderdesk.shared

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The suite's media look: the glass bars and floating controller used by Cinema's player, Memories'
 * viewer and Wayfinder's viewer, so photos and videos look the same, and follow the suite look, everywhere.
 */

/** Colours of the media glass for one suite look. [backdrop] is the viewer's background behind the picture. */
@Immutable
data class PlayerChrome(val hud: List<Color>, val bar: List<Color>, val edge: Color, val ink: Color, val accent: Color, val track: Color, val gel: List<Color>,
                        val gloss: Float, val radius: Dp, val backdrop: List<Color>)

fun playerChrome(look: WonderLook) = when (look) {
    // QuickTime X smoked glass with an Aqua blue gel button
    WonderLook.AQUA -> PlayerChrome(listOf(Color(0xD9404650), Color(0xE61A1D22)), listOf(Color(0xC04A505A), Color(0xC0202328)), Color(0x66FFFFFF), Color.White, Color(0xFF5AB0FF),
        Color(0x55FFFFFF), listOf(Color(0xFF9FD3FF), Color(0xFF2B86E8), Color(0xFF0F5FC4)), .16f, 14.dp, listOf(Color(0xFF23272D), Color(0xFF0E1013)))
    // Windows Media Player XP: Luna blue with a green play orb
    WonderLook.BLISS -> PlayerChrome(listOf(Color(0xE62A6FD6), Color(0xF0103E9E)), listOf(Color(0xE63A86F2), Color(0xE6114BB8)), Color(0xAA9FC4FF), Color.White, Color(0xFF8BE36B),
        Color(0x66FFFFFF), listOf(Color(0xFFB9F49A), Color(0xFF3FAE2A), Color(0xFF1F7A12)), .22f, 6.dp, listOf(Color(0xFF0B1F45), Color(0xFF040A18)))
    WonderLook.DARK -> PlayerChrome(listOf(Color(0xE63A3F47), Color(0xF01B1E22)), listOf(Color(0xCC3A3F47), Color(0xCC1B1E22)), Color(0x33FFFFFF), Color(0xFFEDEFF2), Color(0xFF6EA8FF),
        Color(0x44FFFFFF), listOf(Color(0xFF8FB9FF), Color(0xFF3D6FC4), Color(0xFF244A8A)), .08f, 12.dp, listOf(Color(0xFF15171A), Color(0xFF08090A)))
    WonderLook.SUPER -> PlayerChrome(listOf(Color(0xE02A1A55), Color(0xF00D0A1A)), listOf(Color(0xCC3B1F7A), Color(0xCC1B1238)), Color(0xAA00F0FF), Color(0xFFF2ECFF), Color(0xFF00F0FF),
        Color(0x5500F0FF), listOf(Color(0xFFFF7AC8), Color(0xFFB01EFF), Color(0xFF5B0FA8)), .12f, 18.dp, listOf(Color(0xFF1C1038), Color(0xFF07040F)))
}

@Composable
fun rememberPlayerChrome(): PlayerChrome { val look = LocalLook.current; return remember(look) { playerChrome(look) } }

fun mediaTime(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

/** The viewer's themed background: a soft vertical gradient in the look's colours, dimmed by [fade] while swiping a photo away. */
fun Modifier.viewerBackdrop(c: PlayerChrome, fade: Float = 1f) = this.drawBehind {
    drawRect(Brush.verticalGradient(c.backdrop.map { it.copy(alpha = it.alpha * fade) }))
}

/** Glossy round play/pause button (an Aqua gel). */
@Composable
fun GelPlay(playing: Boolean, c: PlayerChrome, size: Dp = 62.dp, onClick: () -> Unit) {
    val view = LocalView.current
    Box(Modifier.size(size).shadow(8.dp, CircleShape).clip(CircleShape).background(Brush.verticalGradient(c.gel)).border(1.dp, Color.White.copy(alpha = .55f), CircleShape)
        .drawBehind { drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = .75f), Color.White.copy(alpha = .05f))), topLeft = Offset(this.size.width * .14f, this.size.height * .05f),
            size = Size(this.size.width * .72f, this.size.height * .45f)) }
        .clickable { view.tick(); onClick() }, contentAlignment = Alignment.Center) {
        Icon(if (playing) WonderIcons.Pause else WonderIcons.PlayArrow, if (playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(size * .58f))
    }
}

@Composable
fun HudButton(icon: ImageVector, desc: String, c: PlayerChrome, size: Dp = 44.dp, tint: Color = c.ink, onClick: () -> Unit) {
    val view = LocalView.current
    Box(Modifier.size(size).clip(CircleShape).clickable { view.tick(); onClick() }, contentAlignment = Alignment.Center) { Icon(icon, desc, tint = tint, modifier = Modifier.size(size * .66f)) }
}

/** iTunes-style capsule scrubber with a glossy fill and a round knob. Drag or tap to seek. */
@Composable
fun Scrubber(value: Float, c: PlayerChrome, modifier: Modifier, onSeek: (Float) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val v = (dragging ?: value).coerceIn(0f, 1f)
    Box(modifier.height(30.dp).pointerInput(Unit) {
        detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) }
    }.pointerInput(Unit) {
        detectHorizontalDragGestures(onDragStart = { o -> dragging = (o.x / size.width).coerceIn(0f, 1f) }, onDragEnd = { dragging?.let(onSeek); dragging = null }, onDragCancel = { dragging = null }) { ch, d ->
            ch.consume(); dragging = ((dragging ?: value) + d / size.width).coerceIn(0f, 1f); onSeek(dragging!!)
        }
    }.drawBehind {
        val h = 7.dp.toPx(); val y = size.height / 2 - h / 2; val r = CornerRadius(h / 2)
        drawRoundRect(c.track, Offset(0f, y), Size(size.width, h), r)
        drawRoundRect(Brush.verticalGradient(listOf(c.accent, c.accent.copy(red = c.accent.red * .7f, green = c.accent.green * .7f, blue = c.accent.blue * .8f)), startY = y, endY = y + h),
            Offset(0f, y), Size(size.width * v, h), r)
        drawRoundRect(Color.White.copy(alpha = .35f), Offset(0f, y), Size(size.width * v, h / 2), r)
        val kx = size.width * v; val kr = (if (dragging != null) 10.dp else 8.dp).toPx()
        drawCircle(Color.Black.copy(alpha = .35f), kr + 1.5f, Offset(kx, size.height / 2 + 1.5f))
        drawCircle(Brush.verticalGradient(listOf(Color.White, Color(0xFFCFD5DC)), startY = size.height / 2 - kr, endY = size.height / 2 + kr), kr, Offset(kx, size.height / 2))
    })
}

/** Glass panel in the media look: [content] sits on the look's smoked/blue/neon glass with a gloss highlight. */
fun Modifier.mediaGlass(c: PlayerChrome, radius: Dp = c.radius, hud: Boolean = false) = this
    .shadow(if (hud) 18.dp else 8.dp, RoundedCornerShape(radius)).clip(RoundedCornerShape(radius))
    .background(Brush.verticalGradient(if (hud) c.hud else c.bar)).border(1.dp, c.edge, RoundedCornerShape(radius))
    .drawBehind { drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = c.gloss), Color.Transparent), endY = size.height * .45f)) }

/** The viewer's title strip: back, a title and subtitle, then [actions]. Floats over the photo as slim glass. */
@Composable
fun ViewerTopBar(title: String, subtitle: String?, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    val c = rememberPlayerChrome()
    Row(modifier.fillMaxWidth().statusBarsPadding().padding(10.dp).mediaGlass(c).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        HudButton(WonderIcons.ArrowBack, "Back", c, 44.dp, onClick = onBack)
        Column(Modifier.weight(1f).padding(start = 2.dp)) {
            Text(title, color = c.ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = c.ink.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CompositionLocalProvider(LocalContentColor provides c.ink) { actions() }
    }
}

/** The floating action dock at the bottom of a viewer (share, favourite, edit, delete…). */
@Composable
fun ViewerDock(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val c = rememberPlayerChrome()
    Row(modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).widthIn(max = 560.dp).fillMaxWidth().mediaGlass(c, c.radius + 6.dp, hud = true)
        .padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
fun ViewerAction(icon: ImageVector, label: String, active: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val c = rememberPlayerChrome()
    val view = LocalView.current
    val tint = when { danger -> Color(0xFFFF7A70); active -> c.accent; else -> c.ink }
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { view.tick(); onClick() }.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, color = tint.copy(alpha = .9f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** Short centred message over the picture: "+10 s", "Loop on"… */
@Composable
fun MediaHud(text: String?, modifier: Modifier = Modifier) {
    val c = rememberPlayerChrome()
    AnimatedVisibility(text != null, modifier, enter = fadeIn(tween(120)), exit = fadeOut(tween(220))) {
        var last by remember { mutableStateOf("") }
        if (text != null) last = text
        Text(last, Modifier.mediaGlass(c).padding(18.dp, 10.dp), color = c.ink, style = MaterialTheme.typography.titleLarge)
    }
}

/* ------------------------------------------------ zoomable photo ------------------------------------------------ */

/**
 * A photo that fits the screen: pinch (around your fingers) and double-tap to zoom, drag to pan with the edges held in
 * view, and, at normal size, swipe up or down to close ([onDismiss]); [onDismissProgress] reports 0..1 while dragging so the
 * viewer can fade. Horizontal swipes at normal size (or at the edge when zoomed) are left for a pager to go to the next photo.
 */
@Composable
fun ZoomableImage(image: ImageBitmap?, description: String?, onTap: () -> Unit, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null,
                  onDismissProgress: (Float) -> Unit = {}, overlay: @Composable BoxScope.() -> Unit = {}) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var box by remember { mutableStateOf(Size.Zero) }
    val c = rememberPlayerChrome()
    fun clamp(o: Offset, s: Float) = Offset(o.x.coerceIn(-(s - 1) * box.width / 2, (s - 1) * box.width / 2), o.y.coerceIn(-(s - 1) * box.height / 2, (s - 1) * box.height / 2))
    fun zoomTo(ts: Float, to: Offset) {
        val fs = scale; val fo = offset; val tt = clamp(to, ts)
        scope.launch { animate(0f, 1f, animationSpec = tween(260, easing = FastOutSlowInEasing)) { t, _ -> scale = fs + (ts - fs) * t; offset = fo + (tt - fo) * t } }
    }
    Box(modifier.fillMaxSize().onSizeChanged { box = Size(it.width.toFloat(), it.height.toFloat()) }
        .pointerInput(image) {
            detectTapGestures(onTap = { onTap() }, onDoubleTap = { p ->
                if (scale > 1.05f) zoomTo(1f, Offset.Zero) else zoomTo(2.5f, (Offset(size.width / 2f, size.height / 2f) - p) * 1.5f)
            })
        }
        .pointerInput(image, onDismiss != null) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var mode = 0 // 0 deciding, 1 zoom/pan, 2 swipe-to-close, 3 leave it to the pager
                var travel = Offset.Zero
                do {
                    val ev = awaitPointerEvent()
                    if (ev.changes.any { it.isConsumed }) { if (mode == 0) mode = 3 }
                    val fingers = ev.changes.count { it.pressed }
                    val pan = ev.calculatePan(); val zoom = ev.calculateZoom()
                    if (mode == 0) {
                        if (fingers >= 2) mode = 1 else {
                            travel += pan
                            if (travel.getDistance() > slop) mode = when {
                                scale > 1.01f -> 1
                                onDismiss != null && kotlin.math.abs(travel.y) > kotlin.math.abs(travel.x) * 1.4f -> 2
                                else -> 3
                            }
                        }
                    }
                    when (mode) {
                        1 -> {
                            val ns = (scale * zoom).coerceIn(.85f, 8f)
                            val centre = Offset(box.width / 2, box.height / 2)
                            val cen = if (fingers >= 2) ev.calculateCentroid() - centre else Offset.Zero
                            val z = ns / scale
                            val wanted = cen * (1 - z) + offset * z + pan
                            val clamped = if (ns <= 1f) Offset.Zero else clamp(wanted, ns)
                            // At the left/right edge of a zoomed photo, a one-finger sideways drag goes on to the next photo.
                            val atEdge = fingers == 1 && kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y) && kotlin.math.abs(clamped.x - wanted.x) > .5f && kotlin.math.abs(clamped.x - offset.x) < .5f
                            if (atEdge) mode = 3 else {
                                scale = ns; offset = clamped
                                ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                        2 -> {
                            scope.launch { drag.snapTo(drag.value + pan.y) }
                            onDismissProgress((kotlin.math.abs(drag.value) / (box.height * .4f)).coerceIn(0f, 1f))
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    }
                } while (ev.changes.any { it.pressed })
                if (mode == 2) {
                    if (kotlin.math.abs(drag.value) > box.height * .16f) onDismiss?.invoke()
                    else scope.launch { drag.animateTo(0f, spring(dampingRatio = .8f, stiffness = 500f)) { onDismissProgress((kotlin.math.abs(value) / (box.height * .4f)).coerceIn(0f, 1f)) } }
                }
                if (scale < 1f) zoomTo(1f, Offset.Zero)
            }
        }, contentAlignment = Alignment.Center) {
        if (image == null) CircularProgressIndicator(color = c.accent, strokeWidth = 3.dp)
        else {
            val shrink = 1f - (kotlin.math.abs(drag.value) / (box.height.coerceAtLeast(1f))) * .35f
            Image(image, description, Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale * shrink; scaleY = scale * shrink; translationX = offset.x; translationY = offset.y + drag.value
            }, contentScale = ContentScale.Fit)
        }
        overlay()
    }
}

/* ------------------------------------------------ inline video ------------------------------------------------ */

/**
 * A video (or song) playing right inside a viewer page, with the suite's glass controls: gel play button, ±10 s,
 * scrubber with elapsed and remaining time, speed, loop, mute and fit/fill. Plays while [active] (the page on screen)
 * and the app is open, pauses otherwise, and keeps the screen on while it plays. Tap shows or hides the controls
 * ([controls]/[onToggleControls]); double-tap the left or right third to skip 10 s.
 *
 * @param bottomSpace room to leave under the controller for the viewer's own dock.
 */
@Composable
fun WonderVideo(uri: Uri, active: Boolean, controls: Boolean, onToggleControls: (Boolean) -> Unit, modifier: Modifier = Modifier,
                audioOnly: Boolean = false, bottomSpace: Dp = 0.dp, startAt: Long = 0L, onPosition: ((Long, Long) -> Unit)? = null,
                extra: @Composable RowScope.(PlayerChrome) -> Unit = {}) {
    val context = LocalContext.current
    val view = LocalView.current
    val c = rememberPlayerChrome()
    val player = remember(uri) {
        ExoPlayer.Builder(context).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build().apply {
            setMediaItem(MediaItem.fromUri(uri)); prepare(); if (startAt > 0) seekTo(startAt)
        }
    }
    var playing by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var loop by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var fill by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf<String?>(null) }
    val resumed = rememberIsResumed()
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying; view.keepScreenOn = isPlaying }
            override fun onPlaybackStateChanged(state: Int) { ended = state == Player.STATE_ENDED; if (state == Player.STATE_READY) dur = player.duration.coerceAtLeast(0) }
            override fun onPlayerError(e: PlaybackException) { error = "This video can't be played here" }
        }
        player.addListener(l)
        onDispose { onPosition?.invoke(player.currentPosition, player.duration); player.removeListener(l); player.release(); view.keepScreenOn = false }
    }
    LaunchedEffect(active, resumed) { if (active && resumed) player.play() else player.pause() }
    LaunchedEffect(controls, playing) {
        while (true) { pos = player.currentPosition; dur = player.duration.coerceAtLeast(0); delay(if (controls && playing) 200 else 1000) }
    }
    LaunchedEffect(controls, playing) { if (controls && playing) { delay(3200); onToggleControls(false) } }
    LaunchedEffect(hud) { if (hud != null) { delay(800); hud = null } }
    fun toggle() { if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } }
    Box(modifier.fillMaxSize()) {
        if (!audioOnly) AndroidView({ ctx -> PlayerView(ctx).apply { useController = false; this.player = player; setShutterBackgroundColor(android.graphics.Color.TRANSPARENT); setBackgroundColor(android.graphics.Color.TRANSPARENT) } },
            Modifier.fillMaxSize(), update = { it.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT })
        else Box(Modifier.align(Alignment.Center).size(180.dp).mediaGlass(c, 36.dp, hud = true), contentAlignment = Alignment.Center) {
            Icon(WonderIcons.MusicNote, null, Modifier.size(96.dp), tint = c.ink.copy(alpha = .85f))
        }
        Box(Modifier.fillMaxSize().pointerInput(uri) {
            detectTapGestures(onTap = { onToggleControls(!controls) }, onDoubleTap = { o ->
                when { o.x < size.width / 3 -> { player.seekBack(); hud = "−10 s" }; o.x > size.width * 2 / 3 -> { player.seekForward(); hud = "+10 s" }; else -> toggle() }
            })
        })
        MediaHud(hud, Modifier.align(Alignment.Center))
        error?.let { Text(it, Modifier.align(Alignment.Center).mediaGlass(c).padding(18.dp, 12.dp), color = c.ink, style = MaterialTheme.typography.bodyLarge) }
        // A paused video always shows its play button so it's obvious it can play.
        AnimatedVisibility(controls || !playing, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            if (error == null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                if (controls) Box(Modifier.mediaGlass(c, 26.dp)) { HudButton(WonderIcons.Replay10, "Back 10 seconds", c, 52.dp) { player.seekBack() } }
                GelPlay(playing, c, 72.dp) { toggle() }
                if (controls) Box(Modifier.mediaGlass(c, 26.dp)) { HudButton(WonderIcons.Forward10, "Forward 10 seconds", c, 52.dp) { player.seekForward() } }
            }
        }
        AnimatedVisibility(controls, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.navigationBarsPadding().padding(bottom = bottomSpace).padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 640.dp).fillMaxWidth()
                .mediaGlass(c, c.radius + 4.dp, hud = true).padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(mediaTime(pos), color = c.ink.copy(alpha = .9f), style = MaterialTheme.typography.labelMedium)
                    Scrubber(if (dur > 0) pos.toFloat() / dur else 0f, c, Modifier.weight(1f).padding(horizontal = 10.dp)) { f -> player.seekTo((f * dur).toLong()); pos = (f * dur).toLong() }
                    Text("-" + mediaTime(dur - pos), color = c.ink.copy(alpha = .9f), style = MaterialTheme.typography.labelMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudButton(if (muted) WonderIcons.VolumeOff else WonderIcons.VolumeUp, if (muted) "Unmute" else "Mute", c, 40.dp) { muted = !muted; player.volume = if (muted) 0f else 1f; hud = if (muted) "Muted" else "Sound on" }
                    HudButton(WonderIcons.Repeat, "Loop", c, 40.dp, tint = if (loop) c.accent else c.ink.copy(alpha = .85f)) {
                        loop = !loop; player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF; hud = if (loop) "Loop on" else "Loop off" }
                    Text("${if (speed % 1 == 0f) speed.toInt() else speed}×", Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; 2f -> .5f; .5f -> .75f; else -> 1f }; player.playbackParameters = PlaybackParameters(speed); hud = "Speed ${speed}×"
                    }.padding(10.dp), color = c.ink, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    extra(c)
                    if (!audioOnly) HudButton(WonderIcons.Fullscreen, if (fill) "Fit" else "Fill screen", c, 40.dp) { fill = !fill; hud = if (fill) "Fill screen" else "Fit" }
                }
            }
        }
    }
}
