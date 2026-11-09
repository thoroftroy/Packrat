package dev.wonderdesk.shared

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

fun decodeImage(context: Context, uri: Uri, limit: Int): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
    val scale = minOf(1.0, limit.toDouble() / maxOf(info.size.width, info.size.height))
    decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
}

@Composable
fun PhotoThumbnail(uri: Uri, name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) { value = withContext(Dispatchers.IO) { runCatching { decodeImage(context, uri, 256) }.getOrNull() } }
    Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer)) {
        bitmap?.let { Image(it.asImageBitmap(), name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Text("Preview unavailable", Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
    }
}

private data class PhotoRef(val uri: Uri, val name: String)

@Composable
fun PhotosScreen(library: Boolean) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("collection", Context.MODE_PRIVATE) }
    val op = rememberOperation()
    var photos by remember { mutableStateOf(listOf<PhotoRef>()) }
    var selected by remember { mutableStateOf<PhotoRef?>(null) }
    var query by remember { mutableStateOf("") }
    fun store(values: List<PhotoRef>) {
        photos = values
        if (library) prefs.edit().putString("uris", JSONArray(values.map { it.uri.toString() }).toString()).apply()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { values ->
        if (values.isNotEmpty()) op.run("Loading selected photo references", {
            values.map { uri -> context.keepRead(uri); PhotoRef(uri, context.displayName(uri)) }
        }) {
            store(if (library) (photos + it).distinctBy { p -> p.uri } else it)
            if (!library) selected = it.firstOrNull()
            op.notice("${it.size} selected. Originals were not copied or changed.")
        }
    }
    LaunchedEffect(Unit) {
        val initial = (context as ComponentActivity).intent.data
        if (initial != null) op.run("Opening image", { PhotoRef(initial, context.displayName(initial)) }) { photos = listOf(it); selected = it; op.notice("Image opened") }
        else if (library) op.run("Loading saved collection", {
            val saved = JSONArray(prefs.getString("uris", "[]"))
            (0 until saved.length()).map { Uri.parse(saved.getString(it)) }.map { uri -> PhotoRef(uri, runCatching { context.displayName(uri) }.getOrDefault("That file could not be read. Select it again.")) }
        }) { photos = it; op.notice("${it.size} saved references. Missing permissions require selecting the photo again.") }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AquaButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !op.busy) { Text(if (library) "Add photos to collection" else "Open images") }
        Text(if (library) "A local collection of selected photo references. Removing a reference never deletes its original." else "Select images, then pinch to zoom and drag to pan. Rotation is a preview only.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Filter by name") }, singleLine = true)
        OperationStatus(op)
        LazyVerticalGrid(GridCells.Adaptive(112.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(photos.filter { it.name.contains(query, true) }, key = { it.uri.toString() }) { photo ->
                Card(onClick = { selected = photo }) {
                    PhotoThumbnail(photo.uri, photo.name, Modifier.fillMaxWidth().aspectRatio(1f))
                    Text(photo.name, Modifier.padding(8.dp), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    selected?.let { photo ->
        Dialog(onDismissRequest = { selected = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            var zoom by remember(photo.uri) { mutableFloatStateOf(1f) }
            var offset by remember(photo.uri) { mutableStateOf(Offset.Zero) }
            var rotation by remember(photo.uri) { mutableFloatStateOf(0f) }
            var error by remember(photo.uri) { mutableStateOf("") }
            val bitmap by produceState<Bitmap?>(null, photo.uri) {
                try { value = withContext(Dispatchers.IO) { decodeImage(context, photo.uri, 2048) } }
                catch (e: Exception) { error = "Image cannot be decoded or access was revoked. Select it again." }
            }
            Surface(Modifier.fillMaxSize().systemBarsPadding()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(photo.name, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { selected = null }) { Text("Close") }
                        TextButton(onClick = { context.share(photo.uri, "image/*") { error = it } }) { Text("Share") }
                        TextButton(onClick = { rotation = (rotation + 90f) % 360 }) { Text("Rotate") }
                    }
                    if (error.isNotEmpty()) Text(error)
                    Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF18212B)).pointerInput(photo.uri) {
                        detectTransformGestures { _, pan, scale, _ ->
                            zoom = (zoom * scale).coerceIn(1f, 6f)
                            offset = if (zoom == 1f) Offset.Zero else Offset((offset.x + pan.x).coerceIn(-2000f, 2000f), (offset.y + pan.y).coerceIn(-2000f, 2000f))
                        }
                    }) {
                        bitmap?.let { Image(it.asImageBitmap(), photo.name, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y; rotationZ = rotation }, contentScale = ContentScale.Fit) }
                    }
                    bitmap?.let { Text("Decoded preview: ${it.width} × ${it.height} · originals unchanged", style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val index = photos.indexOf(photo)
                        AquaSecondaryButton(onClick = { selected = photos[index - 1] }, enabled = index > 0) { Text("Previous") }
                        AquaSecondaryButton(onClick = { zoom = 1f; offset = Offset.Zero; rotation = 0f }) { Text("Fit") }
                        AquaSecondaryButton(onClick = { selected = photos[index + 1] }, enabled = index >= 0 && index + 1 < photos.size) { Text("Next") }
                    }
                    if (library) TextButton(onClick = { store(photos.filterNot { it.uri == photo.uri }); selected = null }) { Text("Remove reference (keep original)") }
                }
            }
        }
    }
}
