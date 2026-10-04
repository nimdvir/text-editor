package io.github.nimdvir.notepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {
    private val text = "Cat cat CAT dog"

    @Test
    fun findNextRespectsCaseAndWraps() {
        assertEquals(4..6, TextSearch.findNext(text, "cat", 1, matchCase = true))
        assertEquals(4..6, TextSearch.findNext(text, "cat", 5, matchCase = true)) // wraps to the only match
        assertEquals(8..10, TextSearch.findNext(text, "cat", 7, matchCase = false))
        assertEquals(0..2, TextSearch.findNext(text, "cat", 11, matchCase = false)) // wraps to top
        assertNull(TextSearch.findNext(text, "bird", 0, matchCase = false))
        assertNull(TextSearch.findNext(text, "", 0, matchCase = false))
    }

    @Test
    fun findPreviousWraps() {
        assertEquals(4..6, TextSearch.findPrevious(text, "cat", 8, matchCase = false))
        assertEquals(8..10, TextSearch.findPrevious(text, "cat", 0, matchCase = false)) // wraps to bottom
        assertEquals(4..6, TextSearch.findPrevious(text, "cat", 4, matchCase = true)) // wraps to the only match
    }

    @Test
    fun replaceAllCountsAndReplaces() {
        assertEquals("dog dog dog dog" to 3, TextSearch.replaceAll(text, "cat", "dog", matchCase = false))
        assertEquals("Cat dog CAT dog" to 1, TextSearch.replaceAll(text, "cat", "dog", matchCase = true))
        assertEquals(text to 0, TextSearch.replaceAll(text, "bird", "x", matchCase = false))
    }

    @Test
    fun matchesAt() {
        assertTrue(TextSearch.matchesAt(text, 0..2, "cat", matchCase = false))
        assertFalse(TextSearch.matchesAt(text, 0..2, "cat", matchCase = true))
        assertFalse(TextSearch.matchesAt(text, 0..1, "cat", matchCase = false))
    }

    @Test
    fun lineAndColumn() {
        val t = "ab\ncde\n\nf"
        assertEquals(1 to 1, TextSearch.lineAndColumn(t, 0))
        assertEquals(1 to 3, TextSearch.lineAndColumn(t, 2))
        assertEquals(2 to 1, TextSearch.lineAndColumn(t, 3))
        assertEquals(4 to 2, TextSearch.lineAndColumn(t, t.length))
        assertEquals(4, TextSearch.lineCount(t))
    }

    @Test
    fun lineStartOffset() {
        val t = "ab\ncde\n\nf"
        assertEquals(0, TextSearch.lineStartOffset(t, 1))
        assertEquals(3, TextSearch.lineStartOffset(t, 2))
        assertEquals(7, TextSearch.lineStartOffset(t, 3))
        assertEquals(8, TextSearch.lineStartOffset(t, 4))
        assertNull(TextSearch.lineStartOffset(t, 5))
        assertNull(TextSearch.lineStartOffset(t, 0))
    }
}
