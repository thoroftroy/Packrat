package dev.wonderdesk.shared

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Per-theme type personalities, adapted from the owner's BlissPlayer theme
 * direction. Bliss uses bundled PT Sans, Aqua uses Aqua Sans, Super uses
 * Super Sans, Dark Bliss uses the system font. */
fun wonderTypography(look: WonderLook): Typography {
    val base = Typography()
    val blissFamily = FontFamily(Font(R.font.ptsans))
    val aquaFamily = FontFamily(Font(R.font.aqua_sans))
    val superFamily = FontFamily(Font(R.font.super_sans))
    return when (look) {
        WonderLook.AQUA -> base.copy(
            displayMedium = base.displayMedium.copy(fontFamily = aquaFamily),
            headlineSmall = base.headlineSmall.copy(fontSize = 18.sp, letterSpacing = (-0.5).sp, fontFamily = aquaFamily),
            headlineMedium = base.headlineMedium.copy(letterSpacing = (-0.5).sp, fontFamily = aquaFamily),
            titleLarge = base.titleLarge.copy(letterSpacing = (-0.4).sp, fontFamily = aquaFamily),
            titleMedium = base.titleMedium.copy(letterSpacing = (-0.3).sp, fontFamily = aquaFamily),
            bodyLarge = base.bodyLarge.copy(fontFamily = aquaFamily),
            bodyMedium = base.bodyMedium.copy(fontFamily = aquaFamily),
            labelSmall = base.labelSmall.copy(letterSpacing = 0.2.sp, fontFamily = aquaFamily)
        )
        WonderLook.SUPER -> base.copy(
            displayMedium = base.displayMedium.copy(fontFamily = superFamily),
            headlineSmall = base.headlineSmall.copy(letterSpacing = 0.5.sp, fontFamily = superFamily),
            headlineMedium = base.headlineMedium.copy(letterSpacing = 0.5.sp, fontFamily = superFamily),
            titleLarge = base.titleLarge.copy(letterSpacing = 0.3.sp, fontFamily = superFamily),
            titleMedium = base.titleMedium.copy(letterSpacing = 0.2.sp, fontFamily = superFamily),
            bodyLarge = base.bodyLarge.copy(fontFamily = superFamily),
            bodyMedium = base.bodyMedium.copy(fontFamily = superFamily),
            labelLarge = base.labelLarge.copy(letterSpacing = 0.4.sp, fontFamily = superFamily),
            labelSmall = base.labelSmall.copy(fontFamily = superFamily)
        )
        WonderLook.BLISS -> base.copy(
            displayMedium = base.displayMedium.copy(fontFamily = blissFamily),
            headlineSmall = base.headlineSmall.copy(fontFamily = blissFamily),
            headlineMedium = base.headlineMedium.copy(fontFamily = blissFamily),
            titleLarge = base.titleLarge.copy(fontFamily = blissFamily),
            titleMedium = base.titleMedium.copy(fontFamily = blissFamily),
            bodyLarge = base.bodyLarge.copy(fontFamily = blissFamily),
            bodyMedium = base.bodyMedium.copy(fontFamily = blissFamily),
            labelLarge = base.labelLarge.copy(fontFamily = blissFamily),
            labelSmall = base.labelSmall.copy(fontFamily = blissFamily)
        )
        else -> base
    }
}

/** Dialog chrome per theme; content callers stay unchanged. */
@Composable
fun WonderDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null
) {
    val look = LocalLook.current
    val (shape, container, tonal) = when (look) {
        WonderLook.AQUA -> Triple(
            androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            Color(0xFFF4F8FB), 3.dp
        )
        WonderLook.BLISS -> Triple(
            androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            Color(0xFFF8F6EA), 1.dp
        )
        WonderLook.DARK -> Triple(
            androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            Color(0xFF2E333A), 1.dp
        )
        WonderLook.SUPER -> Triple(
            androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            Color(0xFF1A1230), 6.dp
        )
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        shape = shape,
        containerColor = container,
        tonalElevation = tonal
    )
}

/** Shared About/licence copy so every app stays consistent. */
@Composable
fun ColumnScope.AboutBody(app: AppInfo, version: String) {
    val setLook = LocalLookPreference.current
    val selectedLook = LocalLook.current
    val setMotion = LocalMotionPreference.current
    val selectedMotion = LocalMotion.current
    Text(app.description)
    Text(app.limits)
    Text(
        "Local-first. No analytics or automatic upload.",
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        "Third-party components retain their own licenses.",
        style = MaterialTheme.typography.bodySmall
    )
    Text("Appearance (this app)", style = MaterialTheme.typography.titleSmall)
    WonderLook.entries.forEach { look ->
        TextButton(onClick = { setLook(look) }) { Text(if (selectedLook == look) "✓ ${look.label}" else look.label) }
    }
    Text("Motion (this app)", style = MaterialTheme.typography.titleSmall)
    Text(
        "Reduced and Off disable ambient motion. Android's animation setting is always respected.",
        style = MaterialTheme.typography.bodySmall
    )
    WonderMotion.entries.forEach { motion ->
        TextButton(onClick = { setMotion(motion) }) { Text(if (selectedMotion == motion) "✓ ${motion.label}" else motion.label) }
    }
}
