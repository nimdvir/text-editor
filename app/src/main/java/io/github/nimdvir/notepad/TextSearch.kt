package io.github.nimdvir.notepad

/** Find / Replace / Go To helpers. All offsets are in the `\n`-normalized editor text. */
object TextSearch {

    /** Next match starting at or after [from], wrapping around to the top like Notepad's "Wrap around". */
    fun findNext(text: CharSequence, query: String, from: Int, matchCase: Boolean): IntRange? {
        if (query.isEmpty() || text.length < query.length) return null
        val start = from.coerceIn(0, text.length)
        val hit = indexOf(text, query, start, matchCase).takeIf { it >= 0 }
            ?: indexOf(text, query, 0, matchCase).takeIf { it in 0 until start }
            ?: return null
        return hit until hit + query.length
    }

    /** Previous match ending at or before [before], wrapping around to the bottom. */
    fun findPrevious(text: CharSequence, query: String, before: Int, matchCase: Boolean): IntRange? {
        if (query.isEmpty() || text.length < query.length) return null
        val end = before.coerceIn(0, text.length)
        val hit = lastIndexOf(text, query, end - query.length, matchCase).takeIf { it >= 0 }
            ?: lastIndexOf(text, query, text.length - query.length, matchCase)
                .takeIf { it >= 0 && it + query.length > end }
            ?: return null
        return hit until hit + query.length
    }

    fun countMatches(text: CharSequence, query: String, matchCase: Boolean): Int {
        if (query.isEmpty()) return 0
        var count = 0
        var i = indexOf(text, query, 0, matchCase)
        while (i >= 0) {
            count++
            i = indexOf(text, query, i + query.length, matchCase)
        }
        return count
    }

    /** Returns the new text and how many replacements were made. */
    fun replaceAll(text: String, query: String, replacement: String, matchCase: Boolean): Pair<String, Int> {
        val count = countMatches(text, query, matchCase)
        if (count == 0) return text to 0
        return text.replace(query, replacement, ignoreCase = !matchCase) to count
    }

    fun matchesAt(text: CharSequence, range: IntRange, query: String, matchCase: Boolean): Boolean =
        query.isNotEmpty() && range.last + 1 - range.first == query.length &&
            range.first >= 0 && range.last < text.length &&
            text.regionMatches(range.first, query, 0, query.length, ignoreCase = !matchCase)

    /** 1-based line and column of [offset]. */
    fun lineAndColumn(text: CharSequence, offset: Int): Pair<Int, Int> {
        val end = offset.coerceIn(0, text.length)
        var line = 1
        var lineStart = 0
        for (i in 0 until end) {
            if (text[i] == '\n') {
                line++
                lineStart = i + 1
            }
        }
        return line to (end - lineStart + 1)
    }

    fun lineCount(text: CharSequence): Int = text.count { it == '\n' } + 1

    /** Offset where 1-based [line] starts, or null if the line does not exist. */
    fun lineStartOffset(text: CharSequence, line: Int): Int? {
        if (line < 1) return null
        if (line == 1) return 0
        var current = 1
        for (i in text.indices) {
            if (text[i] == '\n') {
                current++
                if (current == line) return i + 1
            }
        }
        return null
    }

    private fun indexOf(text: CharSequence, query: String, from: Int, matchCase: Boolean): Int =
        text.indexOf(query, from, ignoreCase = !matchCase)

    private fun lastIndexOf(text: CharSequence, query: String, from: Int, matchCase: Boolean): Int =
        if (from < 0) -1 else text.lastIndexOf(query, from, ignoreCase = !matchCase)
}
