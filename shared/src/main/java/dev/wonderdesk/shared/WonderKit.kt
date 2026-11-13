package dev.wonderdesk.shared

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ------------------------------------------------------------------------------------------
 * WonderKit: the shared app chrome and controls for every Wonderdesk app.
 * Aqua follows Mac OS X Snow Leopard (unified toolbar, grouped white panels, pill search,
 * blue gel default button). Bliss follows Windows XP Luna, Dark Bliss is its night variant,
 * Super is the neon look. Every control asks [wonderPalette] for its colours.
 * ---------------------------------------------------------------------------------------- */

@Immutable
data class WonderPalette(
    val look: WonderLook,
    val toolbar: List<Color>,
    val toolbarLine: Color,
    val toolbarText: Color,
    val toolbarIcon: Color,
    val window: Color,
    val panel: Color,
    val panelBorder: Color,
    val separator: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color,
    val onAccent: Color,
    val selection: Color,
    val field: Color,
    val fieldBorder: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
    val tabBar: List<Color>,
    val tabSelected: Color,
    val tabIdle: Color,
    val panelRadius: Dp,
    val dark: Boolean,
)

@Composable
fun wonderPalette(): WonderPalette = paletteFor(LocalLook.current)

fun paletteFor(look: WonderLook): WonderPalette = when (look) {
    WonderLook.AQUA -> WonderPalette(
        look, listOf(Color(0xFFEDEDED), Color(0xFFD9D9D9), Color(0xFFC2C2C2)), Color(0xFF8C8C8C),
        Color(0xFF1C1C1C), Color(0xFF3C3C3C), Color(0xFFE4E6EA), Color.White, Color(0xFFBFC3C8), Color(0xFFE3E5E8),
        Color(0xFF1B1D21), Color(0xFF6B7078), Color(0xFF1E7FE8), Color.White, Color(0xFF3875D7),
        Color.White, Color(0xFF9DA3AB), Color(0xFFD7372F), Color(0xFF2E9E44), Color(0xFFE39A12),
        listOf(Color(0xFFE9E9E9), Color(0xFFCFCFCF)), Color(0xFF1E7FE8), Color(0xFF5E636A), 10.dp, false
    )
    WonderLook.BLISS -> WonderPalette(
        look, listOf(Color(0xFF0A5FEA), Color(0xFF2B7CF5), Color(0xFF0054E3)), Color(0xFF003C9E),
        Color.White, Color.White, Color(0xFFECE9D8), Color.White, Color(0xFF7F9DB9), Color(0xFFE5E2D3),
        Color(0xFF000000), Color(0xFF5A5A5A), Color(0xFF0058E6), Color.White, Color(0xFF316AC5),
        Color.White, Color(0xFF7F9DB9), Color(0xFFC1311F), Color(0xFF3C9C3C), Color(0xFFD08A00),
        listOf(Color(0xFFF7F5EC), Color(0xFFE3DFCB)), Color(0xFF0058E6), Color(0xFF4A4A4A), 4.dp, false
    )
    WonderLook.DARK -> WonderPalette(
        look, listOf(Color(0xFF454B54), Color(0xFF353A41), Color(0xFF2A2E34)), Color(0xFF15171A),
        Color(0xFFEDEFF2), Color(0xFFD5DAE0), Color(0xFF1C1F23), Color(0xFF292D33), Color(0xFF3B4048), Color(0xFF353A41),
        Color(0xFFEDEFF2), Color(0xFF9AA2AD), Color(0xFF6EA8FF), Color(0xFF0B1A2E), Color(0xFF3D6FC4),
        Color(0xFF1F2227), Color(0xFF4A5059), Color(0xFFFF6B61), Color(0xFF5BC777), Color(0xFFF2B33D),
        listOf(Color(0xFF33373E), Color(0xFF24272C)), Color(0xFF6EA8FF), Color(0xFF8F98A3), 8.dp, true
    )
    WonderLook.SUPER -> WonderPalette(
        look, listOf(Color(0xFF2A1A55), Color(0xFF3B1F7A), Color(0xFF1B1238)), Color(0xFFFF2E9A),
        Color(0xFFF2ECFF), Color(0xFF7DF9FF), Color(0xFF0D0A1A), Color(0xFF1A1230), Color(0xFF3B2A6E), Color(0xFF2A1E4D),
        Color(0xFFF2ECFF), Color(0xFFB4A8D6), Color(0xFF00F0FF), Color(0xFF07131A), Color(0xFFB01EFF),
        Color(0xFF140E28), Color(0xFF5B3FA8), Color(0xFFFF4F7B), Color(0xFF3CFFA8), Color(0xFFFFCE68),
        listOf(Color(0xFF21163F), Color(0xFF120C26)), Color(0xFF00F0FF), Color(0xFF9C8FC4), 16.dp, true
    )
}

/* ---------------------------------- activity + scaffold ---------------------------------- */

/** Base activity: edge-to-edge drawing, light/dark bar icons are handled by [WonderTheme]. */
abstract class WonderActivity : ComponentActivity() {
    open val secure: Boolean = false
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        if (secure) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        // Decode the app's artwork (toolbar, About) in the background so no screen waits for it.
        val app = applicationContext
        Thread { runCatching { app.appArt(96); app.appArt(192) } }.start()
        setContent { Content() }
    }

    @Composable abstract fun Content()
}

data class WonderTab(val key: String, val label: String, val icon: ImageVector)
data class WonderMenuItem(val label: String, val icon: ImageVector? = null, val danger: Boolean = false, val onClick: () -> Unit)

/** Shows a short message above the tab bar. Replaces permanent status text. */
val LocalToast = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Counts taps on the tab that's already selected; lists use it to scroll back to the top (see [ScrollToTopOnReselect]). */
val LocalTabReselect = staticCompositionLocalOf<State<Int>> { mutableIntStateOf(0) }

/** Opens the built-in Settings & About page. */
val LocalOpenSettings = staticCompositionLocalOf<() -> Unit> { {} }

class ToastState {
    var message by mutableStateOf<String?>(null)
    var serial by mutableIntStateOf(0)
    fun show(text: String) { message = text; serial++ }
}

/**
 * The window every app lives in.
 *
 * @param tabs bottom tab bar entries; empty hides the bar.
 * @param title toolbar title; defaults to the app name.
 * @param onBack shows a back arrow in the toolbar and handles the system back gesture.
 * @param actions toolbar buttons on the right (use [ToolbarButton]).
 * @param menu extra overflow-menu entries placed above Settings and About.
 * @param settings extra sections shown on the Settings page above Appearance.
 */
@Composable
fun WonderApp(
    appName: String,
    about: String,
    tabs: List<WonderTab> = emptyList(),
    selectedTab: String = tabs.firstOrNull()?.key ?: "",
    onTab: (String) -> Unit = {},
    title: String = appName,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    menu: List<WonderMenuItem> = emptyList(),
    settings: (@Composable ColumnScope.() -> Unit)? = null,
    showChrome: Boolean = true,
    needs: List<WonderNeed> = emptyList(),
    content: @Composable BoxScope.() -> Unit,
) {
    WonderKitTheme {
        val p = wonderPalette()
        val look = LocalLook.current
        val motion = LocalMotion.current
        val density = LocalDensity.current.density
        val fx = remember { WonderMotionField() }
        val toast = remember { ToastState() }
        val reselect = remember { mutableIntStateOf(0) }
        val tabClick: (String) -> Unit = { k -> if (k == selectedTab) reselect.intValue++; onTab(k) }
        var settingsOpen by rememberSaveable { mutableStateOf(false) }
        BackHandler(enabled = settingsOpen) { settingsOpen = false }
        if (onBack != null) BackHandler(enabled = !settingsOpen) { onBack() }
        CompositionLocalProvider(
            LocalToast provides toast::show,
            LocalTabReselect provides reselect,
            LocalOpenSettings provides { settingsOpen = true },
            LocalContentColor provides p.text,
        ) {
            Box(
                Modifier.fillMaxSize()
                    .wonderFx(fx, motion != WonderMotion.OFF, look, motion == WonderMotion.FULL, density)
                    .background(p.window)
            ) {
                WonderBackdrop(look, motion, false)
                if (settingsOpen) {
                    Column(Modifier.fillMaxSize().wonderEnter(motion, "settings")) {
                        WonderToolbar("Settings", null, onBack = { settingsOpen = false }, appIcon = false) {}
                        SettingsPage(appName, about, settings, needs)
                    }
                } else {
                    // Landscape on a phone: tabs move to a rail on the left so the content keeps its height.
                    val conf = androidx.compose.ui.platform.LocalConfiguration.current
                    val rail = tabs.isNotEmpty() && showChrome && conf.screenWidthDp > conf.screenHeightDp && conf.screenHeightDp < 560
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                        if (showChrome) WonderToolbar(title, subtitle, onBack, appIcon = onBack == null) {
                            actions()
                            OverflowMenu(menu + listOf(WonderMenuItem("Settings", WonderIcons.Settings) { settingsOpen = true }))
                        }
                        if (showChrome) NeedsBanner(needs)
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            if (rail) WonderTabRail(tabs, selectedTab, tabClick)
                            Box(Modifier.weight(1f).fillMaxHeight().then(if (tabs.isEmpty() || !showChrome || rail) Modifier.navigationBarsPadding() else Modifier).imePadding()) {
                                // New screens and tabs ease in instead of snapping.
                                Box(Modifier.fillMaxSize().wonderEnter(motion, selectedTab)) { content() }
                                ToastHost(toast, Modifier.align(Alignment.BottomCenter))
                            }
                        }
                        if (tabs.isNotEmpty() && showChrome && !rail) WonderTabBar(tabs, selectedTab, tabClick)
                    }
                }
                WonderFxOverlay(fx)
                CrashPrompt(appName)
            }
        }
    }
}

@Composable
private fun ToastHost(state: ToastState, modifier: Modifier) {
    val p = wonderPalette()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state.serial) {
        if (state.message != null) { visible = true; delay(2800); visible = false }
    }
    AnimatedVisibility(visible, modifier.padding(16.dp), enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
        val bg = if (p.dark) Color(0xF0E9ECF0) else Color(0xEE2A2D33)
        Text(
            state.message ?: "", color = if (p.dark) Color(0xFF15181C) else Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.shadow(8.dp, RoundedCornerShape(22.dp)).background(bg, RoundedCornerShape(22.dp)).padding(horizontal = 18.dp, vertical = 11.dp)
        )
    }
}

@Composable
fun WonderToolbar(title: String, subtitle: String?, onBack: (() -> Unit)?, appIcon: Boolean, actions: @Composable RowScope.() -> Unit) {
    val p = wonderPalette()
    val context = LocalContext.current
    val icon = remember { context.appArt(96) }
    Column(
        Modifier.fillMaxWidth().drawBehind {
            drawRect(Brush.verticalGradient(p.toolbar))
            drawLine(Color.White.copy(alpha = if (p.dark) .10f else .6f), Offset(0f, 0f), Offset(size.width, 0f), 1f)
            drawLine(p.toolbarLine, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 2f)
        }.statusBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) ToolbarButton(WonderIcons.ArrowBack, "Back", onBack)
            else Spacer(Modifier.width(8.dp))
            if (appIcon && icon != null) {
                Image(icon, null, Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = p.toolbarText, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, color = p.toolbarText.copy(alpha = .72f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CompositionLocalProvider(LocalContentColor provides p.toolbarIcon) { actions() }
        }
    }
}

@Composable
fun ToolbarButton(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true, tint: Color? = null) {
    val p = wonderPalette()
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, description, tint = (tint ?: p.toolbarIcon).copy(alpha = if (enabled) 1f else .35f))
    }
}

@Composable
fun OverflowMenu(items: List<WonderMenuItem>) {
    var open by remember { mutableStateOf(false) }
    val p = wonderPalette()
    Box {
        ToolbarButton(WonderIcons.MoreVert, "More options", { open = true })
        DropdownMenu(open, { open = false }, modifier = Modifier.background(p.panel)) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, color = if (item.danger) p.danger else p.text) },
                    leadingIcon = item.icon?.let { { Icon(it, null, tint = if (item.danger) p.danger else p.secondaryText) } },
                    onClick = { open = false; item.onClick() }
                )
            }
        }
    }
}

@Composable
fun WonderTabBar(tabs: List<WonderTab>, selected: String, onTab: (String) -> Unit) {
    val p = wonderPalette()
    val view = LocalView.current
    Row(
        Modifier.fillMaxWidth().drawBehind {
            drawRect(Brush.verticalGradient(p.tabBar))
            drawLine(p.toolbarLine.copy(alpha = .8f), Offset(0f, 0f), Offset(size.width, 0f), 2f)
        }.navigationBarsPadding().height(60.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val on = tab.key == selected
            val color = if (on) p.tabSelected else p.tabIdle
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(vertical = 5.dp, horizontal = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (on) Modifier.background(p.tabSelected.copy(alpha = if (p.dark) .16f else .11f)) else Modifier)
                    .clickable(role = Role.Tab) { if (tab.key != selected) view.tick(); onTab(tab.key) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Icon(tab.icon, null, tint = color, modifier = Modifier.size(24.dp))
                Text(tab.label, color = color, style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1)
            }
        }
    }
}

/**
 * A short, soft entrance (fade plus a small rise) that plays whenever [key] changes: a new screen,
 * a new tab. Instant when motion is off, gentler when reduced.
 */
@Composable
fun Modifier.wonderEnter(motion: WonderMotion, key: Any): Modifier {
    if (motion == WonderMotion.OFF) return this
    val p = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(key) { p.snapTo(0f); p.animateTo(1f, androidx.compose.animation.core.tween(if (motion == WonderMotion.FULL) 220 else 140, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    val rise = with(LocalDensity.current) { (if (motion == WonderMotion.FULL) 14.dp else 6.dp).toPx() }
    return this.then(Modifier.graphicsLayer { alpha = .35f + .65f * p.value; translationY = (1f - p.value) * rise })
}

/** [WonderTabBar] standing on its side, for landscape. */
@Composable
fun WonderTabRail(tabs: List<WonderTab>, selected: String, onTab: (String) -> Unit) {
    val p = wonderPalette()
    val view = LocalView.current
    Column(
        Modifier.fillMaxHeight().width(84.dp).drawBehind {
            drawRect(Brush.horizontalGradient(p.tabBar))
            drawLine(p.toolbarLine.copy(alpha = .8f), Offset(size.width - 1f, 0f), Offset(size.width - 1f, size.height), 2f)
        }.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(vertical = 6.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        tabs.forEach { tab ->
            val on = tab.key == selected
            val color = if (on) p.tabSelected else p.tabIdle
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .then(if (on) Modifier.background(p.tabSelected.copy(alpha = if (p.dark) .16f else .11f)) else Modifier)
                    .clickable(role = Role.Tab) { if (tab.key != selected) view.tick(); onTab(tab.key) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(tab.icon, null, tint = color, modifier = Modifier.size(24.dp))
                Text(tab.label, color = color, style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * After an app crashed, the next time it opens it says sorry and offers to send the report (once per crash).
 * Skipped when sending is already automatic, or when Control Room isn't there to keep the report.
 */
@Composable
private fun CrashPrompt(appName: String) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wondercrash", Context.MODE_PRIVATE) }
    var pending by remember { mutableStateOf(prefs.getString("prompt", null)) }
    var auto by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        auto = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { WonderCrash.status(context) }
        if (auto != false) { prefs.edit().remove("prompt").apply(); pending = null }
    }
    if (pending != null && auto == false) {
        fun done() { prefs.edit().remove("prompt").apply(); pending = null }
        WonderDialog(onDismissRequest = { done() }, title = { Text("$appName closed unexpectedly") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sorry about that. Sending the report helps get it fixed.", style = MaterialTheme.typography.bodyMedium)
                Text("Only the error, the app version, your phone model and Android version are sent. File names, links and email addresses are removed. You can change this any time in Control Room › Wonderdesk.",
                    style = MaterialTheme.typography.bodySmall, color = wonderPalette().secondaryText)
            }
        }, confirmButton = {
            TextButton(onClick = { done(); scope.launch(kotlinx.coroutines.Dispatchers.IO) { WonderCrash.call(context, "auto", "on") } }) { Text("Always send") }
        }, dismissButton = {
            Row {
                TextButton(onClick = { done() }) { Text("Not now") }
                TextButton(onClick = { done(); scope.launch(kotlinx.coroutines.Dispatchers.IO) { WonderCrash.call(context, "send", context.packageName) } }) { Text("Send") }
            }
        })
    }
}

/* -------------------------------------- settings page ------------------------------------- */

@Composable
private fun SettingsPage(appName: String, about: String, extra: (@Composable ColumnScope.() -> Unit)?, needs: List<WonderNeed> = emptyList()) {
    val context = LocalContext.current
    val p = wonderPalette()
    val setLook = LocalLookPreference.current
    val look = LocalLook.current
    val setMotion = LocalMotionPreference.current
    val motion = LocalMotion.current
    var licenses by remember { mutableStateOf(false) }
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?" }
    val icon = remember { context.appArt(192) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        extra?.invoke(this)
        NeedsSettings(needs)
        val follows = LocalFollowsSuite.current
        val setFollow = LocalFollowSuitePreference.current
        val suiteInstalled = remember { GlobalAppearance.read(context) != null }
        Panel(title = "Appearance", footer = if (suiteInstalled) "Control Room sets one look for every Wonderdesk app. Pick a look below to give just this app its own." else "Install Control Room to set one look for every Wonderdesk app.") {
            if (suiteInstalled) {
                SwitchRow("Follow Control Room", follows, setFollow, subtitle = if (follows) "Using the suite-wide look" else "This app has its own look", icon = WonderIcons.Palette, tint = Color(0xFF8E44AD))
                PanelDivider(0.dp)
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WonderLook.entries.forEach { option ->
                    val pal = paletteFor(option)
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { setLook(option) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(.8f).clip(RoundedCornerShape(8.dp)).background(pal.window)
                                .border(if (option == look) 3.dp else 1.dp, if (option == look) p.accent else p.panelBorder, RoundedCornerShape(8.dp))
                        ) {
                            Box(Modifier.fillMaxWidth().height(14.dp).background(Brush.verticalGradient(pal.toolbar)))
                            Column(Modifier.padding(top = 20.dp, start = 6.dp, end = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.fillMaxWidth().height(10.dp).background(pal.panel, RoundedCornerShape(3.dp)))
                                Box(Modifier.fillMaxWidth(.6f).height(10.dp).background(pal.accent, RoundedCornerShape(5.dp)))
                                Box(Modifier.fillMaxWidth().height(10.dp).background(pal.panel, RoundedCornerShape(3.dp)))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(option.label, style = MaterialTheme.typography.labelMedium, color = if (option == look) p.accent else p.text, textAlign = TextAlign.Center)
                    }
                }
            }
            PanelDivider(0.dp)
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Motion", style = MaterialTheme.typography.titleSmall)
                Segmented(WonderMotion.entries.map { it.label }, WonderMotion.entries.indexOf(motion)) { setMotion(WonderMotion.entries[it]) }
                Text("Reduced and Off turn off the animated background and tap effects. Android's own animation setting is always respected.",
                    style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
            }
        }
        Panel(title = "About") {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) Image(icon, null, Modifier.size(64.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(appName, style = MaterialTheme.typography.titleLarge)
                    Text("Version $version", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
                    Text("Part of the Wonderdesk suite", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
                }
            }
            PanelDivider(0.dp)
            Text(about, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            PanelDivider(0.dp)
            ListRow(
                title = "Get updates with Wonderdesk Apphub", subtitle = "Installs and updates every Wonderdesk app",
                icon = WonderIcons.SystemUpdate, tint = Color(0xFF2E9E44),
                onClick = {
                    val launch = context.packageManager.getLaunchIntentForPackage("dev.wonderdesk.apphub")
                    if (launch != null) context.launchSafely(launch) {} else context.launchSafely(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/thoroftroy/App-Hub/releases"))) {}
                }
            )
            PanelDivider()
            ListRow(title = "Open-source licenses", icon = WonderIcons.Description, tint = Color(0xFF7A7F87), onClick = { licenses = true })
        }
        Text("Wonderdesk keeps your data on this device. No accounts, ads or tracking.", Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall, color = p.secondaryText, textAlign = TextAlign.Center)
    }
    if (licenses) WonderDialog(onDismissRequest = { licenses = false }, title = { Text("Open-source licenses") }, text = {
        val text = remember { listOf("NOTICE.txt", "APACHE-2.0.txt").joinToString("\n\n") { name -> runCatching { context.assets.open("licenses/$name").bufferedReader().use { it.readText() } }.getOrDefault("") } }
        Text(text, Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
    }, confirmButton = { TextButton(onClick = { licenses = false }) { Text("Close") } })
}

/* ---------------------------------------- panels ------------------------------------------ */

/** A grouped white panel, like a Snow Leopard preference group. */
@Composable
fun Panel(modifier: Modifier = Modifier, title: String? = null, footer: String? = null, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = wonderPalette()
    Column(modifier.fillMaxWidth()) {
        if (title != null || trailing != null) Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(title ?: "", Modifier.weight(1f))
            trailing?.invoke()
        }
        val shape = RoundedCornerShape(p.panelRadius)
        Column(
            Modifier.fillMaxWidth().shadow(if (p.dark) 0.dp else 1.dp, shape).clip(shape).background(p.panel).border(1.dp, p.panelBorder, shape),
            content = content
        )
        if (footer != null) Text(footer, Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp), style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val p = wonderPalette()
    Text(
        if (p.look == WonderLook.AQUA || p.look == WonderLook.DARK) text.uppercase() else text, modifier,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = if (p.look == WonderLook.AQUA || p.look == WonderLook.DARK) .8.sp else 0.sp),
        color = if (p.look == WonderLook.BLISS) Color(0xFF215DC6) else p.secondaryText
    )
}

@Composable
fun PanelDivider(inset: Dp = 56.dp) {
    val p = wonderPalette()
    Box(Modifier.padding(start = inset).fillMaxWidth().height(1.dp).background(p.separator))
}

/** Background for item [index] of [count] inside a LazyColumn so the items read as one panel. */
@Composable
fun Modifier.panelItem(index: Int, count: Int): Modifier {
    val p = wonderPalette()
    val r = p.panelRadius
    val shape = RoundedCornerShape(
        topStart = if (index == 0) r else 0.dp, topEnd = if (index == 0) r else 0.dp,
        bottomStart = if (index == count - 1) r else 0.dp, bottomEnd = if (index == count - 1) r else 0.dp
    )
    return this.fillMaxWidth().clip(shape).background(p.panel).drawBehind {
        val w = 1.dp.toPx()
        val c = p.panelBorder
        val rr = r.toPx()
        // side borders
        drawLine(c, Offset(w / 2, if (index == 0) rr else 0f), Offset(w / 2, size.height - if (index == count - 1) rr else 0f), w)
        drawLine(c, Offset(size.width - w / 2, if (index == 0) rr else 0f), Offset(size.width - w / 2, size.height - if (index == count - 1) rr else 0f), w)
        if (index == 0) {
            drawLine(c, Offset(rr, w / 2), Offset(size.width - rr, w / 2), w)
            drawArc(c, 180f, 90f, false, Offset(w / 2, w / 2), Size(rr * 2, rr * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
            drawArc(c, 270f, 90f, false, Offset(size.width - rr * 2 - w / 2, w / 2), Size(rr * 2, rr * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
        } else drawLine(p.separator, Offset(56.dp.toPx(), 0f), Offset(size.width, 0f), w)
        if (index == count - 1) {
            drawLine(c, Offset(rr, size.height - w / 2), Offset(size.width - rr, size.height - w / 2), w)
            drawArc(c, 90f, 90f, false, Offset(w / 2, size.height - rr * 2 - w / 2), Size(rr * 2, rr * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
            drawArc(c, 0f, 90f, false, Offset(size.width - rr * 2 - w / 2, size.height - rr * 2 - w / 2), Size(rr * 2, rr * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
        }
    }
}

/** Glossy rounded-square glyph tile, like Snow Leopard's preference icons. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 32.dp) {
    val shape = RoundedCornerShape(size * .24f)
    Box(
        Modifier.size(size).clip(shape).background(Brush.verticalGradient(listOf(tint.lighten(.28f), tint, tint.darken(.18f))))
            .drawBehind {
                drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .45f), Color.White.copy(alpha = .05f)), endY = size.toPx() * .5f),
                    size = Size(this.size.width, this.size.height * .5f), cornerRadius = CornerRadius(size.toPx() * .24f))
            },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * .62f)) }
}

fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)
fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)

/** Standard list row. Put inside a [Panel] or give it [Modifier.panelItem] in a LazyColumn. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tint: Color = Color(0xFF1E7FE8),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    selected: Boolean = false,
    danger: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val p = wonderPalette()
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth()
            .then(if (selected) Modifier.background(p.selection.copy(alpha = .16f)) else Modifier)
            .then(if (onClick != null || onLongClick != null) Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick) else Modifier)
            .heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            leading != null -> { Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { leading() }; Spacer(Modifier.width(12.dp)) }
            icon != null -> { IconTile(icon, if (danger) p.danger else tint); Spacer(Modifier.width(12.dp)) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (danger) p.danger else p.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.secondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
        if (chevron) Icon(WonderIcons.ChevronRight, null, tint = p.secondaryText.copy(alpha = .7f))
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null, icon: ImageVector? = null, tint: Color = Color(0xFF1E7FE8)) {
    val p = wonderPalette()
    val view = LocalView.current
    ListRow(title, subtitle = subtitle, icon = icon, tint = tint, onClick = { view.tick(); onChange(!checked) }, trailing = {
        Switch(checked, { view.tick(); onChange(it) }, colors = SwitchDefaults.colors(checkedTrackColor = if (p.look == WonderLook.AQUA) Color(0xFF34C759) else p.accent, checkedThumbColor = Color.White))
    })
}

/* ---------------------------------------- controls ---------------------------------------- */

enum class ButtonKind { Primary, Secondary, Danger, Plain }

@Composable
fun WButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, kind: ButtonKind = ButtonKind.Secondary, enabled: Boolean = true) {
    val view = LocalView.current
    val onClick: () -> Unit = { view.tick(); onClick() }
    val p = wonderPalette()
    val inner: @Composable RowScope.() -> Unit = {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
    }
    val pad = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    when (kind) {
        ButtonKind.Primary -> AquaButton(onClick, modifier, enabled, pad, inner)
        ButtonKind.Secondary -> AquaSecondaryButton(onClick, modifier, enabled, pad, inner)
        ButtonKind.Danger -> Button(onClick, modifier.heightIn(min = 48.dp), enabled, shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = p.danger, contentColor = Color.White), contentPadding = pad, content = inner)
        ButtonKind.Plain -> TextButton(onClick, modifier, enabled, colors = ButtonDefaults.textButtonColors(contentColor = p.accent), content = inner)
    }
}

/** Aqua segmented control. */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val p = wonderPalette()
    val shape = RoundedCornerShape(if (p.look == WonderLook.BLISS) 3.dp else 9.dp)
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().height(38.dp).clip(shape)
            .background(Brush.verticalGradient(if (p.dark) listOf(Color(0xFF3A3F47), Color(0xFF2B2F35)) else listOf(Color.White, Color(0xFFE6E8EB))))
            .border(1.dp, p.fieldBorder, shape)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .then(if (on) Modifier.background(Brush.verticalGradient(listOf(p.accent.lighten(.25f), p.accent, p.accent.darken(.12f)))) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) p.onAccent else p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (i < options.lastIndex) Box(Modifier.width(1.dp).fillMaxHeight().background(p.fieldBorder.copy(alpha = .7f)))
        }
    }
}

/** Snow Leopard pill search field. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onSubmit: (() -> Unit)? = null) {
    val p = wonderPalette()
    val shape = RoundedCornerShape(50)
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().height(44.dp).clip(shape).background(p.field).border(1.dp, p.fieldBorder, shape).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(WonderIcons.Search, null, tint = p.secondaryText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = p.secondaryText, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            BasicTextField(value, onChange, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.text),
                cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }))
        }
        if (value.isNotEmpty()) Box(Modifier.size(22.dp).clip(CircleShape).background(p.secondaryText.copy(alpha = .55f)).clickable { onChange("") }, contentAlignment = Alignment.Center) {
            Icon(WonderIcons.Close, "Clear", tint = p.field, modifier = Modifier.size(14.dp))
        }
    }
}

/** Text field styled for the current look. */
@Composable
fun Field(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    singleLine: Boolean = true, keyboard: KeyboardType = KeyboardType.Text, imeAction: ImeAction = ImeAction.Done,
    onDone: (() -> Unit)? = null, isError: Boolean = false, supporting: String? = null,
    visual: VisualTransformation = VisualTransformation.None, trailing: (@Composable () -> Unit)? = null,
    minLines: Int = 1, textStyle: TextStyle? = null,
) {
    val p = wonderPalette()
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, isError = isError, minLines = minLines,
        supportingText = supporting?.let { { Text(it) } }, visualTransformation = visual, trailingIcon = trailing,
        textStyle = textStyle ?: MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }, onGo = { onDone?.invoke() }, onSearch = { onDone?.invoke() }),
        shape = RoundedCornerShape(if (p.look == WonderLook.BLISS) 3.dp else 10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = p.field, unfocusedContainerColor = p.field, focusedBorderColor = p.accent, unfocusedBorderColor = p.fieldBorder,
            focusedTextColor = p.text, unfocusedTextColor = p.text, focusedLabelColor = p.accent, unfocusedLabelColor = p.secondaryText, cursorColor = p.accent
        )
    )
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val p = wonderPalette()
    val view = LocalView.current
    val shape = RoundedCornerShape(50)
    androidx.compose.foundation.layout.Row(
        Modifier.height(34.dp).clip(shape)
            .background(if (selected) p.accent else p.panel)
            .border(1.dp, if (selected) p.accent else p.panelBorder, shape)
            .clickable { view.tick(); onClick() }.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = if (selected) p.onAccent else p.secondaryText); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) p.onAccent else p.text, maxLines = 1)
    }
}

/** A friendly empty or first-run state with the app's illustration. */
@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, icon: ImageVector? = null, art: Boolean = icon == null, action: (@Composable () -> Unit)? = null) {
    val p = wonderPalette()
    val context = LocalContext.current
    val appArt = remember { if (art) context.appArt(288) else null }
    // Scrolls when there isn't room (landscape, big fonts), so nothing ever overlaps.
    val short = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 560
    // Inside a list it just takes its height; on its own it scrolls when there isn't room, so nothing overlaps.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val scroll = if (constraints.hasBoundedHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(Modifier.fillMaxWidth().then(scroll).padding(horizontal = 32.dp, vertical = if (short) 16.dp else 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                appArt != null -> Image(appArt, null, Modifier.size(if (short) 72.dp else 112.dp))
                icon != null -> Icon(icon, null, Modifier.size(72.dp), tint = p.secondaryText.copy(alpha = .55f))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = p.secondaryText, textAlign = TextAlign.Center)
            if (action != null) { Spacer(Modifier.height(6.dp)); action() }
        }
    }
}

/** Big number with a caption, for dashboards. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = Color(0xFF1E7FE8), detail: String? = null, progress: Float? = null) {
    val p = wonderPalette()
    val shape = RoundedCornerShape(p.panelRadius)
    Column(modifier.clip(shape).background(p.panel).border(1.dp, p.panelBorder, shape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { IconTile(icon, tint, 24.dp); Spacer(Modifier.width(8.dp)) }
            Text(label, style = MaterialTheme.typography.labelLarge, color = p.secondaryText, maxLines = 1)
        }
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = p.secondaryText, maxLines = 2)
        if (progress != null) AquaProgress(progress, Modifier.fillMaxWidth().padding(top = 4.dp), tint)
    }
}

/** Glossy capsule progress bar. Pass null for indeterminate. */
@Composable
fun AquaProgress(value: Float?, modifier: Modifier = Modifier, tint: Color? = null) {
    val p = wonderPalette()
    val c = tint ?: p.accent
    if (value == null) {
        LinearProgressIndicator(modifier.height(8.dp).clip(RoundedCornerShape(50)), color = c, trackColor = p.separator)
        return
    }
    Box(modifier.height(10.dp).clip(RoundedCornerShape(50)).background(p.separator).border(1.dp, p.panelBorder.copy(alpha = .6f), RoundedCornerShape(50))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f, 1f)).clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(c.lighten(.45f), c, c.darken(.1f)))))
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false) {
    val p = wonderPalette()
    WonderDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = if (danger) p.danger else p.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = p.text) } })
}

@Composable
fun InputDialog(title: String, label: String, initial: String = "", confirm: String = "OK", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    WonderDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Field(value, { value = it }, label, onDone = { if (value.isNotBlank()) { onDismiss(); onConfirm(value.trim()) } })
    }, confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onDismiss(); onConfirm(value.trim()) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

/** Scrolling page body with standard padding and spacing. */
@Composable
fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0; var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (v >= 100) "%.0f %s".format(v, units[i]) else "%.1f %s".format(v, units[i])
}

fun Context.prefs(name: String) = getSharedPreferences(name, Context.MODE_PRIVATE)

/** The app's full illustration (not the masked launcher icon), for toolbars and empty states. */
fun Context.appArt(px: Int): ImageBitmap? = suiteArt(packageName, px)

/**
 * A Wonderdesk app's full artwork, cropped to its visible edges so it fills the space it's given.
 * Falls back to the app's normal icon (and works for any installed app).
 */
fun Context.suiteArt(pkg: String, px: Int): ImageBitmap? {
    val key = "$pkg@$px"
    ArtCache.map.get(key)?.let { return it }
    return suiteArtNow(pkg, px)?.also { ArtCache.map.put(key, it) }
}

/** Decoded artwork and icons, so screens and lists never decode the same picture twice. */
object ArtCache { val map = android.util.LruCache<String, ImageBitmap>(96) }

/** [suiteArt] without blocking: null for the first frame, then the picture (decoded off the main thread). */
@Composable
fun rememberSuiteArt(pkg: String, px: Int): ImageBitmap? {
    val c = LocalContext.current
    return produceState(ArtCache.map.get("$pkg@$px"), pkg, px) {
        if (value == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.suiteArt(pkg, px) }
    }.value
}

/** Any installed app's normal icon, decoded off the main thread and cached. */
@Composable
fun rememberAppIcon(pkg: String, px: Int): ImageBitmap? {
    val c = LocalContext.current
    val key = "icon:$pkg@$px"
    return produceState(ArtCache.map.get(key), pkg, px) {
        if (value == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { c.packageManager.getApplicationIcon(pkg).toBitmap(px, px).asImageBitmap() }.getOrNull()?.also { ArtCache.map.put(key, it) }
        }
    }.value
}

private fun Context.suiteArtNow(pkg: String, px: Int): ImageBitmap? = runCatching {
    val res = if (pkg == packageName) resources else packageManager.getResourcesForApplication(pkg)
    val id = listOf("wonderdesk_${pkg.substringAfterLast('.')}_icon", "wonderdesk_icon").map { res.getIdentifier(it, "drawable", pkg) }.firstOrNull { it != 0 }
    if (id != null) {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeResource(res, id, opts)
        opts.inSampleSize = maxOf(1, minOf(opts.outWidth, opts.outHeight) / (px * 2)); opts.inJustDecodeBounds = false
        val raw = android.graphics.BitmapFactory.decodeResource(res, id, opts)
        fitSquare(trimTransparent(raw), px).asImageBitmap()
    } else packageManager.getApplicationIcon(pkg).toBitmap(px, px).asImageBitmap()
}.getOrNull()

/** Crops fully transparent (or nearly) margins. */
fun trimTransparent(b: android.graphics.Bitmap, threshold: Int = 40): android.graphics.Bitmap {
    val w = b.width; val h = b.height
    val row = IntArray(w)
    var top = 0; var bottom = h - 1; var left = w; var right = -1
    fun opaqueRow(y: Int): Boolean { b.getPixels(row, 0, w, 0, y, w, 1); return row.any { (it ushr 24) > threshold } }
    while (top < h && !opaqueRow(top)) top++
    while (bottom > top && !opaqueRow(bottom)) bottom--
    if (top >= bottom) return b
    for (y in top..bottom step 2) { b.getPixels(row, 0, w, 0, y, w, 1); for (x in 0 until w) if ((row[x] ushr 24) > threshold) { if (x < left) left = x; if (x > right) right = x } }
    if (right <= left) return b
    return android.graphics.Bitmap.createBitmap(b, left, top, right - left + 1, bottom - top + 1)
}

/** Centres [b] in a transparent square of [px], as large as fits. */
fun fitSquare(b: android.graphics.Bitmap, px: Int): android.graphics.Bitmap {
    val out = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
    val s = minOf(px.toFloat() / b.width, px.toFloat() / b.height); val w = b.width * s; val h = b.height * s
    android.graphics.Canvas(out).drawBitmap(b, null, android.graphics.RectF((px - w) / 2, (px - h) / 2, (px + w) / 2, (px + h) / 2), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG))
    return out
}

/** "Just now", "5 minutes ago", "Yesterday", or a date. */
fun relativeTime(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    if (diff in -120_000..59_000) return "Just now"
    return android.text.format.DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
}

/* ------------------------------------- suite-wide appearance ------------------------------------ */

/** Reads the look and motion chosen in Control Room. Null when Control Room isn't installed. */
object GlobalAppearance {
    val URI: android.net.Uri = android.net.Uri.parse("content://dev.wonderdesk.controlroom.appearance/current")
    @Volatile var cached: Pair<WonderLook, WonderMotion>? = null
    fun read(context: Context): Pair<WonderLook, WonderMotion>? = readNow(context).also { cached = it }
    private fun readNow(context: Context): Pair<WonderLook, WonderMotion>? = runCatching {
        context.contentResolver.query(URI, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val look = runCatching { WonderLook.valueOf(c.getString(c.getColumnIndexOrThrow("look"))) }.getOrDefault(WonderLook.AQUA)
            val motion = runCatching { WonderMotion.valueOf(c.getString(c.getColumnIndexOrThrow("motion"))) }.getOrDefault(WonderMotion.FULL)
            look to motion
        }
    }.getOrNull()
}

/** True while this app follows Control Room's appearance (the default). */
val LocalFollowsSuite = staticCompositionLocalOf { true }
val LocalFollowSuitePreference = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

/**
 * Theme used by [WonderApp]. Follows Control Room unless the user picked a look for this app.
 */
@Composable
fun WonderKitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val prefs = remember { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    var follow by remember { mutableStateOf(prefs.getBoolean("follow_suite", true)) }
    var ownLook by remember { mutableStateOf(runCatching { WonderLook.valueOf(prefs.getString("look", "AQUA") ?: "AQUA") }.getOrDefault(WonderLook.AQUA)) }
    var ownMotion by remember { mutableStateOf(runCatching { WonderMotion.valueOf(prefs.getString("motion", "FULL") ?: "FULL") }.getOrDefault(WonderMotion.FULL)) }
    var global by remember { mutableStateOf(GlobalAppearance.cached ?: GlobalAppearance.read(context)) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(owner) {
        // Re-check Control Room off the main thread when the app comes back, so resuming never stutters.
        val o = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) scope.launch {
                val g = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { GlobalAppearance.read(context) }
                if (g != global) global = g
            }
        }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    val look = if (follow && global != null) global!!.first else ownLook
    val motion = if (follow && global != null) global!!.second else ownMotion
    SideEffect {
        (context as? Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !paletteFor(look).dark && look != WonderLook.BLISS
                isAppearanceLightNavigationBars = !paletteFor(look).dark
            }
        }
    }
    val p = paletteFor(look)
    val colors = if (p.dark) darkColorScheme(primary = p.accent, onPrimary = p.onAccent, secondary = p.accent, background = p.window, surface = p.panel, onSurface = p.text, onBackground = p.text, outline = p.panelBorder, surfaceVariant = p.window, onSurfaceVariant = p.secondaryText, error = p.danger)
                 else lightColorScheme(primary = p.accent, onPrimary = p.onAccent, secondary = p.accent, background = p.window, surface = p.panel, onSurface = p.text, onBackground = p.text, outline = p.panelBorder, surfaceVariant = p.window, onSurfaceVariant = p.secondaryText, error = p.danger)
    CompositionLocalProvider(
        LocalLook provides look, LocalMotion provides motion, LocalIsDark provides p.dark,
        LocalLookPreference provides { l -> ownLook = l; follow = false; prefs.edit().putString("look", l.name).putBoolean("follow_suite", false).apply() },
        LocalMotionPreference provides { m -> ownMotion = m; follow = false; prefs.edit().putString("motion", m.name).putBoolean("follow_suite", false).apply() },
        LocalFollowsSuite provides follow,
        LocalFollowSuitePreference provides { f -> follow = f; prefs.edit().putBoolean("follow_suite", f).apply(); global = GlobalAppearance.read(context) },
    ) {
        MaterialTheme(colorScheme = colors, typography = wonderTypography(look)) {
            CompositionLocalProvider(LocalContentColor provides p.text, content = content)
        }
    }
}

/* ------------------------------------- battery-friendly clocks ------------------------------------ */

/** True while the app is on screen (resumed). Use it to pause anything that animates or polls. */
@Composable
fun rememberIsResumed(): Boolean {
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    return resumed
}

/**
 * The current time, refreshed every [periodMs] on the period boundary (so a minute clock flips
 * exactly on the minute) and only while the app is visible. Nothing runs in the background.
 */
@Composable
fun rememberNow(periodMs: Long, enabled: Boolean = true): Long {
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var started by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, _ -> started = owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs, enabled, started) {
        if (!enabled || !started) return@LaunchedEffect
        while (true) {
            val t = System.currentTimeMillis(); now.longValue = t
            delay((periodMs - t % periodMs).coerceAtLeast(16L))
        }
    }
    return now.longValue
}

/* ---------------------------------------- special access ---------------------------------------- */

/**
 * Android hides some powerful switches (usage access, accessibility, notification access, all-files)
 * behind settings screens, and for apps installed outside an app store it may also block them as a
 * "restricted setting" until the user allows it from App info. [SpecialAccess] opens the exact
 * screen for this app, and [AccessGuide] walks the user through both steps.
 */
object SpecialAccess {
    enum class Kind(val title: String, val settingName: String) {
        USAGE("Usage access", "Usage access"),
        ACCESSIBILITY("Accessibility", "Accessibility"),
        NOTIFICATIONS("Notification access", "Notification access"),
        ALL_FILES("All files access", "All files access"),
        EXACT_ALARMS("Alarms & reminders", "Alarms & reminders"),
        FULL_SCREEN("Full-screen alerts", "Full-screen notifications"),
        AUTOFILL("Autofill service", "Autofill service"),
        WRITE_SETTINGS("Modify system settings", "Modify system settings"),
        DND("Do Not Disturb access", "Do Not Disturb access"),
        INSTALL("Install unknown apps", "Install unknown apps"),
    }

    /** These may be blocked as "restricted settings" for sideloaded apps on Android 13 and newer. */
    fun canBeRestricted(kind: Kind) = android.os.Build.VERSION.SDK_INT >= 33 && kind in setOf(Kind.ACCESSIBILITY, Kind.NOTIFICATIONS, Kind.USAGE)

    /**
     * True only when Android has clearly let this app use restricted settings. Android 15 and newer no longer
     * report a block here (the app op stays "default" while the switch is still greyed out), so anything other
     * than an explicit "allowed" is treated as unknown and the guide keeps showing the unlock steps.
     */
    fun restrictedAllowed(c: Context): Boolean? = if (android.os.Build.VERSION.SDK_INT < 33) true else runCatching {
        val ops = c.getSystemService(android.app.AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow("android:access_restricted_settings", android.os.Process.myUid(), c.packageName)
        if (mode == android.app.AppOpsManager.MODE_ALLOWED) true else if (mode == android.app.AppOpsManager.MODE_ERRORED || mode == android.app.AppOpsManager.MODE_IGNORED) false else null
    }.getOrNull()

    /** The name Android shows for [component] in its settings lists (an accessibility or listener service), or the app's name. */
    fun switchName(c: Context, component: Class<*>?): String = component?.let { runCatching {
        c.packageManager.getServiceInfo(android.content.ComponentName(c, it), 0).loadLabel(c.packageManager).toString()
    }.getOrNull() } ?: appLabel(c)

    fun granted(c: Context, kind: Kind, component: Class<*>? = null): Boolean = runCatching {
        when (kind) {
            Kind.USAGE -> {
                val ops = c.getSystemService(android.app.AppOpsManager::class.java)
                ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.packageName) == android.app.AppOpsManager.MODE_ALLOWED
            }
            Kind.ACCESSIBILITY -> {
                val on = android.provider.Settings.Secure.getString(c.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                on.split(':').any { it.startsWith(c.packageName + "/") && (component == null || it.endsWith(component.name) || it.endsWith(component.simpleName)) }
            }
            Kind.NOTIFICATIONS -> androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)
            Kind.ALL_FILES -> android.os.Environment.isExternalStorageManager()
            Kind.EXACT_ALARMS -> android.os.Build.VERSION.SDK_INT < 31 || c.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()
            Kind.FULL_SCREEN -> android.os.Build.VERSION.SDK_INT < 34 || c.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()
            Kind.AUTOFILL -> c.getSystemService(android.view.autofill.AutofillManager::class.java)?.hasEnabledAutofillServices() == true
            Kind.WRITE_SETTINGS -> android.provider.Settings.System.canWrite(c)
            Kind.DND -> c.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted
            Kind.INSTALL -> c.packageManager.canRequestPackageInstalls()
        }
    }.getOrDefault(false)

    /** Opens the most specific screen Android offers for this app and [kind]. */
    fun open(c: Context, kind: Kind, component: Class<*>? = null) {
        val pkg = android.net.Uri.parse("package:" + c.packageName)
        val S = android.provider.Settings::class.java
        val tries: List<android.content.Intent> = when (kind) {
            Kind.USAGE -> listOf(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS, pkg), android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
            Kind.ACCESSIBILITY -> listOfNotNull(
                if (android.os.Build.VERSION.SDK_INT >= 33 && component != null) android.content.Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                    .putExtra(android.content.Intent.EXTRA_COMPONENT_NAME, android.content.ComponentName(c, component).flattenToString()) else null,
                android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Kind.NOTIFICATIONS -> listOfNotNull(
                if (android.os.Build.VERSION.SDK_INT >= 30 && component != null) android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, android.content.ComponentName(c, component).flattenToString()) else null,
                android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            Kind.ALL_FILES -> listOf(android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg), android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            Kind.EXACT_ALARMS -> listOf(android.content.Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM", pkg))
            Kind.FULL_SCREEN -> listOf(android.content.Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", pkg))
            Kind.AUTOFILL -> listOf(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, pkg))
            Kind.WRITE_SETTINGS -> listOf(android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg))
            Kind.DND -> listOf(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            Kind.INSTALL -> listOf(android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkg))
        } + android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        for (i in tries) {
            if (runCatching { c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }

    fun openAppInfo(c: Context) = runCatching {
        c.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + c.packageName)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Step-by-step sheet for turning on a special permission. Checks again every time the user comes
 * back to the app and calls [onGranted] (then closes) as soon as it's on.
 *
 * For switches Android can block as a "restricted setting" (apps installed outside an app store), it
 * always lays out the three steps: try the switch once so Android shows its warning, allow restricted
 * settings from App info, then switch it on. Android gives apps no reliable way to tell whether the
 * block applies, so the steps stay until the switch is actually on.
 */
@Composable
fun AccessGuide(kind: SpecialAccess.Kind, why: String, onDismiss: () -> Unit, component: Class<*>? = null, onGranted: () -> Unit = {}) {
    val context = LocalContext.current
    val p = wonderPalette()
    val resumed = rememberIsResumed()
    var granted by remember { mutableStateOf(SpecialAccess.granted(context, kind, component)) }
    var step by remember { mutableIntStateOf(0) }
    var allowed by remember { mutableStateOf(SpecialAccess.restrictedAllowed(context)) }
    val name = remember(component) { SpecialAccess.switchName(context, component) }
    LaunchedEffect(resumed) {
        if (resumed) {
            granted = SpecialAccess.granted(context, kind, component); allowed = SpecialAccess.restrictedAllowed(context)
            if (granted) { onGranted(); onDismiss() }
        }
    }
    val restricted = SpecialAccess.canBeRestricted(kind) && allowed != true
    @Composable fun Step(n: Int, done: Boolean, title: String, detail: String, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 2.dp).size(28.dp).clip(CircleShape).background(if (done) p.success else p.accent), contentAlignment = Alignment.Center) {
                if (done) Icon(WonderIcons.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                else Text("$n", color = p.onAccent, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
            }
            Icon(WonderIcons.ChevronRight, null, Modifier.padding(top = 2.dp), tint = p.secondaryText)
        }
    }
    WonderDialog(onDismissRequest = onDismiss, title = { Text(if (component != null) "Turn on “$name”" else "Turn on ${kind.title}") }, text = {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(why, style = MaterialTheme.typography.bodyMedium)
            if (!restricted) {
                Panel {
                    ListRow("Open ${kind.settingName}", subtitle = "Find “$name” and switch it on", icon = WonderIcons.Settings, tint = p.accent, chevron = true,
                        trailing = if (granted) ({ Icon(WonderIcons.CheckCircle, null, tint = p.success) }) else null,
                        onClick = { step = 1; SpecialAccess.open(context, kind, component) })
                }
                if (step > 0 && !granted) Text("Still off. Switch it on in Settings, then come back here.", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
            } else {
                Text("Android protects this switch for apps that didn't come from an app store, so it starts out greyed out. Unlocking it takes three steps, once:",
                    style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
                Panel {
                    Step(1, step >= 1, "Open ${kind.settingName} and tap “$name”", "It's greyed out or says “Restricted setting”. Tap OK on the message: Android only offers step 2 after you've seen it.") {
                        step = maxOf(step, 1); SpecialAccess.open(context, kind, component) }
                    PanelDivider(52.dp)
                    Step(2, allowed == true || step >= 2, "Allow restricted settings", "In App info, tap ⋮ at the top right, then “Allow restricted settings”, and confirm with your PIN or fingerprint.") {
                        step = maxOf(step, 2); SpecialAccess.openAppInfo(context) }
                    PanelDivider(52.dp)
                    Step(3, granted, "Switch on “$name”", "Back in ${kind.settingName}, the switch now works. Turn it on and come back here.") {
                        step = 3; SpecialAccess.open(context, kind, component) }
                }
                Text("No ⋮ menu or no “Allow restricted settings” in App info? Do step 1 first, then look again. If your phone has Advanced Protection turned on, Android doesn't allow this for apps from outside the Play Store.",
                    style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
            }
        }
    }, confirmButton = {
        val next = if (!restricted) 1 else (step + 1).coerceAtMost(3)
        TextButton(onClick = {
            step = next
            if (restricted && next == 2) SpecialAccess.openAppInfo(context) else SpecialAccess.open(context, kind, component)
        }) { Text(if (!restricted) (if (step > 0) "Open again" else "Open Settings") else when (next) { 1 -> "Step 1"; 2 -> "Step 2"; else -> "Step 3" }) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } })
}

fun appLabel(c: Context): String = runCatching { c.applicationInfo.loadLabel(c.packageManager).toString() }.getOrDefault("this app")

/* ------------------------------------------- widgets ------------------------------------------- */

/** One home-screen widget's own settings, kept by its widget id. */
class WidgetPrefs(c: Context, private val id: Int) {
    private val p = c.getSharedPreferences("widget-settings", Context.MODE_PRIVATE)
    fun str(name: String, def: String) = p.getString("$id.$name", def) ?: def
    fun bool(name: String, def: Boolean) = p.getBoolean("$id.$name", def)
    fun int(name: String, def: Int) = p.getInt("$id.$name", def)
    fun put(vararg pairs: Pair<String, Any>) = p.edit().apply { pairs.forEach { (k, v) -> when (v) { is Boolean -> putBoolean("$id.$k", v); is Int -> putInt("$id.$k", v); else -> putString("$id.$k", v.toString()) } } }.apply()
    fun clear() = p.edit().apply { p.all.keys.filter { it.startsWith("$id.") }.forEach { remove(it) } }.apply()
}

/** How a widget sits on the wallpaper. */
enum class WidgetLook(val label: String) { GLASS("Light glass"), DARK("Dark glass"), CLEAR("No background") }

/**
 * Applies [look] to a widget: background drawable plus text colours (light text on dark glass and on no background).
 * Each app supplies its three backgrounds and lists its main and secondary text views.
 */
fun android.widget.RemoteViews.applyLook(look: WidgetLook, root: Int, bgs: Triple<Int, Int, Int>, primary: List<Int>, secondary: List<Int>) {
    setInt(root, "setBackgroundResource", when (look) { WidgetLook.GLASS -> bgs.first; WidgetLook.DARK -> bgs.second; WidgetLook.CLEAR -> bgs.third })
    val light = look != WidgetLook.GLASS
    primary.forEach { setTextColor(it, if (light) 0xFFFFFFFF.toInt() else 0xFF1B1D21.toInt()) }
    secondary.forEach { setTextColor(it, if (light) 0xDDFFFFFF.toInt() else 0xFF6B7078.toInt()) }
    if (look == WidgetLook.CLEAR) (primary + secondary).forEach { runCatching { setFloat(it, "setShadowLayerRadius", 6f) } }
}

/**
 * Base for a widget's setup screen. Opens when the widget is added (and from the launcher's Edit), saves,
 * redraws the widget and closes. Backing out of the first setup cancels adding the widget.
 */
abstract class WidgetSetupActivity : WonderActivity() {
    protected var widgetId = android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
    override fun onCreate(savedInstanceState: Bundle?) {
        widgetId = intent?.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) ?: android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, android.content.Intent().putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        super.onCreate(savedInstanceState)
        if (widgetId == android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) finish()
    }
    /** Redraw the widget with its new settings. */
    abstract fun redraw()
    fun save() {
        Thread { runCatching { redraw() } }.start()
        setResult(RESULT_OK, android.content.Intent().putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)); finish()
    }
}

/** The standard widget setup screen: a title, the options, and Save. */
@Composable
fun WidgetSetup(appName: String, title: String, onCancel: () -> Unit, onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    WonderApp(appName = appName, about = title, title = title, onBack = onCancel,
        actions = { TextButton(onClick = onSave) { Text("Save", color = wonderPalette().toolbarText, fontWeight = FontWeight.SemiBold) } }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content()
            WButton("Save widget", onSave, Modifier.fillMaxWidth(), icon = WonderIcons.Check, kind = ButtonKind.Primary)
        }
    }
}

/** Background picker row used by every widget setup screen. */
@Composable
fun WidgetLookPicker(look: WidgetLook, onChange: (WidgetLook) -> Unit) {
    Panel(title = "Background") {
        WidgetLook.values().forEachIndexed { i, l ->
            if (i > 0) PanelDivider()
            ListRow(l.label, trailing = { RadioButton(look == l, { onChange(l) }) }, onClick = { onChange(l) })
        }
    }
}

/**
 * A widget that fits any size you give it: Android shows the biggest layout that fits (1×1, a wide strip, or full).
 * [make] builds the views for one layout. Before Android 12 the full layout is used.
 */
fun responsiveWidget(small: Int, wide: Int?, full: Int, make: (layout: Int) -> android.widget.RemoteViews): android.widget.RemoteViews {
    if (android.os.Build.VERSION.SDK_INT < 31) return make(full)
    val map = linkedMapOf(android.util.SizeF(30f, 30f) to make(small))
    if (wide != null) map[android.util.SizeF(150f, 30f)] = make(wide)
    map[android.util.SizeF(110f, 105f)] = make(full)
    return android.widget.RemoteViews(map)
}


/** A light tap the user can feel, following the phone's touch-feedback setting. */
fun android.view.View.tick() { performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) }
