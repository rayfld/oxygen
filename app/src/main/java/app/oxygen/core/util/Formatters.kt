package app.oxygen.core.util
import java.util.Locale
import kotlin.math.abs
object Fmt {
    fun cost(usd: Double?): String {
        if (usd == null) return "-"
        if (usd == 0.0) return "$0"
        return when {
            usd < 0.0001 -> "<$0.0001"
            usd < 0.01 -> "$" + trim(usd, 4)
            usd < 1 -> "$" + trimMin(usd, 3, 2)
            usd < 100 -> "$" + String.format(Locale.US, "%.2f", usd)
            else -> "$" + String.format(Locale.US, "%.0f", usd)
        }
    }
    private fun trim(v: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", v).trimEnd('0').trimEnd('.').ifEmpty { "0" }
    private fun trimMin(v: Double, maxDecimals: Int, minDecimals: Int): String {
        var text = String.format(Locale.US, "%.${maxDecimals}f", v)
        while (text.length - text.indexOf('.') - 1 > minDecimals && text.endsWith("0")) {
            text = text.dropLast(1)
        }
        return text
    }
    fun tokens(n: Int?): String {
        if (n == null) return "-"
        return when {
            n < 1000 -> n.toString()
            n < 10_000 -> String.format(Locale.US, "%.1fk", n / 1000.0).replace(".0k", "k")
            n < 1_000_000 -> "${n / 1000}k"
            else -> String.format(Locale.US, "%.1fM", n / 1_000_000.0).replace(".0M", "M")
        }
    }
    fun pricePerM(perToken: Double?): String {
        if (perToken == null) return "-"
        val perM = perToken * 1_000_000.0
        return when {
            perM == 0.0 -> "Free"
            perM < 0.01 -> "$" + String.format(Locale.US, "%.4f", perM)
            perM < 1 -> "$" + String.format(Locale.US, "%.3f", perM).trimEnd('0').trimEnd('.')
            perM < 100 -> "$" + String.format(Locale.US, "%.2f", perM).trimEnd('0').trimEnd('.')
            else -> "$" + String.format(Locale.US, "%.0f", perM)
        }
    }
    fun context(n: Int?): String {
        if (n == null) return "-"
        return when {
            n < 1000 -> n.toString()
            n < 1_000_000 -> "${n / 1000}K"
            else -> String.format(Locale.US, "%.1fM", n / 1_000_000.0).replace(".0M", "M")
        }
    }
    fun duration(ms: Long?): String {
        if (ms == null) return "-"
        return when {
            ms < 1000 -> "${ms}ms"
            ms < 60_000 -> String.format(Locale.US, "%.1fs", ms / 1000.0)
            else -> "${ms / 60_000}m ${(ms % 60_000) / 1000}s"
        }
    }
    fun relativeTime(ts: Long, now: Long = System.currentTimeMillis()): String {
        val d = abs(now - ts)
        return when {
            d < 60_000 -> "now"
            d < 3_600_000 -> "${d / 60_000}m"
            d < 86_400_000 -> "${d / 3_600_000}h"
            d < 7 * 86_400_000L -> "${d / 86_400_000}d"
            d < 365 * 86_400_000L -> {
                val days = (d / 86_400_000L).toInt()
                when {
                    days < 30 -> "${days / 7}w"
                    else -> "${days / 30}mo"
                }
            }
            else -> "${d / (365 * 86_400_000L)}y"
        }
    }
    fun dateTime(ts: Long): String =
        java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
    fun dayLabel(ts: Long): String =
        java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(ts))
    fun tokensPerSecond(completionTokens: Int?, latencyMs: Long?): String? {
        if (completionTokens == null || latencyMs == null || latencyMs <= 0) return null
        val tps = completionTokens / (latencyMs / 1000.0)
        if (tps <= 0) return null
        return String.format(Locale.US, "%.0f", tps)
    }
}
object Brand {
    fun authorLabel(author: String): String {
        val cleaned = author.removePrefix("~")
        return cleaned.replaceFirstChar { it.uppercaseChar() }
    }
}
