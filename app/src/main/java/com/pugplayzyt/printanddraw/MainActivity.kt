package com.pugplayzyt.printanddraw

import android.content.ClipData
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
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.print.PrintHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { PrintAndDraw() } }
    }
}

data class Segment(val a: Offset, val b: Offset, val color: Int, val width: Float)

private const val DEFAULT_SCRIPT = """# Multi-style road demo
pen speed 1000
let y = h / 2

pen up
pen width 100
pen colour 90, 90, 90
pen position 40, y
pen down
pen position w - 40, y

pen up
pen width 22
pen colour 190, 185, 170
pen position 40, y - 62
pen down
pen position w - 40, y - 62
pen up
pen position 40, y + 62
pen down
pen position w - 40, y + 62

pen up
pen width 7
pen colour 255, 205, 0
pen position 40, y
pen down
pen position w - 40, y
pen up"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintAndDraw() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<Segment>() }
    val redo = remember { mutableStateListOf<Segment>() }

    var hue by remember { mutableFloatStateOf(240f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var brightness by remember { mutableFloatStateOf(1f) }
    val color = Color.hsv(hue, saturation, brightness)

    val developerCodeActive = hue.roundToInt() in 147..153 &&
        (saturation * 100).roundToInt() in 37..43 &&
        (brightness * 100).roundToInt() in 37..43

    val preferences = remember { ctx.getSharedPreferences("print_and_draw_settings", Context.MODE_PRIVATE) }
    var developerMode by remember { mutableStateOf(preferences.getBoolean("developer_mode", false)) }

    LaunchedEffect(developerCodeActive) {
        if (developerCodeActive && !developerMode) {
            developerMode = true
            preferences.edit().putBoolean("developer_mode", true).apply()
        }
    }

    var width by remember { mutableFloatStateOf(32f) }
    var start by remember { mutableStateOf<Offset?>(null) }
    var end by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showColourPicker by remember { mutableStateOf(false) }
    var showScriptEditor by remember { mutableStateOf(false) }
    var scriptText by remember { mutableStateOf(DEFAULT_SCRIPT) }
    var scriptStatus by remember { mutableStateOf("Ready") }
    var scriptJob by remember { mutableStateOf<Job?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Print & Draw") },
                actions = {
                    IconButton(enabled = lines.isNotEmpty(), onClick = {
                        if (lines.isNotEmpty()) redo.add(lines.removeAt(lines.lastIndex))
                    }) { Icon(Icons.Default.Undo, "Undo") }
                    IconButton(enabled = redo.isNotEmpty(), onClick = {
                        if (redo.isNotEmpty()) lines.add(redo.removeAt(redo.lastIndex))
                    }) { Icon(Icons.Default.Redo, "Redo") }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Block thickness: ${width.toInt()} px", style = MaterialTheme.typography.titleMedium)
            Slider(value = width, onValueChange = { width = it }, valueRange = 8f..120f)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp)
                        .background(color, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Selected colour", style = MaterialTheme.typography.labelLarge)
                    Text(colorToHex(color), style = MaterialTheme.typography.bodyMedium)
                }
                FilledTonalButton(onClick = { showColourPicker = true }) {
                    Icon(Icons.Default.ColorLens, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Pick colour")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    if (canvasSize != IntSize.Zero) printBitmap(ctx, render(canvasSize, lines))
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Print, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Print")
                }
                Button(onClick = {
                    if (canvasSize != IntSize.Zero) savePng(ctx, render(canvasSize, lines))
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Save PNG")
                }
            }

            OutlinedButton(
                onClick = {
                    scriptJob?.cancel()
                    scriptStatus = "Ready"
                    lines.clear()
                    redo.clear()
                    start = null
                    end = null
                },
                enabled = lines.isNotEmpty() || scriptJob?.isActive == true,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.DeleteSweep, null)
                Spacer(Modifier.width(8.dp))
                Text("Clear canvas")
            }

            if (developerMode) {
                FilledTonalButton(onClick = { showScriptEditor = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Code, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scripting block • Developer mode")
                }
            }

            Text("Drag from one point to another to place a coloured block.")

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .onSizeChanged { canvasSize = it }
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize().pointerInput(color, width, canvasSize) {
                        fun bounded(point: Offset): Offset = Offset(
                            point.x.coerceIn(0f, canvasSize.width.toFloat()),
                            point.y.coerceIn(0f, canvasSize.height.toFloat())
                        )
                        detectDragGestures(
                            onDragStart = { point ->
                                val safe = bounded(point)
                                start = safe
                                end = safe
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                end = bounded(change.position)
                            },
                            onDragEnd = {
                                val a = start
                                val b = end
                                if (a != null && b != null && a != b) {
                                    lines.add(Segment(a, b, color.toArgb(), width))
                                    redo.clear()
                                }
                                start = null
                                end = null
                            },
                            onDragCancel = { start = null; end = null }
                        )
                    }
                ) {
                    clipRect {
                        lines.forEach { drawLine(Color(it.color), it.a, it.b, it.width, StrokeCap.Square) }
                        val a = start
                        val b = end
                        if (a != null && b != null) drawLine(color, a, b, width, StrokeCap.Square)
                    }
                }
            }
        }
    }

    if (showColourPicker) {
        ColourPickerDialog(
            hue = hue,
            saturation = saturation,
            brightness = brightness,
            developerModeUnlocked = developerMode,
            onHueChange = { hue = it.roundToInt().toFloat() },
            onSaturationChange = { saturation = ((it * 100).roundToInt() / 100f).coerceIn(0f, 1f) },
            onBrightnessChange = { brightness = ((it * 100).roundToInt() / 100f).coerceIn(0f, 1f) },
            onWheelPick = { newHue, newSaturation -> hue = newHue; saturation = newSaturation },
            onDismiss = { showColourPicker = false }
        )
    }

    if (showScriptEditor) {
        ScriptEditorDialog(
            script = scriptText,
            status = scriptStatus,
            running = scriptJob?.isActive == true,
            onScriptChange = { scriptText = it },
            onRun = {
                if (canvasSize == IntSize.Zero) {
                    scriptStatus = "Canvas is not ready"
                } else {
                    scriptJob?.cancel()
                    redo.clear()
                    scriptJob = scope.launch {
                        try {
                            DrawScriptEngine.run(
                                source = scriptText,
                                canvasSize = canvasSize,
                                color = color.toArgb(),
                                strokeWidth = width,
                                onSegment = { lines.add(it) },
                                onStatus = { scriptStatus = it }
                            )
                        } catch (e: Exception) {
                            scriptStatus = if (e is kotlinx.coroutines.CancellationException) "Stopped" else "Error: ${e.message}"
                        }
                    }
                }
            },
            onStop = { scriptJob?.cancel(); scriptStatus = "Stopped" },
            onDismiss = { showScriptEditor = false }
        )
    }
}

@Composable
fun ScriptEditorDialog(
    script: String,
    status: String,
    running: Boolean,
    onScriptChange: (String) -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDocs by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), tonalElevation = 8.dp) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Drawing Script", style = MaterialTheme.typography.headlineSmall)
                    FilledTonalButton(onClick = { showDocs = true }) {
                        Icon(Icons.Default.MenuBook, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Docs")
                    }
                }
                Text(
                    "Scripts can now change pen width and RGB colour while running.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = script,
                    onValueChange = onScriptChange,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    label = { Text("Script") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                Text(status, style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRun, enabled = !running, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Run")
                    }
                    OutlinedButton(onClick = onStop, enabled = running, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Stop")
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }

    if (showDocs) ScriptDocsDialog(onDismiss = { showDocs = false })
}

@Composable
fun ScriptDocsDialog(onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.90f),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Scripting Docs", style = MaterialTheme.typography.headlineSmall)
                Text("Rendered Markdown reference", style = MaterialTheme.typography.bodySmall)

                RenderedMarkdown(
                    markdown = SCRIPTING_DOCS_V2,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Print & Draw scripting docs", SCRIPTING_DOCS_V2))
                            Toast.makeText(ctx, "Markdown docs copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Markdown")
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
fun ColourPickerDialog(
    hue: Float,
    saturation: Float,
    brightness: Float,
    developerModeUnlocked: Boolean,
    onHueChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onWheelPick: (Float, Float) -> Unit,
    onDismiss: () -> Unit
) {
    val selected = Color.hsv(hue, saturation, brightness)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.90f),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pick colour", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ColourWheel(hue, saturation, onWheelPick)
                    Box(
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                            .background(selected, RoundedCornerShape(14.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    )
                    Text(colorToHex(selected), style = MaterialTheme.typography.titleMedium)
                    SliderLabel("Hue", "${hue.roundToInt()}°")
                    Slider(value = hue, onValueChange = onHueChange, valueRange = 0f..360f)
                    SliderLabel("Saturation", "${(saturation * 100).roundToInt()}%")
                    Slider(value = saturation, onValueChange = onSaturationChange, valueRange = 0f..1f)
                    SliderLabel("Brightness", "${(brightness * 100).roundToInt()}%")
                    Slider(value = brightness, onValueChange = onBrightnessChange, valueRange = 0f..1f)
                    Box(modifier = Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.Center) {
                        if (developerModeUnlocked) {
                            Text("Developer mode unlocked ✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
        }
    }
}

@Composable
fun SliderLabel(name: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ColourWheel(hue: Float, saturation: Float, onPick: (Float, Float) -> Unit) {
    val image = remember { makeWheel(500).asImageBitmap() }
    Box(modifier = Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Image(
            bitmap = image,
            contentDescription = "Colour wheel",
            modifier = Modifier.matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val radius = min(size.width, size.height) / 2f
                        val dx = p.x - size.width / 2f
                        val dy = p.y - size.height / 2f
                        val d = hypot(dx, dy)
                        if (d <= radius) {
                            onPick(((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f, (d / radius).coerceIn(0f, 1f))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { p ->
                            val radius = min(size.width, size.height) / 2f
                            val dx = p.x - size.width / 2f
                            val dy = p.y - size.height / 2f
                            val d = hypot(dx, dy).coerceAtMost(radius)
                            onPick(((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f, (d / radius).coerceIn(0f, 1f))
                        },
                        onDrag = { change, _ ->
                            val p = change.position
                            val radius = min(size.width, size.height) / 2f
                            val dx = p.x - size.width / 2f
                            val dy = p.y - size.height / 2f
                            val d = hypot(dx, dy).coerceAtMost(radius)
                            onPick(((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f, (d / radius).coerceIn(0f, 1f))
                            change.consume()
                        }
                    )
                }
        )
        Canvas(modifier = Modifier.matchParentSize()) {
            val radius = size.minDimension / 2f
            val angle = Math.toRadians(hue.toDouble())
            val markerDistance = radius * saturation
            val marker = Offset(
                center.x + cos(angle).toFloat() * markerDistance,
                center.y + sin(angle).toFloat() * markerDistance
            )
            drawCircle(Color.White, 12f, marker, style = Stroke(width = 5f))
            drawCircle(Color.Black, 8f, marker, style = Stroke(width = 2f))
        }
    }
}

fun makeWheel(n: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    val center = n / 2f
    val hsv = FloatArray(3)
    for (y in 0 until n) for (x in 0 until n) {
        val dx = x - center
        val dy = y - center
        val d = hypot(dx, dy)
        bitmap.setPixel(x, y, if (d <= center) {
            hsv[0] = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
            hsv[1] = (d / center).coerceIn(0f, 1f)
            hsv[2] = 1f
            android.graphics.Color.HSVToColor(hsv)
        } else android.graphics.Color.TRANSPARENT)
    }
    return bitmap
}

fun render(size: IntSize, lines: List<Segment>): Bitmap {
    val bitmap = Bitmap.createBitmap(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = GCanvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.SQUARE }
    lines.forEach {
        paint.color = it.color
        paint.strokeWidth = it.width
        canvas.drawLine(it.a.x, it.a.y, it.b.x, it.b.y, paint)
    }
    return bitmap
}

fun printBitmap(ctx: Context, bitmap: Bitmap) {
    PrintHelper(ctx).apply {
        scaleMode = PrintHelper.SCALE_MODE_FIT
        colorMode = PrintHelper.COLOR_MODE_COLOR
    }.printBitmap("Print & Draw", bitmap)
}

fun savePng(ctx: Context, bitmap: Bitmap) {
    val name = "print_and_draw_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PrintAndDraw")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = ctx.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: run {
        Toast.makeText(ctx, "Could not create PNG", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        Toast.makeText(ctx, "Saved to Pictures/PrintAndDraw", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        Toast.makeText(ctx, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun colorToHex(color: Color): String = String.format(
    Locale.US,
    "#%02X%02X%02X",
    (color.red * 255).toInt(),
    (color.green * 255).toInt(),
    (color.blue * 255).toInt()
)
