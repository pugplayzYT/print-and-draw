package com.pugplayzyt.printanddraw

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PrintAndDrawApp()
                }
            }
        }
    }
}

data class Segment(
    val start: Offset,
    val end: Offset,
    val argb: Int,
    val width: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintAndDrawApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val segments = remember { mutableStateListOf<Segment>() }
    val redo = remember { mutableStateListOf<Segment>() }
    var selectedColor by remember { mutableStateOf(Color(0xFF1565C0)) }
    var strokeWidth by remember { mutableFloatStateOf(32f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Print & Draw") },
                actions = {
                    IconButton(
                        enabled = segments.isNotEmpty(),
                        onClick = {
                            if (segments.isNotEmpty()) {
                                redo.add(segments.removeAt(segments.lastIndex))
                            }
                        }
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        enabled = redo.isNotEmpty(),
                        onClick = {
                            if (redo.isNotEmpty()) {
                                segments.add(redo.removeAt(redo.lastIndex))
                            }
                        }
                    ) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ColorWheel(
                    color = selectedColor,
                    onColor = { selectedColor = it }
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text("Block thickness: ${strokeWidth.toInt()} px")
                    Slider(
                        value = strokeWidth,
                        onValueChange = { strokeWidth = it },
                        valueRange = 8f..120f
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (canvasSize != IntSize.Zero) {
                                    val bitmap = renderBitmap(canvasSize, segments)
                                    printBitmap(context, bitmap)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Print")
                        }
                        Button(
                            onClick = {
                                if (canvasSize != IntSize.Zero) {
                                    val bitmap = renderBitmap(canvasSize, segments)
                                    savePng(context, bitmap)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("PNG")
                        }
                    }
                }
            }

            Text("Drag from one point to another to place a coloured block.")

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White)
                    .onSizeChanged { canvasSize = it }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(selectedColor, strokeWidth) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    dragStart = pos
                                    dragCurrent = pos
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    dragCurrent = change.position
                                },
                                onDragEnd = {
                                    val start = dragStart
                                    val end = dragCurrent
                                    if (start != null && end != null && start != end) {
                                        segments.add(
                                            Segment(
                                                start = start,
                                                end = end,
                                                argb = selectedColor.toArgb(),
                                                width = strokeWidth
                                            )
                                        )
                                        redo.clear()
                                    }
                                    dragStart = null
                                    dragCurrent = null
                                },
                                onDragCancel = {
                                    dragStart = null
                                    dragCurrent = null
                                }
                            )
                        }
                ) {
                    segments.forEach { segment ->
                        drawLine(
                            color = Color(segment.argb),
                            start = segment.start,
                            end = segment.end,
                            strokeWidth = segment.width,
                            cap = StrokeCap.Square
                        )
                    }

                    val start = dragStart
                    val end = dragCurrent
                    if (start != null && end != null) {
                        drawLine(
                            color = selectedColor,
                            start = start,
                            end = end,
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Square
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ColorWheel(color: Color, onColor: (Color) -> Unit) {
    val wheel = remember { buildColorWheel(320).asImageBitmap() }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            bitmap = wheel,
            contentDescription = "Colour wheel",
            modifier = Modifier
                .size(150.dp)
                .pointerInput(Unit) {
                    detectTapGestures { point ->
                        val radius = min(size.width, size.height) / 2f
                        val dx = point.x - size.width / 2f
                        val dy = point.y - size.height / 2f
                        val distance = hypot(dx, dy)
                        if (distance <= radius) {
                            val saturation = (distance / radius).coerceIn(0f, 1f)
                            val hue = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
                            onColor(Color.hsv(hue, saturation, 1f))
                        }
                    }
                }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(width = 150.dp, height = 20.dp)
                .background(color)
        )
    }
}

fun buildColorWheel(size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val center = size / 2f
    val radius = size / 2f
    val hsv = FloatArray(3)

    for (y in 0 until size) {
        for (x in 0 until size) {
            val dx = x - center
            val dy = y - center
            val distance = hypot(dx, dy)
            if (distance <= radius) {
                hsv[0] = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
                hsv[1] = (distance / radius).coerceIn(0f, 1f)
                hsv[2] = 1f
                bitmap.setPixel(x, y, android.graphics.Color.HSVToColor(hsv))
            } else {
                bitmap.setPixel(x, y, android.graphics.Color.TRANSPARENT)
            }
        }
    }
    return bitmap
}

fun renderBitmap(size: IntSize, segments: List<Segment>): Bitmap {
    val width = size.width.coerceAtLeast(1)
    val height = size.height.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
    }

    segments.forEach { segment ->
        paint.color = segment.argb
        paint.strokeWidth = segment.width
        canvas.drawLine(
            segment.start.x,
            segment.start.y,
            segment.end.x,
            segment.end.y,
            paint
        )
    }
    return bitmap
}

fun printBitmap(context: Context, bitmap: Bitmap) {
    PrintHelper(context).apply {
        scaleMode = PrintHelper.SCALE_MODE_FIT
        colorMode = PrintHelper.COLOR_MODE_COLOR
    }.printBitmap("Print & Draw", bitmap)
}

fun savePng(context: Context, bitmap: Bitmap) {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "print_and_draw_$stamp.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PrintAndDraw")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

    if (uri == null) {
        Toast.makeText(context, "Could not create PNG", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        Toast.makeText(context, "Saved to Pictures/PrintAndDraw", Toast.LENGTH_SHORT).show()
    } catch (error: Exception) {
        resolver.delete(uri, null, null)
        Toast.makeText(context, "Save failed: ${error.message}", Toast.LENGTH_LONG).show()
    }
}
