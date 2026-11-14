package dev.wonderdesk.shared

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Something an app needs from Android to work fully: a runtime permission, a special-access switch or any other
 * setting. Apps list theirs in [WonderApp]'s `needs`; a slim banner appears whenever a required one is off, and
 * Settings shows every one with its state and a button that goes straight to the right switch.
 *
 * @param permissions runtime permissions to request (any one granted counts when [anyOf]).
 * @param access a special-access switch, opened with the step-by-step [AccessGuide].
 * @param check a custom test; [fix] a custom way to turn it on.
 * @param required false for nice-to-haves: they show in Settings but never in the banner.
 */
data class WonderNeed(
    val title: String,
    val why: String,
    val required: Boolean = true,
    val permissions: List<String> = emptyList(),
    val anyOf: Boolean = false,
    val access: SpecialAccess.Kind? = null,
    val component: Class<*>? = null,
    val check: ((Context) -> Boolean)? = null,
    val fix: ((Context) -> Unit)? = null,
    /** What the banner says when this is the one problem; defaults to "<title> is off". */
    val alert: String? = null,
) {
    fun met(c: Context): Boolean = runCatching {
        when {
            check != null -> check.invoke(c)
            access != null -> SpecialAccess.granted(c, access, component)
            permissions.isNotEmpty() -> if (anyOf) permissions.any { ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED }
                                        else permissions.all { ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED }
            else -> true
        }
    }.getOrDefault(true)

    companion object {
        /** Android 13+ notification permission (always met on older versions). */
        fun notifications(why: String, required: Boolean = true) = WonderNeed("Notifications", why, required,
            check = { c -> androidx.core.app.NotificationManagerCompat.from(c).areNotificationsEnabled() },
            fix = null, permissions = if (android.os.Build.VERSION.SDK_INT >= 33) listOf(android.Manifest.permission.POST_NOTIFICATIONS) else emptyList())
    }
}

/** Checks [needs] each time the app comes back to the screen. */
@Composable
fun rememberNeedState(needs: List<WonderNeed>): State<Map<WonderNeed, Boolean>> {
    val context = LocalContext.current
    val resumed = rememberIsResumed()
    val state = remember(needs) { mutableStateOf(needs.associateWith { it.met(context) }) }
    LaunchedEffect(resumed, needs) { if (resumed) state.value = needs.associateWith { it.met(context) } }
    return state
}

/** Turns one need on: asks for the permission, walks through the special-access switch, or opens the setting. */
@Composable
fun rememberNeedFixer(onChanged: () -> Unit = {}): (WonderNeed) -> Unit {
    val context = LocalContext.current
    var guide by remember { mutableStateOf<WonderNeed?>(null) }
    var pending by remember { mutableStateOf<WonderNeed?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val n = pending; pending = null
        // Android stops showing the prompt after two refusals; then the only way is App info.
        if (n != null && !n.met(context) && r.values.none { it }) {
            val act = context as? android.app.Activity
            val canAsk = act != null && n.permissions.any { androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(act, it) }
            if (!canAsk) SpecialAccess.openAppInfo(context)
        }
        onChanged()
    }
    guide?.let { n ->
        AccessGuide(n.access!!, n.why, onDismiss = { guide = null; onChanged() }, component = n.component, onGranted = onChanged)
    }
    return remember {
        { n: WonderNeed ->
            when {
                n.fix != null -> n.fix.invoke(context)
                n.access != null -> guide = n
                n.permissions.isNotEmpty() -> { pending = n; ask.launch(n.permissions.toTypedArray()) }
                n.title == "Notifications" -> context.launchSafely(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)) {}
                else -> SpecialAccess.openAppInfo(context)
            }
        }
    }
}

/** Slim strip under the toolbar while something required is off. Tapping it fixes the first one; × hides it until next launch. */
@Composable
fun NeedsBanner(needs: List<WonderNeed>) {
    if (needs.isEmpty()) return
    val p = wonderPalette()
    val state by rememberNeedState(needs)
    var tick by remember { mutableIntStateOf(0) }
    val fixer = rememberNeedFixer { tick++ }
    var hidden by rememberSaveableBoolean("needs-hidden")
    val missing = needs.filter { it.required && state[it] == false }
    var open by remember { mutableStateOf(false) }
    AnimatedVisibility(missing.isNotEmpty() && !hidden, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Row(Modifier.fillMaxWidth().background(p.warning.copy(alpha = if (p.dark) .22f else .16f)).clickable { if (missing.size == 1) fixer(missing.first()) else open = true }
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(WonderIcons.Warning, null, tint = p.warning, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (missing.size == 1) (missing.first().alert ?: (missing.first().title + " is off")) else "${missing.size} things need your OK", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                Text(if (missing.size == 1) missing.first().why else missing.joinToString(" · ") { it.title }, style = MaterialTheme.typography.labelSmall, color = p.secondaryText, maxLines = 2)
            }
            TextButton(onClick = { if (missing.size == 1) fixer(missing.first()) else open = true }) { Text("Fix") }
            IconButton(onClick = { hidden = true }) { Icon(WonderIcons.Close, "Hide for now", tint = p.secondaryText, modifier = Modifier.size(18.dp)) }
        }
    }
    if (open) WonderDialog(onDismissRequest = { open = false }, title = { Text("To work fully") }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) { NeedsList(needs, state, fixer) }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text("Done") } })
}

/** Every need with its state; the Settings page shows this as "Permissions and access". */
@Composable
fun NeedsList(needs: List<WonderNeed>, state: Map<WonderNeed, Boolean>, fixer: (WonderNeed) -> Unit) {
    val p = wonderPalette()
    val context = LocalContext.current
    Panel {
        needs.forEachIndexed { i, n ->
            if (i > 0) PanelDivider()
            val ok = state[n] != false
            ListRow(n.title, subtitle = n.why + if (!n.required) " (optional)" else "", icon = if (ok) WonderIcons.CheckCircle else WonderIcons.Warning,
                tint = if (ok) p.success else if (n.required) p.warning else p.secondaryText,
                trailing = if (ok) ({ Text("On", color = p.success, style = MaterialTheme.typography.labelLarge) }) else ({ TextButton(onClick = { fixer(n) }) { Text("Turn on") } }),
                onClick = { if (!ok) fixer(n) else SpecialAccess.openAppInfo(context) })
        }
    }
}

@Composable
internal fun NeedsSettings(needs: List<WonderNeed>) {
    if (needs.isEmpty()) return
    val state by rememberNeedState(needs)
    var tick by remember { mutableIntStateOf(0) }
    val fixer = rememberNeedFixer { tick++ }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Permissions and access", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = wonderPalette().secondaryText)
        NeedsList(needs, state, fixer)
        Text("Each one opens the exact switch in Android's settings. Come back and it updates by itself.", Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = wonderPalette().secondaryText)
    }
}

@Composable
private fun rememberSaveableBoolean(@Suppress("UNUSED_PARAMETER") key: String): MutableState<Boolean> = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
