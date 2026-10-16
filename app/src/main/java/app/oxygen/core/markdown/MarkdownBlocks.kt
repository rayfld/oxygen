package app.oxygen.core.markdown
sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class ListBlock(
        val items: List<String>,
        val ordered: Boolean,
        val start: Int,
    ) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val language: String?, val code: String, val closed: Boolean) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Divider : MdBlock
}
object MarkdownBlocks {
    private val headingRegex = Regex("^(#{1,6})\\s+(.*)$")
    private val bulletRegex = Regex("^\\s*([-*+])\\s+(.*)$")
    private val orderedRegex = Regex("^\\s*(\\d{1,4})[.)]\\s+(.*)$")
    private val fenceRegex = Regex("^\\s*(```+|~~~+)\\s*(.*)$")
    private val dividerRegex = Regex("^\\s*([-*_])\\s*(\\1\\s*){2,}$")
    private val quoteRegex = Regex("^\\s*>\\s?(.*)$")
    private val tableSeparatorRegex = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")
    fun parse(text: String): List<MdBlock> {
        if (text.isBlank()) return emptyList()
        val blocks = ArrayList<MdBlock>()
        val lines = text.split("\n")
        var i = 0
        fun nextIsBlank(index: Int) = index >= lines.size || lines[index].isBlank()
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                i++
                continue
            }
            val fence = fenceRegex.find(line)
            if (fence != null) {
                val marker = fence.groupValues[1].trim()
                val language = fence.groupValues[2].trim().substringBefore(" ").take(24).ifBlank { null }
                val code = StringBuilder()
                var j = i + 1
                var closed = false
                while (j < lines.size) {
                    val candidate = lines[j]
                    if (candidate.trimStart().startsWith(marker) &&
                        candidate.trimStart().all { it == marker[0] }
                    ) {
                        closed = true
                        break
                    }
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(candidate)
                    j++
                }
                blocks += MdBlock.Code(language = language, code = code.toString(), closed = closed)
                i = if (closed) j + 1 else lines.size
                continue
            }
            headingRegex.find(line)?.let { match ->
                blocks += MdBlock.Heading(
                    level = match.groupValues[1].length,
                    text = match.groupValues[2].trim(),
                )
                i++
                return@let
            } ?: run {
                if (dividerRegex.matches(line) && line.trim().length >= 3) {
                    blocks += MdBlock.Divider
                    i++
                    continue
                }
                if (line.contains('|') && i + 1 < lines.size && tableSeparatorRegex.matches(lines[i + 1])) {
                    val header = splitTableRow(line)
                    val rows = ArrayList<List<String>>()
                    var j = i + 2
                    while (j < lines.size && lines[j].contains('|') && lines[j].isNotBlank()) {
                        rows += splitTableRow(lines[j])
                        j++
                    }
                    blocks += MdBlock.Table(header = header, rows = rows)
                    i = j
                    continue
                }
                if (quoteRegex.matches(line)) {
                    val quote = StringBuilder()
                    var j = i
                    while (j < lines.size) {
                        val match = quoteRegex.find(lines[j]) ?: break
                        if (quote.isNotEmpty()) quote.append('\n')
                        quote.append(match.groupValues[1])
                        j++
                    }
                    blocks += MdBlock.Quote(quote.toString())
                    i = j
                    continue
                }
                val bullet = bulletRegex.find(line)
                val ordered = orderedRegex.find(line)
                if (bullet != null || ordered != null) {
                    val isOrdered = ordered != null
                    val items = ArrayList<String>()
                    val start = ordered?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    var j = i
                    while (j < lines.size) {
                        val candidate = lines[j]
                        val item = if (isOrdered) orderedRegex.find(candidate) else bulletRegex.find(candidate)
                        val otherMarker = if (isOrdered) bulletRegex.find(candidate) else orderedRegex.find(candidate)
                        if (item == null) {
                            if (otherMarker == null && candidate.isNotBlank() && !headingRegex.matches(candidate) &&
                                fenceRegex.find(candidate) == null && !candidate.trimStart().startsWith(">") &&
                                !candidate.contains('|')
                            ) {
                                if (items.isNotEmpty()) items[items.size - 1] = items.last() + " " + candidate.trim()
                                j++
                                continue
                            }
                            break
                        }
                        items += item.groupValues[2].trim()
                        j++
                    }
                    blocks += MdBlock.ListBlock(items = items, ordered = isOrdered, start = start)
                    i = j
                    continue
                }
                val paragraph = StringBuilder(line.trim())
                var j = i + 1
                while (j < lines.size && lines[j].isNotBlank() &&
                    headingRegex.find(lines[j]) == null &&
                    fenceRegex.find(lines[j]) == null &&
                    !bulletRegex.matches(lines[j]) &&
                    !orderedRegex.matches(lines[j]) &&
                    !quoteRegex.matches(lines[j]) &&
                    lines[j].trim() != "---"
                ) {
                    paragraph.append('\n').append(lines[j].trim())
                    j++
                }
                blocks += MdBlock.Paragraph(paragraph.toString())
                i = j
                continue
            }
        }
        return blocks
    }
    private fun splitTableRow(line: String): List<String> =
        line.trim().trimStart('|').trimEnd('|').split('|').map { it.trim() }
    fun plainText(blocks: List<MdBlock>): String = buildString {
        for (block in blocks) {
            when (block) {
                is MdBlock.Paragraph -> appendLine(block.text)
                is MdBlock.Heading -> appendLine(block.text)
                is MdBlock.ListBlock -> block.items.forEachIndexed { index, item ->
                    appendLine(if (block.ordered) "${block.start + index}. $item" else "• $item")
                }
                is MdBlock.Quote -> appendLine(block.text)
                is MdBlock.Code -> appendLine(block.code)
                is MdBlock.Table -> {
                    appendLine(block.header.joinToString(" | "))
                    block.rows.forEach { appendLine(it.joinToString(" | ")) }
                }
                is MdBlock.Divider -> appendLine("---")
            }
        }
    }.trim()
}
class StreamingMarkdownParser {
    private var lastText = ""
    private var stableBlocks: List<MdBlock> = emptyList()
    private var stableOffset = 0
    @Synchronized
    fun update(text: String): List<MdBlock> {
        if (text == lastText) return stableBlocks + parseTail()
        if (!text.startsWith(lastText)) {
            stableBlocks = emptyList()
            stableOffset = 0
        }
        lastText = text
        if (text.isEmpty()) return emptyList()
        val boundary = findStableBoundary(text, stableOffset)
        if (boundary > stableOffset) {
            stableBlocks = stableBlocks + MarkdownBlocks.parse(text.substring(stableOffset, boundary))
            stableOffset = boundary
        }
        return stableBlocks + parseTail()
    }
    private fun parseTail(): List<MdBlock> {
        if (stableOffset >= lastText.length) return emptyList()
        return MarkdownBlocks.parse(lastText.substring(stableOffset))
    }
    private fun findStableBoundary(text: String, from: Int): Int {
        var i = from
        var inFence = false
        var fenceChar = ' '
        var lastBoundary = from
        while (i < text.length) {
            val lineStart = i == 0 || text[i - 1] == '\n'
            if (lineStart && i + 2 < text.length &&
                (text.startsWith("```", i) || text.startsWith("~~~", i))
            ) {
                val char = text[i]
                if (!inFence) {
                    inFence = true
                    fenceChar = char
                } else if (char == fenceChar) {
                    inFence = false
                }
                while (i < text.length && text[i] != '\n') i++
                continue
            }
            if (!inFence && i + 1 < text.length && text[i] == '\n' && text[i + 1] == '\n') {
                var end = i + 2
                while (end < text.length && text[end] == '\n') end++
                lastBoundary = end
                i = end
                continue
            }
            i++
        }
        return lastBoundary
    }
}
