package com.pugplayzyt.printanddraw

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as GCanvas
import android.graphics.Paint
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.print.PrintHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { PrintAndDraw() } }
    }
}

data class Segment(val a: Offset, val b: Offset, val color: Int, val width: Float)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintAndDraw() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val lines = remember { mutableStateListOf<Segment>() }
    val redo = remember { mutableStateListOf<Segment>() }
    var color by remember { mutableStateOf(Color.Blue) }
    var width by remember { mutableFloatStateOf(32f) }
    var start by remember { mutableStateOf<Offset?>(null) }
    var end by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Print & Draw") }, actions = {
            IconButton(enabled = lines.isNotEmpty(), onClick = { if (lines.isNotEmpty()) redo.add(lines.removeAt(lines.lastIndex)) }) { Icon(Icons.Default.Undo, "Undo") }
            IconButton(enabled = redo.isNotEmpty(), onClick = { if (redo.isNotEmpty()) lines.add(redo.removeAt(redo.lastIndex)) }) { Icon(Icons.Default.Redo, "Redo") }
        })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ColorWheel(color) { color = it }
                Column(Modifier.weight(1f)) {
                    Text("Block thickness: ${width.toInt()} px")
                    Slider(width, { width = it }, valueRange = 8f..120f)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { if (size != IntSize.Zero) printBitmap(ctx, render(size, lines)) }) { Icon(Icons.Default.Print, null); Spacer(Modifier.width(5.dp)); Text("Print") }
                        Button(onClick = { if (size != IntSize.Zero) savePng(ctx, render(size, lines)) }) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(5.dp)); Text("PNG") }
                    }
                }
            }
            Text("Drag from one point to another to place a coloured block.")
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.White).onSizeChanged { size = it }) {
                Canvas(Modifier.fillMaxSize().pointerInput(color, width) {
                    detectDragGestures(
                        onDragStart = { start = it; end = it },
                        onDrag = { change, _ -> change.consume(); end = change.position },
                        onDragEnd = {
                            val a = start; val b = end
                            if (a != null && b != null && a != b) { lines.add(Segment(a, b, color.toArgb(), width)); redo.clear() }
                            start = null; end = null
                        },
                        onDragCancel = { start = null; end = null }
                    )
                }) {
                    lines.forEach { drawLine(Color(it.color), it.a, it.b, it.width, StrokeCap.Square) }
                    val a = start; val b = end
                    if (a != null && b != null) drawLine(color, a, b, width, StrokeCap.Square)
                }
            }
        }
    }
}

@Composable
fun ColorWheel(selected: Color, choose: (Color) -> Unit) {
    val image = remember { makeWheel(280).asImageBitmap() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(image, "Colour wheel", Modifier.size(140.dp).pointerInput(Unit) {
            detectTapGestures { p ->
                val r = min(size.width, size.height) / 2f
                val dx = p.x - size.width / 2f; val dy = p.y - size.height / 2f
                val d = hypot(dx, dy)
                if (d <= r) choose(Color.hsv(((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f, (d / r).coerceIn(0f, 1f), 1f))
            }
        })
        Box(Modifier.size(140.dp, 18.dp).background(selected))
    }
}

fun makeWheel(n: Int): Bitmap {
    val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888); val c = n / 2f; val hsv = FloatArray(3)
    for (y in 0 until n) for (x in 0 until n) {
        val dx = x - c; val dy = y - c; val d = hypot(dx, dy)
        b.setPixel(x, y, if (d <= c) { hsv[0] = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f; hsv[1] = (d / c).coerceIn(0f, 1f); hsv[2] = 1f; android.graphics.Color.HSVToColor(hsv) } else android.graphics.Color.TRANSPARENT)
    }
    return b
}

fun render(size: IntSize, lines: List<Segment>): Bitmap {
    val b = Bitmap.createBitmap(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = GCanvas(b); c.drawColor(android.graphics.Color.WHITE)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.SQUARE }
    lines.forEach { p.color = it.color; p.strokeWidth = it.width; c.drawLine(it.a.x, it.a.y, it.b.x, it.b.y, p) }
    return b
}

fun printBitmap(ctx: Context, b: Bitmap) = PrintHelper(ctx).apply { scaleMode = PrintHelper.SCALE_MODE_FIT; colorMode = PrintHelper.COLOR_MODE_COLOR }.printBitmap("Print & Draw", b)

fun savePng(ctx: Context, b: Bitmap) {
    val name = "print_and_draw_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"
    val v = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PrintAndDraw"); put(MediaStore.Images.Media.IS_PENDING, 1) }
    val r = ctx.contentResolver; val u = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
    if (u == null) { Toast.makeText(ctx, "Could not create PNG", Toast.LENGTH_SHORT).show(); return }
    try { r.openOutputStream(u)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; v.clear(); v.put(MediaStore.Images.Media.IS_PENDING, 0); r.update(u, v, null, null); Toast.makeText(ctx, "Saved to Pictures/PrintAndDraw", Toast.LENGTH_SHORT).show() }
    catch (e: Exception) { r.delete(u, null, null); Toast.makeText(ctx, "Save failed", Toast.LENGTH_LONG).show() }
}
