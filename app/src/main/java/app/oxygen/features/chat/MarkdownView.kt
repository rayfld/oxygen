package app.oxygen.features.chat
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.oxygen.core.markdown.CodeHighlighter
import app.oxygen.core.markdown.InlineMarkdown
import app.oxygen.core.markdown.MdBlock
import app.oxygen.core.markdown.StreamingMarkdownParser
import app.oxygen.ui.components.OxIconButton
import app.oxygen.ui.components.isDark
import app.oxygen.ui.theme.Ox
import app.oxygen.ui.theme.LocalChatFontScale
import app.oxygen.ui.theme.OxType
@Composable
fun MarkdownContent(
    markdown: String,
    streaming: Boolean,
    modifier: Modifier = Modifier,
) {
    val blocks = if (streaming) {
        val parser = remember { StreamingMarkdownParser() }
        parser.update(markdown)
    } else {
        remember(markdown) { app.oxygen.core.markdown.MarkdownBlocks.parse(markdown) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEachIndexed { index, block ->
            BlockView(
                block = block,
                fadeTail = streaming && index == blocks.lastIndex,
            )
        }
    }
}
@Composable
private fun BlockView(block: MdBlock, fadeTail: Boolean) {
    val c = Ox.colors
    val scale = LocalChatFontScale.current
    val bodyStyle = OxType.bodyLarge.copy(
        fontSize = (16f * scale).sp,
        lineHeight = (23f * scale).sp,
    )
    when (block) {
        is MdBlock.Paragraph -> {
            val text = remember(block.text, c) { InlineMarkdown.parse(block.text, c) }
            val styled = if (fadeTail && text.isNotEmpty()) {
                buildAnnotatedString {
                    append(text)
                    val start = (text.length - 32).coerceAtLeast(0)
                    val span = text.length - start
                    for (i in start until text.length) {
                        val t = (i - start).toFloat() / (span - 1).coerceAtLeast(1)
                        val alpha = (1f - 0.45f * t).coerceIn(0.45f, 1f)
                        addStyle(SpanStyle(color = c.text.copy(alpha = alpha)), i, i + 1)
                    }
                }
            } else {
                text
            }
            Text(styled, style = bodyStyle, color = c.text)
        }
        is MdBlock.Heading -> {
            val text = remember(block.text, c) { InlineMarkdown.parse(block.text, c) }
            val style = when (block.level) {
                1 -> OxType.headline.copy(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)
                2 -> OxType.titleLarge
                else -> OxType.title
            }
            Text(
                text,
                style = if (block.level == 1) OxType.headline else style,
                color = c.text,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        is MdBlock.ListBlock -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.items.forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        val marker = if (block.ordered) "${block.start + index}." else "•"
                        Text(
                            marker,
                            style = bodyStyle,
                            color = c.textMuted,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(
                            remember(item, c) { InlineMarkdown.parse(item, c) },
                            style = bodyStyle,
                            color = c.text,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        is MdBlock.Quote -> {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(c.borderStrong),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    remember(block.text, c) { InlineMarkdown.parse(block.text, c) },
                    style = bodyStyle,
                    color = c.textSecondary,
                    modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                )
            }
        }
        is MdBlock.Code -> CodeBlockView(block)
        is MdBlock.Table -> TableView(block)
        MdBlock.Divider -> Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .height(1.dp)
                .background(c.border),
        )
    }
}
@Composable
fun CodeBlockView(block: MdBlock.Code) {
    val c = Ox.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val dark = isDark(c)
    val shape = RoundedCornerShape(12.dp)
    val annotated = remember(block.code, block.language, c, dark) {
        CodeHighlighter.annotate(block.code, block.language, c, dark)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.codeBackground)
            .border(1.dp, c.border, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                block.language ?: "code",
                style = OxType.codeSmall,
                color = c.textMuted,
                modifier = Modifier.weight(1f),
            )
            OxIconButton(
                Icons.Rounded.ContentCopy,
                contentDescription = "Copy code",
                size = 30.dp,
                iconSize = 15.dp,
                tint = c.textMuted,
                onClick = {
                    clipboard.setText(AnnotatedString(block.code))
                    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                },
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        SelectionContainer {
            Text(
                annotated,
                style = OxType.code,
                color = c.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}
@Composable
private fun TableView(block: MdBlock.Table) {
    val c = Ox.colors
    val shape = RoundedCornerShape(12.dp)
    val scrollState = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = maxWidth
        val columns = block.header.size.coerceAtLeast(1)
        val naturalWidths = remember(block) {
            List(columns) { index ->
                var maxLen = block.header.getOrNull(index)?.length ?: 0
                for (row in block.rows) {
                    val length = row.getOrNull(index)?.length ?: 0
                    if (length > maxLen) maxLen = length
                }
                (maxLen * 7).coerceIn(84, 230).dp
            }
        }
        val total = naturalWidths.fold(0.dp) { acc, width -> acc + width }
        val widths = if (total < available) {
            val extra = (available - total) / columns
            naturalWidths.map { it + extra }
        } else {
            naturalWidths
        }
        val tableWidth = widths.fold(0.dp) { acc, width -> acc + width }
        Column(
            Modifier
                .clip(shape)
                .border(1.dp, c.border, shape)
                .horizontalScroll(scrollState),
        ) {
            Row(
                Modifier
                    .width(tableWidth)
                    .background(c.container)
                    .padding(vertical = 8.dp),
            ) {
                block.header.forEachIndexed { index, cell ->
                    Text(
                        cell,
                        style = OxType.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = c.text,
                        modifier = Modifier
                            .width(widths.getOrNull(index) ?: 120.dp)
                            .padding(horizontal = 10.dp),
                    )
                }
            }
            block.rows.forEachIndexed { rowIndex, row ->
                Box(
                    Modifier
                        .width(tableWidth)
                        .height(1.dp)
                        .background(c.border.copy(alpha = 0.6f)),
                )
                Row(
                    Modifier
                        .width(tableWidth)
                        .then(
                            if (rowIndex % 2 == 1) {
                                Modifier.background(c.container.copy(alpha = 0.35f))
                            } else {
                                Modifier
                            },
                        )
                        .padding(vertical = 7.dp),
                ) {
                    for (index in block.header.indices) {
                        Text(
                            row.getOrNull(index).orEmpty(),
                            style = OxType.bodySmall,
                            color = c.textSecondary,
                            modifier = Modifier
                                .width(widths.getOrNull(index) ?: 120.dp)
                                .padding(horizontal = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
fun markdownToPlainText(markdown: String): String =
    app.oxygen.core.markdown.MarkdownBlocks.plainText(app.oxygen.core.markdown.MarkdownBlocks.parse(markdown))
