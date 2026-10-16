package app.oxygen.core.markdown
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import app.oxygen.ui.theme.OxType
import app.oxygen.ui.theme.OxygenColors
object InlineMarkdown {
    fun parse(text: String, colors: OxygenColors): AnnotatedString = buildAnnotatedString {
        appendInline(this, text, colors, depth = 0)
    }
    private fun appendInline(
        builder: AnnotatedString.Builder,
        text: String,
        colors: OxygenColors,
        depth: Int,
    ) {
        if (depth > 6) {
            builder.append(text)
            return
        }
        var i = 0
        val n = text.length
        while (i < n) {
            val ch = text[i]
            when {
                ch == '\\' && i + 1 < n -> {
                    builder.append(text[i + 1])
                    i += 2
                }
                ch == '`' -> {
                    val run = countRun(text, i, '`')
                    val close = findClosing(text, i + run, if (run >= 2) "``" else "`")
                    if (close >= 0) {
                        val inner = text.substring(i + run, close).trim()
                        builder.withStyle(
                            SpanStyle(
                                fontFamily = OxType.code.fontFamily,
                                fontSize = OxType.code.fontSize,
                                background = colors.codeBackground,
                                color = colors.text,
                            ),
                        ) { append(inner) }
                        i = close + if (run >= 2) 2 else 1
                    } else {
                        builder.append(text, i, minOf(i + run, n))
                        i += run
                    }
                }
                (ch == '*' || ch == '_') -> {
                    val run = countRun(text, i, ch)
                    if (run >= 2) {
                        val close = findClosing(text, i + run, "" + ch + ch)
                        if (close >= 0) {
                            builder.withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                appendInline(builder, text.substring(i + run, close), colors, depth + 1)
                            }
                            i = close + 2
                        } else {
                            builder.append(ch)
                            i++
                        }
                    } else {
                        val close = findClosingChar(text, i + 1, ch)
                        if (close >= 0 && close > i + 1) {
                            builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                appendInline(builder, text.substring(i + 1, close), colors, depth + 1)
                            }
                            i = close + 1
                        } else {
                            builder.append(ch)
                            i++
                        }
                    }
                }
                ch == '~' && i + 1 < n && text[i + 1] == '~' -> {
                    val close = findClosing(text, i + 2, "~~")
                    if (close >= 0) {
                        builder.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            appendInline(builder, text.substring(i + 2, close), colors, depth + 1)
                        }
                        i = close + 2
                    } else {
                        builder.append(ch)
                        i++
                    }
                }
                ch == '[' -> {
                    val labelEnd = text.indexOf(']', i + 1)
                    if (labelEnd > i && labelEnd + 1 < n && text[labelEnd + 1] == '(') {
                        val urlEnd = findClosingChar(text, labelEnd + 2, ')')
                        if (urlEnd > 0) {
                            val label = text.substring(i + 1, labelEnd)
                            val url = text.substring(labelEnd + 2, urlEnd).trim().substringBefore(' ')
                            if (url.isNotBlank()) {
                                builder.appendLink(label.ifBlank { url }, url, colors, depth)
                                i = urlEnd + 1
                                continue
                            }
                        }
                    }
                    builder.append(ch)
                    i++
                }
                (ch == 'h' || ch == 'w') && (i == 0 || text[i - 1].isWhitespace() || text[i - 1] == '(') -> {
                    val url = matchUrl(text, i)
                    if (url != null) {
                        val end = i + url.length
                        var visible = url
                        if (end < n && text[end] == ')') {
                        }
                        builder.appendLink(visible, url, colors, depth)
                        i = end
                    } else {
                        builder.append(ch)
                        i++
                    }
                }
                else -> {
                    builder.append(ch)
                    i++
                }
            }
        }
    }
    private fun AnnotatedString.Builder.appendLink(
        label: String,
        url: String,
        colors: OxygenColors,
        depth: Int,
    ) {
        withLink(
            LinkAnnotation.Url(
                url = url,
                styles = TextLinkStyles(
                    style = SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline),
                ),
            ),
        ) {
            append(label)
        }
    }
    private fun matchUrl(text: String, start: Int): String? {
        if (text.startsWith("http://", start) || text.startsWith("https://", start)) {
            var end = start
            while (end < text.length && !text[end].isWhitespace() && text[end] != ')' && text[end] != '>' && text[end] != '"') end++
            var url = text.substring(start, end)
            while (url.isNotEmpty() && url.last() in ".,;:!?") url = url.dropLast(1)
            return url.takeIf { it.length > 8 }
        }
        return null
    }
    private fun countRun(text: String, start: Int, ch: Char): Int {
        var count = 0
        while (start + count < text.length && text[start + count] == ch) count++
        return count
    }
    private fun findClosing(text: String, from: Int, marker: String): Int {
        var index = from
        while (index < text.length) {
            val found = text.indexOf(marker, index)
            if (found < 0) return -1
            if (found > 0 && text[found - 1] == '\n') return -1
            return found
        }
        return -1
    }
    private fun findClosingChar(text: String, from: Int, ch: Char): Int {
        val found = text.indexOf(ch, from)
        return if (found > 0 && text[found - 1] == '\n' && ch == 'e') -1 else found
    }
}
