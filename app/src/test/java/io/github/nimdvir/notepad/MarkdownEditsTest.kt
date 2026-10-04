package io.github.nimdvir.notepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownEditsTest {
    private fun TextEdit.applyTo(text: String) = text.replaceRange(start, end, replacement)

    @Test
    fun wrapAndUnwrap() {
        val t = "say hello now"
        val bold = MarkdownEdits.toggleWrap(t, 4, 9, "**")
        assertEquals("say **hello** now", bold.applyTo(t))
        assertEquals(6 to 11, bold.selStart to bold.selEnd)
        val t2 = bold.applyTo(t)
        assertEquals(t, MarkdownEdits.toggleWrap(t2, 6, 11, "**").applyTo(t2)) // markers outside selection
        assertEquals(t, MarkdownEdits.toggleWrap(t2, 4, 13, "**").applyTo(t2)) // markers inside selection
        val empty = MarkdownEdits.toggleWrap("", 0, 0, "*", "text")
        assertEquals("*text*", empty.replacement)
        assertEquals(1 to 5, empty.selStart to empty.selEnd)
    }

    @Test
    fun headings() {
        val t = "Title\nbody"
        val h2 = MarkdownEdits.setHeading(t, 2, 2, 2)
        assertEquals("## Title\nbody", h2.applyTo(t))
        assertEquals(5, h2.selStart) // cursor stays after "Ti"
        val t2 = h2.applyTo(t)
        assertEquals("# Title\nbody", MarkdownEdits.setHeading(t2, 3, 3, 1).applyTo(t2))
        assertEquals(t, MarkdownEdits.setHeading(t2, 3, 3, 2).applyTo(t2))
    }

    @Test
    fun listsOnMultipleLines() {
        val t = "one\ntwo\nthree"
        assertEquals("- one\n- two\n- three", MarkdownEdits.toggleLineStyle(t, 0, t.length, MarkdownEdits.LineStyle.BULLET).applyTo(t))
        assertEquals("1. one\n2. two\n3. three", MarkdownEdits.toggleLineStyle(t, 0, t.length, MarkdownEdits.LineStyle.NUMBERED).applyTo(t))
        val tasks = MarkdownEdits.toggleLineStyle(t, 0, 5, MarkdownEdits.LineStyle.TASK).applyTo(t)
        assertEquals("- [ ] one\n- [ ] two\nthree", tasks)
        // Converting bullets to tasks replaces the marker instead of stacking.
        val bullets = "- a\n- b"
        assertEquals("- [ ] a\n- [ ] b", MarkdownEdits.toggleLineStyle(bullets, 0, bullets.length, MarkdownEdits.LineStyle.TASK).applyTo(bullets))
        // Toggling again removes.
        assertEquals("a\nb", MarkdownEdits.toggleLineStyle(bullets, 0, bullets.length, MarkdownEdits.LineStyle.BULLET).applyTo(bullets))
        assertEquals("> q", MarkdownEdits.toggleLineStyle("q", 0, 0, MarkdownEdits.LineStyle.QUOTE).applyTo("q"))
    }

    @Test
    fun tasksToggle() {
        val t = "- [ ] buy milk\n- [x] done"
        assertEquals("- [x] buy milk\n- [x] done", MarkdownEdits.toggleTaskAtLine(t, 0).applyTo(t))
        assertEquals("- [ ] buy milk\n- [ ] done", MarkdownEdits.toggleTaskAtLine(t, 15).applyTo(t))
    }

    @Test
    fun indentOutdent() {
        val t = "- a\n- b"
        val indented = MarkdownEdits.indent(t, 0, t.length).applyTo(t)
        assertEquals("  - a\n  - b", indented)
        assertEquals(t, MarkdownEdits.outdent(indented, 0, indented.length).applyTo(indented))
    }

    @Test
    fun linksAndBlocks() {
        val l = MarkdownEdits.link("see docs", 4, 8)
        assertEquals("see [docs](https://)", l.applyTo("see docs"))
        assertEquals("https://", l.applyTo("see docs").substring(l.selStart, l.selEnd))
        assertEquals("![description](https://)", MarkdownEdits.link("", 0, 0, image = true).replacement)
        assertEquals("a\n```\ncode\n```", MarkdownEdits.codeBlock("a\ncode", 2, 6).applyTo("a\ncode"))
        assertEquals("text\n\n---\n", MarkdownEdits.horizontalRule("text", 4, 4).applyTo("text"))
        val table = MarkdownEdits.table("", 0, 0)
        assertEquals("Column 1", table.replacement.substring(table.selStart, table.selEnd))
    }

    @Test
    fun smartEnter() {
        assertEquals("- a\n- ", MarkdownEdits.continueList("- a", 3)!!.applyTo("- a"))
        assertEquals("  1. a\n  2. ", MarkdownEdits.continueList("  1. a", 6)!!.applyTo("  1. a"))
        assertEquals("- [x] a\n- [ ] ", MarkdownEdits.continueList("- [x] a", 7)!!.applyTo("- [x] a"))
        // Empty item ends the list.
        val end = MarkdownEdits.continueList("- a\n- ", 6)!!
        assertEquals("- a\n", end.applyTo("- a\n- "))
        assertNull(MarkdownEdits.continueList("plain", 5))
        assertNull(MarkdownEdits.continueList("- a", 1)) // cursor inside the marker
    }
}
