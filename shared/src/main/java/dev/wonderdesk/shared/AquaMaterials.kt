package dev.wonderdesk.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Layered metal and gel roles informed by the owner's Aqua Bliss renderer. */
@Composable
fun Modifier.aquaBrushedMetal(dark: Boolean): Modifier {
    val look = LocalLook.current
    return drawBehind {
    val colors = if (look == WonderLook.BLISS) listOf(Color(0xFF0058E6), Color(0xFF1A70F0), Color(0xFF3A93FF), Color(0xFF1D6CE5), Color(0xFF0058E6))
                 else if (look == WonderLook.SUPER) listOf(Color(0xFF1A3456), Color(0xFF5B2EE5), Color(0xFFB01EFF), Color(0xFF2A2546), Color(0xFF1B4A55))
                 else if (dark) listOf(Color(0xFF4A6076), Color(0xFF354A5E), Color(0xFF1E3042), Color(0xFF2A3F52), Color(0xFF3A5066))
                 else listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC), Color(0xFFDCE3E9), Color(0xFFB3BFCB), Color(0xFFEEF3F7))
    drawRect(Brush.verticalGradient(0f to colors[0], .04f to colors[1], .48f to colors[2], .52f to colors[3], 1f to colors[4]))
    val spacing = 3.dp.toPx().coerceAtLeast(1f)
    var y = spacing
    while (y < size.height) {
        drawLine(Color.White.copy(alpha = if (dark) .025f else .055f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx() / 3f)
        y += spacing
    }
    drawLine(Color.White.copy(alpha = if (dark) .22f else .65f), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
    }
}

@Composable
fun AquaButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
               contentPadding: PaddingValues = ButtonDefaults.ContentPadding, content: @Composable RowScope.() -> Unit) {
    val dark = LocalIsDark.current
    val look = LocalLook.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(18.dp)
    val colors = when {
        !enabled && dark -> listOf(Color(0xFF475361), Color(0xFF374352), Color(0xFF303A47), Color(0xFF394754))
        !enabled -> listOf(Color(0xFFF2F4F6), Color(0xFFE2E7EB), Color(0xFFD3DCE4), Color(0xFFE6EAEE))
        look == WonderLook.SUPER -> listOf(Color(0xFFFFD07D), Color(0xFFFF855E), Color(0xFFF65C9C), Color(0xFFFF855E))
        look == WonderLook.BLISS -> listOf(Color.White, Color(0xFFF5F3EA), Color(0xFFD4D0C8), Color(0xFFF5F3EA))
        pressed -> listOf(Color(0xFFB8DEF7), Color(0xFF88C1EF), Color(0xFF69A9E2), Color(0xFF9FD6F5))
        dark -> listOf(Color(0xFFD8ECFF), Color(0xFF9FD0F5), Color(0xFF5EA9E6), Color(0xFF8FC6F0))
        else -> listOf(Color(0xFFFFFFFF), Color(0xFFD8ECFF), Color(0xFF8FC6F0), Color(0xFF5EA9E6), Color(0xFFAEDCF7))
    }
    val stops = if (!enabled || pressed || look == WonderLook.SUPER || look == WonderLook.BLISS) listOf(0f, .47f, .52f, 1f) else if (dark) listOf(0f, .18f, .47f, .52f, 1f) else listOf(0f, .18f, .47f, .52f, 1f)
    val brush = Brush.verticalGradient(*stops.zip(colors).map { it.first to it.second }.toTypedArray())
    val border = if (!enabled) Color(0xFF8996A1) else Color(0xFF2F5D84)
    MaterialButton(
        onClick = onClick, enabled = enabled, interactionSource = interaction, shape = shape,
        modifier = modifier.heightIn(min = 48.dp).background(brush, shape).border(1.dp, border, shape),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color(0xFF102A43),
            disabledContainerColor = Color.Transparent, disabledContentColor = if (dark) Color(0xFFC1CBD5) else Color(0xFF4E5964)),
        contentPadding = contentPadding, content = content
    )
}

/** Silver secondary control with the same 48dp touch floor as primary actions. */
@Composable
fun AquaSecondaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                        contentPadding: PaddingValues = ButtonDefaults.ContentPadding, content: @Composable RowScope.() -> Unit) {
    val dark = LocalIsDark.current
    val look = LocalLook.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(14.dp)
    val colors = when {
        !enabled && dark -> listOf(Color(0xFF475361), Color(0xFF374352), Color(0xFF303A47), Color(0xFF394754))
        !enabled -> listOf(Color(0xFFF2F4F6), Color(0xFFE2E7EB), Color(0xFFD3DCE4), Color(0xFFE6EAEE))
        dark && pressed -> listOf(Color(0xFF304254), Color(0xFF26394A), Color(0xFF1A2938), Color(0xFF33485A))
        dark -> listOf(Color(0xFF42586C), Color(0xFF31475B), Color(0xFF1E3042), Color(0xFF34495D))
        pressed -> listOf(Color(0xFFD0DAE3), Color(0xFFB3C3D1), Color(0xFFABBCCB), Color(0xFFD7E3EB))
        else -> listOf(Color(0xFFFBFDFF), Color(0xFFDCE3E9), Color(0xFFB3BFCB), Color(0xFFEEF3F7))
    }
    MaterialButton(onClick = onClick, enabled = enabled, interactionSource = interaction, shape = shape,
        modifier = modifier.heightIn(min = 48.dp).background(
            Brush.verticalGradient(0f to colors[0], .47f to colors[1], .52f to colors[2], 1f to colors[3]), shape
        ).border(1.dp, if (dark) Color(0xFF6E859D) else Color(0xFF8093A6), shape),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent,
            contentColor = if (dark) Color(0xFFEAF4FF) else Color(0xFF102A43),
            disabledContainerColor = Color.Transparent, disabledContentColor = if (dark) Color(0xFFC1CBD5) else Color(0xFF4E5964)),
        contentPadding = contentPadding, content = content)
}
