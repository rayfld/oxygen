package app.oxygen.core.markdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class MarkdownBlocksTest {
    @Test
    fun `parses headings paragraphs and dividers`() {
        val blocks = MarkdownBlocks.parse(
            """
            # Title
            Some **bold** text.
            ---
            Last paragraph.
            """.trimIndent(),
        )
        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is MdBlock.Heading)
        assertEquals(1, (blocks[0] as MdBlock.Heading).level)
        assertTrue(blocks[1] is MdBlock.Paragraph)
        assertTrue(blocks[2] is MdBlock.Divider)
        assertTrue(blocks[3] is MdBlock.Paragraph)
    }
    @Test
    fun `parses fenced code with language and preserves blank lines`() {
        val blocks = MarkdownBlocks.parse(
            """
            Intro.
            ```kotlin
            fun main() {
                println("hi")
            }
            ```
            Outro.
            """.trimIndent(),
        )
        assertEquals(3, blocks.size)
        val code = blocks[1] as MdBlock.Code
        assertEquals("kotlin", code.language)
        assertTrue(code.closed)
        assertTrue(code.code.contains("println(\"hi\")"))
    }
    @Test
    fun `unclosed fence marks code as open`() {
        val blocks = MarkdownBlocks.parse("```py\nprint(1)")
        val code = blocks.single() as MdBlock.Code
        assertEquals("py", code.language)
        assertEquals(false, code.closed)
    }
    @Test
    fun `groups list items and keeps ordered start`() {
        val blocks = MarkdownBlocks.parse(
            """
            3. three
            4. four
            - a
            - b
            """.trimIndent(),
        )
        val ordered = blocks[0] as MdBlock.ListBlock
        assertTrue(ordered.ordered)
        assertEquals(3, ordered.start)
        assertEquals(listOf("three", "four"), ordered.items)
        val bullets = blocks[1] as MdBlock.ListBlock
        assertEquals(false, bullets.ordered)
        assertEquals(2, bullets.items.size)
    }
    @Test
    fun `parses tables with separator row`() {
        val blocks = MarkdownBlocks.parse(
            """
            | Model | Price |
            |-------|-------|
            | a     | ${'$'}1    |
            | b     | ${'$'}2    |
            """.trimIndent(),
        )
        val table = blocks.single() as MdBlock.Table
        assertEquals(listOf("Model", "Price"), table.header)
        assertEquals(2, table.rows.size)
    }
    @Test
    fun `streaming parser only re-parses the tail`() {
        val parser = StreamingMarkdownParser()
        val step1 = parser.update("First paragraph.")
        assertEquals(1, step1.size)
        val step2 = parser.update("First paragraph.\n\nSecond block is being written")
        assertEquals(2, step2.size)
        assertEquals("First paragraph.", (step2[0] as MdBlock.Paragraph).text)
        val step3 = parser.update("First paragraph.\n\nSecond block is being written\n\nThird.")
        assertEquals(3, step3.size)
        assertTrue(step3[0] is MdBlock.Paragraph)
        assertTrue(step3[2] is MdBlock.Paragraph)
    }
    @Test
    fun `streaming parser freezes code fences correctly`() {
        val parser = StreamingMarkdownParser()
        parser.update("Hello.\n\n```kt\nval x = 1")
        val withOpenFence = parser.update("Hello.\n\n```kt\nval x = 1\nval y = 2\n")
        assertEquals(2, withOpenFence.size)
        assertTrue(withOpenFence[1] is MdBlock.Code)
        val closed = parser.update("Hello.\n\n```kt\nval x = 1\nval y = 2\n```\n\nDone.")
        assertEquals(3, closed.size)
        assertTrue((closed[1] as MdBlock.Code).closed)
        assertEquals("Done.", (closed[2] as MdBlock.Paragraph).text)
    }
    @Test
    fun `plain text strips markdown chrome`() {
        val blocks = MarkdownBlocks.parse("# Title\n\n- one\n- two\n\n```\ncode\n```")
        val text = MarkdownBlocks.plainText(blocks)
        assertTrue(text.contains("Title"))
        assertTrue(text.contains("• one"))
        assertTrue(text.contains("code"))
    }
}
