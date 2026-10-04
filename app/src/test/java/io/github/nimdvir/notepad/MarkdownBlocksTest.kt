package io.github.nimdvir.notepad

import io.github.nimdvir.notepad.MdBlock.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownBlocksTest {
    private val doc = """
        # Title

        Para one
        still para

        - [ ] task
        - [x] done
          more text
        1. first

        > quote
        > more

        ```
        code

        here
        ```
        | a | b |
        | - | - |
        ---
    """.trimIndent()

    @Test
    fun splitsIntoBlocks() {
        val blocks = MarkdownBlocks.parse(doc)
        assertEquals(
            listOf(Kind.HEADING, Kind.PARAGRAPH, Kind.LIST_ITEM, Kind.LIST_ITEM, Kind.LIST_ITEM, Kind.QUOTE, Kind.CODE, Kind.TABLE, Kind.RULE),
            blocks.map { it.kind },
        )
        assertEquals("Para one\nstill para", blocks[1].source)
        assertEquals(false, blocks[2].task)
        assertEquals("task", blocks[2].content)
        assertEquals(true, blocks[3].task)
        assertEquals("done\nmore text", blocks[3].content)
        assertEquals(null, blocks[4].task)
        assertEquals("1.", blocks[4].marker)
        assertEquals("```\ncode\n\nhere\n```", blocks[6].source)
        blocks.forEach { assertEquals(it.source, doc.substring(it.start, it.end)) }
    }

    @Test
    fun findsBlockByOffset() {
        val blocks = MarkdownBlocks.parse(doc)
        assertEquals(0, MarkdownBlocks.indexAt(blocks, 0))
        assertEquals(1, MarkdownBlocks.indexAt(blocks, doc.indexOf("still")))
        assertEquals(2, MarkdownBlocks.indexAt(blocks, doc.indexOf("- [ ]") - 1)) // blank line maps to next block
        assertEquals(blocks.size - 1, MarkdownBlocks.indexAt(blocks, doc.length + 5))
    }

    @Test
    fun nestedListIndent() {
        val blocks = MarkdownBlocks.parse("- a\n  - b\n    - c")
        assertEquals(listOf(0, 1, 2), blocks.map { it.indent })
    }
}
