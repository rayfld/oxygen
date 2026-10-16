package app.oxygen.core.tools
import android.util.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
object LocalToolExecutor {
    fun execute(toolId: String, argumentsJson: String): String {
        val args = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(argumentsJson) as? JsonObject
        }.getOrNull() ?: JsonObject(emptyMap())
        return try {
            when (toolId) {
                "datetime" -> dateTime()
                "calculator" -> calculator(args.str("expression"))
                "random" -> random(args)
                "uuid" -> UUID.randomUUID().toString()
                "base64" -> base64(args)
                "hash" -> hash(args)
                "convert" -> convert(args)
                "text_stats" -> textStats(args.str("text"))
                "days_between" -> daysBetween(args.str("start"), args.str("end"))
                else -> "Error: unknown local tool $toolId"
            }
        } catch (e: Exception) {
            "Error: ${e.message ?: "tool failed"}"
        }
    }
    private fun dateTime(): String {
        val now = Date()
        val local = SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz (EEEE)", Locale.US)
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val zone = TimeZone.getDefault()
        return "Local: ${local.format(now)}\nISO: ${iso.format(now)}\nZone: ${zone.id}\nEpoch ms: ${now.time}"
    }
    private fun calculator(expression: String?): String {
        if (expression.isNullOrBlank()) return "Error: expression required"
        val value = ExprParser(expression).parse()
        return formatNumber(value)
    }
    private fun formatNumber(value: Double): String {
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
        if (value == floor(value) && abs(value) < 1e15) {
            return value.toLong().toString()
        }
        val rounded = String.format(Locale.US, "%.10f", value).trimEnd('0').trimEnd('.')
        return rounded
    }
    private class ExprParser(private val input: String) {
        private var pos = 0
        fun parse(): Double {
            val result = expression()
            skipSpaces()
            if (pos < input.length) throw IllegalArgumentException("Unexpected character '${input[pos]}'")
            return result
        }
        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                val ch = peek() ?: return value
                value = when (ch) {
                    '+' -> { pos++; value + term() }
                    '-' -> { pos++; value - term() }
                    else -> return value
                }
            }
        }
        private fun term(): Double {
            var value = power()
            while (true) {
                skipSpaces()
                val ch = peek() ?: return value
                value = when (ch) {
                    '*' -> { pos++; value * power() }
                    '/' -> { pos++; value / power() }
                    '%' -> { pos++; value % power() }
                    else -> return value
                }
            }
        }
        private fun power(): Double {
            val base = unary()
            skipSpaces()
            return if (peek() == '^') {
                pos++
                base.pow(power())
            } else {
                base
            }
        }
        private fun unary(): Double {
            skipSpaces()
            return when (peek()) {
                '-' -> { pos++; -unary() }
                '+' -> { pos++; unary() }
                else -> primary()
            }
        }
        private fun primary(): Double {
            skipSpaces()
            val ch = peek() ?: throw IllegalArgumentException("Unexpected end of expression")
            if (ch == '(') {
                pos++
                val value = expression()
                skipSpaces()
                if (peek() != ')') throw IllegalArgumentException("Missing ')'")
                pos++
                return value
            }
            if (ch.isDigit() || ch == '.') return number()
            if (ch.isLetter()) return functionOrConstant()
            throw IllegalArgumentException("Unexpected character '$ch'")
        }
        private fun number(): Double {
            val start = pos
            while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
            return input.substring(start, pos).toDoubleOrNull()
                ?: throw IllegalArgumentException("Bad number at $start")
        }
        private fun functionOrConstant(): Double {
            val start = pos
            while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_')) pos++
            val name = input.substring(start, pos).lowercase()
            when (name) {
                "pi" -> return Math.PI
                "e" -> return Math.E
            }
            skipSpaces()
            if (peek() != '(') throw IllegalArgumentException("Expected '(' after $name")
            pos++
            val args = ArrayList<Double>()
            args += expression()
            skipSpaces()
            while (peek() == ',') {
                pos++
                args += expression()
                skipSpaces()
            }
            if (peek() != ')') throw IllegalArgumentException("Missing ')' for $name")
            pos++
            return when (name) {
                "sqrt" -> sqrt(args[0])
                "sin" -> sin(args[0])
                "cos" -> cos(args[0])
                "tan" -> tan(args[0])
                "log" -> log10(args[0])
                "ln" -> ln(args[0])
                "abs" -> abs(args[0])
                "round" -> round(args[0])
                "floor" -> floor(args[0])
                "ceil" -> ceil(args[0])
                "min" -> args.min()
                "max" -> args.max()
                else -> throw IllegalArgumentException("Unknown function $name")
            }
        }
        private fun skipSpaces() {
            while (pos < input.length && input[pos].isWhitespace()) pos++
        }
        private fun peek(): Char? = if (pos < input.length) input[pos] else null
    }
    private fun random(args: JsonObject): String {
        val kind = args.str("kind")?.lowercase() ?: "number"
        return when (kind) {
            "coin" -> if ((0..1).random() == 0) "Heads" else "Tails"
            "dice" -> {
                val sides = args["sides"]?.jsonPrimitive?.intOrNull ?: 6
                "Rolled ${(1..sides.coerceAtLeast(2)).random()} on a d${sides.coerceAtLeast(2)}"
            }
            "pick" -> {
                val options = (args["options"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content }
                    .orEmpty()
                if (options.isEmpty()) "Error: options required" else options.random()
            }
            else -> {
                val minValue = args["min"]?.jsonPrimitive?.doubleOrNull ?: 1.0
                val maxValue = args["max"]?.jsonPrimitive?.doubleOrNull ?: 100.0
                val low = min(minValue, maxValue)
                val high = max(minValue, maxValue)
                val value = low + Math.random() * (high - low)
                if (low == floor(low) && high == floor(high)) {
                    (low.toLong()..high.toLong()).random().toString()
                } else {
                    formatNumber(value)
                }
            }
        }
    }
    private fun base64(args: JsonObject): String {
        val mode = args.str("mode")?.lowercase() ?: "encode"
        val text = args.str("text") ?: return "Error: text required"
        return if (mode == "decode") {
            String(Base64.decode(text.trim(), Base64.DEFAULT), Charsets.UTF_8)
        } else {
            Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }
    private fun hash(args: JsonObject): String {
        val algorithm = when (args.str("algorithm")?.lowercase()) {
            "md5" -> "MD5"
            else -> "SHA-256"
        }
        val text = args.str("text") ?: return "Error: text required"
        val digest = MessageDigest.getInstance(algorithm).digest(text.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "$algorithm: $hex"
    }
    private val lengthUnits = mapOf(
        "mm" to 0.001, "cm" to 0.01, "m" to 1.0, "km" to 1000.0,
        "in" to 0.0254, "ft" to 0.3048, "yd" to 0.9144, "mi" to 1609.344,
    )
    private val massUnits = mapOf(
        "mg" to 1e-6, "g" to 0.001, "kg" to 1.0, "t" to 1000.0,
        "oz" to 0.028349523125, "lb" to 0.45359237,
    )
    private val dataUnits = mapOf(
        "b" to 1.0, "kb" to 1024.0, "mb" to 1024.0 * 1024, "gb" to 1024.0 * 1024 * 1024,
        "tb" to 1024.0 * 1024 * 1024 * 1024,
    )
    private fun convert(args: JsonObject): String {
        val value = args["value"]?.jsonPrimitive?.doubleOrNull
            ?: return "Error: numeric value required"
        val from = args.str("from")?.lowercase()?.trim() ?: return "Error: from unit required"
        val to = args.str("to")?.lowercase()?.trim() ?: return "Error: to unit required"
        val temperature = convertTemperature(value, from, to)
        if (temperature != null) return "${formatNumber(temperature)} $to"
        val table = listOf(lengthUnits, massUnits, dataUnits)
            .firstOrNull { from in it && to in it }
            ?: return "Error: cannot convert $from to $to"
        val result = value * table.getValue(from) / table.getValue(to)
        return "${formatNumber(result)} $to"
    }
    private fun convertTemperature(value: Double, from: String, to: String): Double? {
        val normalizedFrom = if (from == "celsius") "c" else if (from == "fahrenheit") "f" else from
        val normalizedTo = if (to == "celsius") "c" else if (to == "fahrenheit") "f" else to
        if (normalizedFrom !in listOf("c", "f", "k") || normalizedTo !in listOf("c", "f", "k")) return null
        val celsius = when (normalizedFrom) {
            "c" -> value
            "f" -> (value - 32) * 5 / 9
            else -> value - 273.15
        }
        return when (normalizedTo) {
            "c" -> celsius
            "f" -> celsius * 9 / 5 + 32
            else -> celsius + 273.15
        }
    }
    private fun textStats(text: String?): String {
        if (text == null) return "Error: text required"
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        val lines = if (text.isEmpty()) 0 else text.split("\n").size
        val sentences = Regex("[.!?]+").findAll(text).count().coerceAtLeast(if (text.isBlank()) 0 else 1)
        return "Characters: ${text.length}\nCharacters (no spaces): ${text.count { !it.isWhitespace() }}\nWords: $words\nLines: $lines\nSentences: $sentences"
    }
    private fun daysBetween(start: String?, end: String?): String {
        if (start.isNullOrBlank() || end.isNullOrBlank()) return "Error: start and end dates required"
        val startDate = LocalDate.parse(start.trim())
        val endDate = LocalDate.parse(end.trim())
        val days = ChronoUnit.DAYS.between(startDate, endDate)
        return "$days days"
    }
    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.content
}
