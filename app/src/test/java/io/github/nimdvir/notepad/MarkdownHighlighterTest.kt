package io.github.nimdvir.notepad

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class MarkdownHighlighterTest {
    private fun spans(t: String) = MarkdownHighlighter.highlight(t)
    private fun has(t: String, style: MdStyle, sub: String): Boolean {
        val i = t.indexOf(sub)
        return spans(t).any { it.style == style && it.start == i && it.end == i + sub.length }
    }

    @Test
    fun headingsAndInline() {
        val t = "## Hello **world**"
        assertTrue(has(t, MdStyle.H2, t))
        assertTrue(has(t, MdStyle.MARKER, "## "))
        assertTrue(has(t, MdStyle.BOLD, "**world**"))
        assertTrue(has("a *it* b", MdStyle.ITALIC, "*it*"))
        assertTrue(has("a _it_ b", MdStyle.ITALIC, "_it_"))
        assertTrue(has("a ~~x~~", MdStyle.STRIKE, "~~x~~"))
        assertTrue(has("use `a*b*c` here", MdStyle.CODE, "`a*b*c`"))
        assertFalse(spans("use `a*b*c` here").any { it.style == MdStyle.ITALIC })
        assertFalse(spans("#hashtag").any { it.style == MdStyle.H1 })
        assertFalse(spans("snake_case_name").any { it.style == MdStyle.ITALIC })
    }

    @Test
    fun listsTasksQuotesLinks() {
        val t = "- [x] done item"
        assertTrue(has(t, MdStyle.LIST_MARKER, "- "))
        assertTrue(has(t, MdStyle.TASK_BOX, "[x]"))
        assertTrue(has(t, MdStyle.TASK_DONE, "done item"))
        assertTrue(has("> hi", MdStyle.QUOTE, "> hi"))
        val l = "see [docs](http://x.y)"
        assertTrue(has(l, MdStyle.LINK_TEXT, "docs"))
        assertTrue(has(l, MdStyle.LINK_URL, "http://x.y"))
    }

    @Test
    fun fencedCodeIsNotFormatted() {
        val t = "```\n# not heading **x**\n```\n# Heading"
        assertTrue(has(t, MdStyle.CODE_BLOCK, "# not heading **x**"))
        assertFalse(spans(t).any { it.style == MdStyle.BOLD })
        assertTrue(has(t, MdStyle.H1, "# Heading"))
    }
}
