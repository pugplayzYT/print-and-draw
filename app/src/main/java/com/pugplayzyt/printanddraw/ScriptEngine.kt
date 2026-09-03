package com.pugplayzyt.printanddraw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Tiny drawing-only language. It has no file, network, reflection, process,
 * Android, or arbitrary code execution features: the only observable output is
 * moving the virtual pen and emitting canvas segments or generated frames.
 */
object DrawScriptEngine {
    private const val MAX_STATEMENTS = 100_000
    private const val MAX_REPEAT = 100_000
    private const val MAX_FRAMES = 10_000
    private const val MIN_PEN_WIDTH = 1.0
    private const val MAX_PEN_WIDTH = 500.0
    private const val MIN_FRAME_RATE = 1.0
    private const val MAX_FRAME_RATE = 120.0
    private const val DEFAULT_FRAME_RATE = 30.0

    suspend fun run(
        source: String,
        canvasSize: IntSize,
        color: Int,
        strokeWidth: Float,
        onSegment: (Segment) -> Unit,
        onFrame: (segments: List<Segment>, frame: Int, totalFrames: Int) -> Unit = { _, _, _ -> },
        onStatus: (String) -> Unit = {}
    ) {
        if (canvasSize == IntSize.Zero) error("Canvas is not ready")

        val parser = Parser(source)
        val program = parser.parse()
        val env = mutableMapOf<String, Double>(
            "w" to canvasSize.width.toDouble(),
            "h" to canvasSize.height.toDouble(),
            "pi" to Math.PI
        )

        var penDown = false
        var pen = Offset(0f, 0f)
        var speed = 1000.0
        var currentColor = color
        var currentWidth = strokeWidth.coerceIn(MIN_PEN_WIDTH.toFloat(), MAX_PEN_WIDTH.toFloat())
        var executed = 0
        var frameSystemRegistered = false
        var frameRate = DEFAULT_FRAME_RATE
        var activeFrameSegments: MutableList<Segment>? = null
        val staticSegments = mutableListOf<Segment>()

        fun bounded(x: Double, y: Double): Offset = Offset(
            x.toFloat().coerceIn(0f, canvasSize.width.toFloat()),
            y.toFloat().coerceIn(0f, canvasSize.height.toFloat())
        )

        fun channel(expression: String): Int = Expression(expression, env)
            .value()
            .roundToInt()
            .coerceIn(0, 255)

        fun emit(segment: Segment) {
            val frame = activeFrameSegments
            if (frame != null) {
                frame += segment
            } else {
                staticSegments += segment
                onSegment(segment)
            }
        }

        suspend fun tick() {
            executed++
            if (executed > MAX_STATEMENTS) error("Script exceeded $MAX_STATEMENTS drawing steps")
            if (executed % 128 == 0) yield()
        }

        suspend fun execute(statements: List<Stmt>) {
            for (statement in statements) {
                when (statement) {
                    is Stmt.Assign -> {
                        env[statement.name] = Expression(statement.expression, env).value()
                        tick()
                    }

                    is Stmt.Pen -> {
                        penDown = statement.down
                        tick()
                    }

                    is Stmt.Speed -> {
                        speed = Expression(statement.expression, env).value().coerceIn(0.1, 1000.0)
                        tick()
                    }

                    is Stmt.Width -> {
                        currentWidth = Expression(statement.expression, env)
                            .value()
                            .coerceIn(MIN_PEN_WIDTH, MAX_PEN_WIDTH)
                            .toFloat()
                        tick()
                    }

                    is Stmt.Color -> {
                        val red = channel(statement.red)
                        val green = channel(statement.green)
                        val blue = channel(statement.blue)
                        currentColor = (
                            (255L shl 24) or
                                (red.toLong() shl 16) or
                                (green.toLong() shl 8) or
                                blue.toLong()
                            ).toInt()
                        tick()
                    }

                    is Stmt.Position -> {
                        val next = bounded(
                            Expression(statement.x, env).value(),
                            Expression(statement.y, env).value()
                        )
                        if (penDown && next != pen) {
                            emit(Segment(pen, next, currentColor, currentWidth))
                        }
                        pen = next
                        tick()

                        if (speed < 1000.0) {
                            delay((1000.0 / speed).toLong().coerceAtLeast(1L))
                        }
                    }

                    is Stmt.Repeat -> {
                        val count = Expression(statement.count, env).value().roundToInt()
                            .coerceIn(0, MAX_REPEAT)
                        repeat(count) { execute(statement.body) }
                    }

                    is Stmt.If -> {
                        if (Condition(statement.condition, env).value()) {
                            execute(statement.body)
                        }
                    }

                    Stmt.RegisterFrameSystem -> {
                        frameSystemRegistered = true
                        tick()
                    }

                    is Stmt.FrameRate -> {
                        if (!frameSystemRegistered) error("Register the frame system before setting frame rate")
                        frameRate = Expression(statement.expression, env)
                            .value()
                            .coerceIn(MIN_FRAME_RATE, MAX_FRAME_RATE)
                        tick()
                    }

                    is Stmt.GenerateFrames -> {
                        if (!frameSystemRegistered) error("Register the frame system before generating frames")
                        if (activeFrameSegments != null) error("Frame blocks cannot be nested")

                        val frameCount = Expression(statement.count, env).value().roundToInt()
                            .coerceIn(0, MAX_FRAMES)
                        val baselineEnv = env.toMap()
                        val baselinePenDown = penDown
                        val baselinePen = pen
                        val baselineSpeed = speed
                        val baselineColor = currentColor
                        val baselineWidth = currentWidth

                        tick()
                        for (frameNumber in 1..frameCount) {
                            env.clear()
                            env.putAll(baselineEnv)
                            env["frame"] = frameNumber.toDouble()
                            env["frames"] = frameCount.toDouble()
                            env["fps"] = frameRate

                            penDown = baselinePenDown
                            pen = baselinePen
                            speed = baselineSpeed
                            currentColor = baselineColor
                            currentWidth = baselineWidth

                            val frameSegments = mutableListOf<Segment>()
                            activeFrameSegments = frameSegments
                            try {
                                execute(statement.body)
                            } finally {
                                activeFrameSegments = null
                            }

                            onFrame(staticSegments + frameSegments, frameNumber, frameCount)
                            onStatus("Frame $frameNumber / $frameCount • ${frameRate.roundToInt()} fps")

                            if (frameNumber < frameCount) {
                                delay((1000.0 / frameRate).toLong().coerceAtLeast(1L))
                            }
                        }

                        env.clear()
                        env.putAll(baselineEnv)
                        penDown = baselinePenDown
                        pen = baselinePen
                        speed = baselineSpeed
                        currentColor = baselineColor
                        currentWidth = baselineWidth
                    }
                }
            }
        }

        onStatus("Running")
        execute(program)
        onStatus("Done • $executed steps")
    }

    private sealed interface Stmt {
        data class Assign(val name: String, val expression: String) : Stmt
        data class Pen(val down: Boolean) : Stmt
        data class Speed(val expression: String) : Stmt
        data class Width(val expression: String) : Stmt
        data class Color(val red: String, val green: String, val blue: String) : Stmt
        data class Position(val x: String, val y: String) : Stmt
        data class Repeat(val count: String, val body: List<Stmt>) : Stmt
        data class If(val condition: String, val body: List<Stmt>) : Stmt
        data object RegisterFrameSystem : Stmt
        data class FrameRate(val expression: String) : Stmt
        data class GenerateFrames(val count: String, val body: List<Stmt>) : Stmt
    }

    private class Parser(source: String) {
        private val lines = source.lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .toList()
        private var index = 0

        fun parse(): List<Stmt> {
            val result = parseBlock(root = true)
            if (index != lines.size) error("Unexpected text after script")
            return result
        }

        private fun parseBlock(root: Boolean): List<Stmt> {
            val out = mutableListOf<Stmt>()
            while (index < lines.size) {
                val line = lines[index]
                if (line == "}") {
                    if (root) error("Unexpected } on line ${index + 1}")
                    index++
                    return out
                }

                val generatedFrames = generatedFrameCount(line)
                when {
                    generatedFrames != null -> {
                        requireText(generatedFrames, "frame count")
                        index++
                        out += Stmt.GenerateFrames(generatedFrames, parseBlock(root = false))
                    }

                    line.startsWith("repeat ") && line.endsWith("{") -> {
                        val count = line.removePrefix("repeat ").removeSuffix("{").trim()
                        requireText(count, "repeat count")
                        index++
                        out += Stmt.Repeat(count, parseBlock(root = false))
                    }

                    line.startsWith("if ") && line.endsWith("{") -> {
                        val condition = line.removePrefix("if ").removeSuffix("{").trim()
                        requireText(condition, "if condition")
                        index++
                        out += Stmt.If(condition, parseBlock(root = false))
                    }

                    line == "register frame system" || line == "register frames" -> {
                        out += Stmt.RegisterFrameSystem
                        index++
                    }

                    line.startsWith("frame rate ") -> {
                        val expression = line.removePrefix("frame rate ").trim()
                        requireText(expression, "frame rate")
                        out += Stmt.FrameRate(expression)
                        index++
                    }

                    line.startsWith("fps ") -> {
                        val expression = line.removePrefix("fps ").trim()
                        requireText(expression, "frame rate")
                        out += Stmt.FrameRate(expression)
                        index++
                    }

                    line == "pen up" -> { out += Stmt.Pen(false); index++ }
                    line == "pen down" -> { out += Stmt.Pen(true); index++ }

                    line.startsWith("pen speed ") -> {
                        out += Stmt.Speed(line.removePrefix("pen speed ").trim())
                        index++
                    }
                    line.startsWith("speed ") -> {
                        out += Stmt.Speed(line.removePrefix("speed ").trim())
                        index++
                    }

                    line.startsWith("pen width ") -> {
                        val expression = line.removePrefix("pen width ").trim()
                        requireText(expression, "pen width")
                        out += Stmt.Width(expression)
                        index++
                    }
                    line.startsWith("width ") -> {
                        val expression = line.removePrefix("width ").trim()
                        requireText(expression, "pen width")
                        out += Stmt.Width(expression)
                        index++
                    }

                    line.startsWith("pen color ") -> {
                        out += parseColor(line.removePrefix("pen color "))
                        index++
                    }
                    line.startsWith("pen colour ") -> {
                        out += parseColor(line.removePrefix("pen colour "))
                        index++
                    }
                    line.startsWith("color ") -> {
                        out += parseColor(line.removePrefix("color "))
                        index++
                    }
                    line.startsWith("colour ") -> {
                        out += parseColor(line.removePrefix("colour "))
                        index++
                    }

                    line.startsWith("pen position ") -> {
                        out += parsePosition(line.removePrefix("pen position "))
                        index++
                    }
                    line.startsWith("pen pos ") -> {
                        out += parsePosition(line.removePrefix("pen pos "))
                        index++
                    }
                    line.startsWith("pos ") -> {
                        out += parsePosition(line.removePrefix("pos "))
                        index++
                    }

                    line.startsWith("let ") -> {
                        out += parseAssignment(line.removePrefix("let "))
                        index++
                    }
                    line.startsWith("set ") -> {
                        out += parseAssignment(line.removePrefix("set "))
                        index++
                    }

                    else -> error("Unknown command on line ${index + 1}: $line")
                }
            }

            if (!root) error("Missing } at end of block")
            return out
        }

        private fun generatedFrameCount(line: String): String? {
            Regex("""generate\s+(.+?)\s+frames\s*\{""").matchEntire(line)?.let {
                return it.groupValues[1].trim()
            }
            Regex("""generate\s+frames\s+(.+?)\s*\{""").matchEntire(line)?.let {
                return it.groupValues[1].trim()
            }
            Regex("""frames\s+(.+?)\s*\{""").matchEntire(line)?.let {
                return it.groupValues[1].trim()
            }
            return null
        }

        private fun parseAssignment(text: String): Stmt.Assign {
            val eq = text.indexOf('=')
            if (eq <= 0 || eq == text.lastIndex) error("Expected: let name = expression")
            val name = text.substring(0, eq).trim()
            if (!name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) error("Invalid variable name: $name")
            val expression = text.substring(eq + 1).trim()
            return Stmt.Assign(name, expression)
        }

        private fun parsePosition(text: String): Stmt.Position {
            val parts = splitTopLevelCommas(text)
            if (parts.size != 2) error("Position needs x, y separated by a comma")
            requireText(parts[0], "x position")
            requireText(parts[1], "y position")
            return Stmt.Position(parts[0], parts[1])
        }

        private fun parseColor(text: String): Stmt.Color {
            val parts = splitTopLevelCommas(text)
            if (parts.size != 3) error("Colour needs red, green, blue values separated by commas")
            requireText(parts[0], "red channel")
            requireText(parts[1], "green channel")
            requireText(parts[2], "blue channel")
            return Stmt.Color(parts[0], parts[1], parts[2])
        }

        private fun splitTopLevelCommas(text: String): List<String> {
            val parts = mutableListOf<String>()
            var depth = 0
            var start = 0
            text.forEachIndexed { i, ch ->
                when (ch) {
                    '(' -> depth++
                    ')' -> depth--
                    ',' -> if (depth == 0) {
                        parts += text.substring(start, i).trim()
                        start = i + 1
                    }
                }
            }
            parts += text.substring(start).trim()
            return parts
        }

        private fun requireText(value: String, name: String) {
            if (value.isBlank()) error("Missing $name")
        }
    }

    private class Condition(private val text: String, private val env: Map<String, Double>) {
        fun value(): Boolean = evaluate(text.trim())

        private fun evaluate(raw: String): Boolean {
            val text = stripOuterParentheses(raw.trim())

            findTopLevelLogical(text, "or", "||")?.let { (at, token) ->
                return evaluate(text.substring(0, at)) || evaluate(text.substring(at + token.length))
            }
            findTopLevelLogical(text, "and", "&&")?.let { (at, token) ->
                return evaluate(text.substring(0, at)) && evaluate(text.substring(at + token.length))
            }

            val operators = listOf(">=", "<=", "==", "!=", ">", "<")
            for (op in operators) {
                val at = findTopLevelOperator(text, op)
                if (at >= 0) {
                    val a = Expression(text.substring(0, at), env).value()
                    val b = Expression(text.substring(at + op.length), env).value()
                    return when (op) {
                        ">=" -> a >= b
                        "<=" -> a <= b
                        "==" -> abs(a - b) < 1e-9
                        "!=" -> abs(a - b) >= 1e-9
                        ">" -> a > b
                        else -> a < b
                    }
                }
            }
            return Expression(text, env).value() != 0.0
        }

        private fun stripOuterParentheses(value: String): String {
            var result = value
            while (result.length >= 2 && result.first() == '(' && result.last() == ')' && wrapsWholeExpression(result)) {
                result = result.substring(1, result.lastIndex).trim()
            }
            return result
        }

        private fun wrapsWholeExpression(value: String): Boolean {
            var depth = 0
            value.forEachIndexed { index, ch ->
                when (ch) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0 && index != value.lastIndex) return false
                        if (depth < 0) return false
                    }
                }
            }
            return depth == 0
        }

        private fun findTopLevelLogical(text: String, word: String, symbol: String): Pair<Int, String>? {
            var depth = 0
            var i = 0
            while (i < text.length) {
                when (text[i]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                if (depth == 0) {
                    if (text.startsWith(symbol, i)) return i to symbol
                    if (text.regionMatches(i, word, 0, word.length, ignoreCase = true)) {
                        val beforeOk = i == 0 || text[i - 1].isWhitespace()
                        val after = i + word.length
                        val afterOk = after == text.length || text[after].isWhitespace()
                        if (beforeOk && afterOk) return i to word
                    }
                }
                i++
            }
            return null
        }

        private fun findTopLevelOperator(text: String, op: String): Int {
            var depth = 0
            var i = 0
            while (i <= text.length - op.length) {
                when (text[i]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                if (depth == 0 && text.startsWith(op, i)) return i
                i++
            }
            return -1
        }
    }

    private class Expression(private val text: String, private val env: Map<String, Double>) {
        private var i = 0

        fun value(): Double {
            val result = expression()
            skipSpaces()
            if (i != text.length) error("Bad expression near '${text.substring(i)}'")
            if (!result.isFinite()) error("Expression produced a non-finite number")
            return result
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                value = when {
                    take('+') -> value + term()
                    take('-') -> value - term()
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = unary()
            while (true) {
                skipSpaces()
                value = when {
                    take('*') -> value * unary()
                    take('/') -> value / unary()
                    take('%') -> value % unary()
                    else -> return value
                }
            }
        }

        private fun unary(): Double {
            skipSpaces()
            return when {
                take('+') -> unary()
                take('-') -> -unary()
                else -> primary()
            }
        }

        private fun primary(): Double {
            skipSpaces()
            if (take('(')) {
                val value = expression()
                skipSpaces()
                if (!take(')')) error("Missing )")
                return value
            }

            if (i < text.length && (text[i].isDigit() || text[i] == '.')) return number()

            val name = identifier()
            skipSpaces()
            if (take('(')) {
                val arg = expression()
                skipSpaces()
                if (!take(')')) error("Missing ) after $name")
                return function(name, arg)
            }
            return env[name] ?: error("Unknown variable: $name")
        }

        private fun number(): Double {
            val start = i
            while (i < text.length && (text[i].isDigit() || text[i] == '.')) i++
            return text.substring(start, i).toDoubleOrNull() ?: error("Invalid number")
        }

        private fun identifier(): String {
            skipSpaces()
            val start = i
            if (i >= text.length || !(text[i].isLetter() || text[i] == '_')) error("Expected a number or variable")
            i++
            while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
            return text.substring(start, i)
        }

        private fun function(name: String, value: Double): Double = when (name.lowercase()) {
            "sin" -> sin(Math.toRadians(value))
            "cos" -> cos(Math.toRadians(value))
            "tan" -> tan(Math.toRadians(value))
            "sqrt" -> sqrt(value)
            "abs" -> abs(value)
            else -> error("Unknown function: $name")
        }

        private fun skipSpaces() {
            while (i < text.length && text[i].isWhitespace()) i++
        }

        private fun take(ch: Char): Boolean {
            if (i < text.length && text[i] == ch) {
                i++
                return true
            }
            return false
        }
    }
}
