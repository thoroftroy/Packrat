package dev.wonderdesk.shared

import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private data class Mark(val color: Int, val points: List<Offset>)

@Composable
fun AnnotationScreen() {
    val context = LocalContext.current
    val op = rememberOperation()
    var source by remember { mutableStateOf<Uri?>((context as ComponentActivity).intent.data) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var marks by remember { mutableStateOf(listOf<Mark>()) }
    var color by remember { mutableIntStateOf(android.graphics.Color.RED) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var export by remember { mutableStateOf<Pair<Bitmap, List<Mark>>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { source = it }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val snapshot = export
        if (uri != null && snapshot != null) op.run("Saving annotated PNG copy", {
            val result = snapshot.first.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = android.graphics.Canvas(result)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = maxOf(3f, result.width / 160f) }
            for (mark in snapshot.second) {
                paint.color = mark.color
                val path = android.graphics.Path()
                mark.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x * result.width, p.y * result.height) else path.lineTo(p.x * result.width, p.y * result.height) }
                canvas.drawPath(path, paint)
            }
            context.contentResolver.openOutputStream(uri, "w")!!.use { check(result.compress(Bitmap.CompressFormat.PNG, 100, it)) { "PNG encoding failed" } }
            result.recycle()
        }) { op.notice("New PNG copy saved at ${snapshot.first.width} × ${snapshot.first.height}. Original unchanged."); export = null }
    }
    LaunchedEffect(source) {
        source?.let { uri -> op.run("Decoding image", { decodeImage(context, uri, 2048) }) { bitmap = it; marks = emptyList(); op.notice("Image opened. Draw with one finger; undo removes the last stroke.") } }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Open an Android screenshot or another image to annotate. Screen capture/recording is not implemented.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AquaButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !op.busy) { Text("Open image") }
            AquaSecondaryButton(onClick = { bitmap?.let { export = it to marks; save.launch("Annotated.png") } }, enabled = bitmap != null && !op.busy) { Text("Save PNG copy") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { color = android.graphics.Color.RED }) { Text(if (color == android.graphics.Color.RED) "• Red" else "Red") }
            TextButton(onClick = { color = android.graphics.Color.BLUE }) { Text(if (color == android.graphics.Color.BLUE) "• Blue" else "Blue") }
            TextButton(onClick = { marks = marks.dropLast(1) }, enabled = marks.isNotEmpty()) { Text("Undo stroke") }
        }
        OperationStatus(op)
        val image = bitmap
        if (image != null) {
            fun normalized(position: Offset): Offset? {
                val ratio = minOf(canvasSize.width.toFloat() / image.width, canvasSize.height.toFloat() / image.height)
                if (ratio <= 0) return null
                val width = image.width * ratio; val height = image.height * ratio
                val left = (canvasSize.width - width) / 2f; val top = (canvasSize.height - height) / 2f
                return Offset(((position.x - left) / width).coerceIn(0f, 1f), ((position.y - top) / height).coerceIn(0f, 1f))
            }
            Canvas(Modifier.fillMaxWidth().weight(1f).onSizeChanged { canvasSize = it }.pointerInput(image, color) {
                detectDragGestures(onDragStart = { start -> normalized(start)?.let { marks = marks + Mark(color, listOf(it)) } }, onDrag = { change, _ ->
                    change.consume()
                    normalized(change.position)?.let { point -> if (marks.isNotEmpty()) marks = marks.dropLast(1) + marks.last().copy(points = marks.last().points + point) }
                })
            }) {
                val ratio = minOf(size.width / image.width, size.height / image.height)
                val width = image.width * ratio; val height = image.height * ratio
                val left = (size.width - width) / 2; val top = (size.height - height) / 2
                drawImage(image.asImageBitmap(), dstOffset = IntOffset(left.toInt(), top.toInt()), dstSize = IntSize(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1)))
                for (mark in marks) {
                    val path = Path()
                    mark.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(left + p.x * width, top + p.y * height) else path.lineTo(left + p.x * width, top + p.y * height) }
                    drawPath(path, Color(mark.color), style = Stroke(maxOf(3f, image.width / 160f) * ratio))
                }
            }
            Text("Export resolution: ${image.width} × ${image.height} (preview capped at 2048 px). Originals are never overwritten.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
