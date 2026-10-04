package io.github.nimdvir.notepad

/**
 * One replacement in the text plus where the selection ends up. Applying it as a single edit keeps
 * each toolbar action as one undo step.
 */
data class TextEdit(val start: Int, val end: Int, val replacement: String, val selStart: Int, val selEnd: Int)

/** The Markdown toolbar's actions, as pure functions of (text, selection). */
object MarkdownEdits {
    private val LIST_PREFIX = Regex("^(\\s*)([-*+] \\[[ xX]\\] |[-*+] |\\d{1,9}[.)] )")
    private val HEADING_PREFIX = Regex("^#{1,6} ")
    private val QUOTE_PREFIX = Regex("^> ?")
    private val TASK_BOX = Regex("^(\\s*[-*+] )\\[([ xX])\\]")
    private val CONTINUE = Regex("^(\\s*)(?:([-*+])( \\[[ xX]\\])?|(\\d{1,9})([.)])) ")

    enum class LineStyle { BULLET, NUMBERED, TASK, QUOTE }

    /** Bold/italic/strike/code: wraps the selection, or removes the markers if it's already wrapped. */
    fun toggleWrap(text: String, selStart: Int, selEnd: Int, marker: String, placeholder: String = ""): TextEdit {
        val s = minOf(selStart, selEnd)
        val e = maxOf(selStart, selEnd)
        val n = marker.length
        // Markers just outside the selection: **|text|**
        if (s >= n && e + n <= text.length && text.regionMatches(s - n, marker, 0, n) &&
            text.regionMatches(e, marker, 0, n)
        ) {
            val inner = text.substring(s, e)
            return TextEdit(s - n, e + n, inner, s - n, s - n + inner.length)
        }
        // Markers included in the selection: |**text**|
        val selected = text.substring(s, e)
        if (selected.length >= 2 * n && selected.startsWith(marker) && selected.endsWith(marker)) {
            val inner = selected.substring(n, selected.length - n)
            return TextEdit(s, e, inner, s, s + inner.length)
        }
        val inner = selected.ifEmpty { placeholder }
        return TextEdit(s, e, marker + inner + marker, s + n, s + n + inner.length)
    }

    /** Sets every selected line to heading [level]; applying the same level again removes it. */
    fun setHeading(text: String, selStart: Int, selEnd: Int, level: Int): TextEdit {
        val prefix = "#".repeat(level) + " "
        return mapLines(text, selStart, selEnd) { lines ->
            val allSame = lines.all { it.startsWith(prefix) }
            lines.map { line ->
                val bare = line.replaceFirst(HEADING_PREFIX, "")
                if (allSame) bare else prefix + bare
            }
        }
    }

    /** Toggles bullet / numbered / task / quote on every selected line. */
    fun toggleLineStyle(text: String, selStart: Int, selEnd: Int, style: LineStyle): TextEdit =
        mapLines(text, selStart, selEnd) { lines ->
            val has: (String) -> Boolean = when (style) {
                LineStyle.BULLET -> { l -> Regex("^\\s*[-*+] (?!\\[[ xX]\\])").containsMatchIn(l) }
                LineStyle.NUMBERED -> { l -> Regex("^\\s*\\d{1,9}[.)] ").containsMatchIn(l) }
                LineStyle.TASK -> { l -> Regex("^\\s*[-*+] \\[[ xX]\\] ").containsMatchIn(l) }
                LineStyle.QUOTE -> { l -> QUOTE_PREFIX.containsMatchIn(l) }
            }
            val nonBlank = lines.filter { it.isNotBlank() }
            val remove = nonBlank.isNotEmpty() && nonBlank.all(has)
            var number = 0
            lines.map { line ->
                when {
                    style == LineStyle.QUOTE ->
                        if (remove) line.replaceFirst(QUOTE_PREFIX, "") else "> $line"
                    line.isBlank() && lines.size > 1 -> line
                    remove -> line.replaceFirst(LIST_PREFIX, "$1")
                    else -> {
                        val m = LIST_PREFIX.find(line)
                        val indent = m?.groupValues?.get(1) ?: line.takeWhile { it == ' ' || it == '\t' }
                        val body = if (m != null) line.substring(m.range.last + 1) else line.trimStart()
                        val marker = when (style) {
                            LineStyle.BULLET -> "- "
                            LineStyle.NUMBERED -> "${++number}. "
                            else -> "- [ ] "
                        }
                        indent + marker + body
                    }
                }
            }
        }

    /** Checks/unchecks task boxes on the selected lines (or the line at the cursor). */
    fun toggleTask(text: String, selStart: Int, selEnd: Int): TextEdit =
        mapLines(text, selStart, selEnd) { lines ->
            lines.map { line ->
                val m = TASK_BOX.find(line) ?: return@map line
                val checked = m.groupValues[2] != " "
                line.replaceRange(m.range, m.groupValues[1] + if (checked) "[ ]" else "[x]")
            }
        }

    /** Toggles the task box of the single line starting at [lineStart] (tapping a checkbox in preview). */
    fun toggleTaskAtLine(text: String, lineStart: Int): TextEdit = toggleTask(text, lineStart, lineStart)

    fun indent(text: String, selStart: Int, selEnd: Int): TextEdit =
        mapLines(text, selStart, selEnd) { lines -> lines.map { if (it.isEmpty()) it else "  $it" } }

    fun outdent(text: String, selStart: Int, selEnd: Int): TextEdit =
        mapLines(text, selStart, selEnd) { lines ->
            lines.map {
                when {
                    it.startsWith("\t") -> it.substring(1)
                    it.startsWith("  ") -> it.substring(2)
                    it.startsWith(" ") -> it.substring(1)
                    else -> it
                }
            }
        }

    fun link(text: String, selStart: Int, selEnd: Int, image: Boolean = false): TextEdit {
        val s = minOf(selStart, selEnd)
        val e = maxOf(selStart, selEnd)
        val label = text.substring(s, e)
        val open = if (image) "![" else "["
        return if (label.isEmpty()) {
            val placeholder = if (image) "description" else "text"
            TextEdit(s, e, "$open$placeholder](https://)", s + open.length, s + open.length + placeholder.length)
        } else {
            val urlStart = s + open.length + label.length + 2
            TextEdit(s, e, "$open$label](https://)", urlStart, urlStart + "https://".length)
        }
    }

    fun codeBlock(text: String, selStart: Int, selEnd: Int): TextEdit {
        val s = minOf(selStart, selEnd)
        val e = maxOf(selStart, selEnd)
        val body = text.substring(s, e)
        val before = if (s > 0 && text[s - 1] != '\n') "\n" else ""
        val after = if (e < text.length && text[e] != '\n') "\n" else ""
        val insert = "$before```\n$body\n```$after"
        val cursor = s + before.length + 4
        return TextEdit(s, e, insert, cursor, cursor + body.length)
    }

    fun horizontalRule(text: String, selStart: Int, selEnd: Int): TextEdit =
        insertBlock(text, selStart, selEnd, "---", cursorAtEnd = true)

    fun table(text: String, selStart: Int, selEnd: Int): TextEdit {
        val template = "| Column 1 | Column 2 | Column 3 |\n| --- | --- | --- |\n|  |  |  |\n|  |  |  |"
        val edit = insertBlock(text, selStart, selEnd, template, cursorAtEnd = false)
        val first = edit.start + edit.replacement.indexOf("Column 1")
        return edit.copy(selStart = first, selEnd = first + "Column 1".length)
    }

    /**
     * Smart Enter: called when a line break was just typed at [cursor] (the offset where it was inserted,
     * in the text before the insert). Returns the edit to apply to the *original* text instead, or null to
     * keep the plain line break.
     */
    fun continueList(text: String, cursor: Int): TextEdit? {
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        val m = CONTINUE.find(line) ?: return null
        val prefixEnd = lineStart + m.range.last + 1
        if (cursor < prefixEnd) return null
        val content = text.substring(prefixEnd, lineEnd)
        if (content.isBlank() && cursor == lineEnd) {
            // Enter on an empty item ends the list: clear the marker, no new line.
            return TextEdit(lineStart, lineEnd, "", lineStart, lineStart)
        }
        val indent = m.groupValues[1]
        val next = when {
            m.groupValues[4].isNotEmpty() -> "${m.groupValues[4].toLong() + 1}${m.groupValues[5]} "
            m.groupValues[3].isNotEmpty() -> "${m.groupValues[2]} [ ] "
            else -> "${m.groupValues[2]} "
        }
        val insert = "\n$indent$next"
        return TextEdit(cursor, cursor, insert, cursor + insert.length, cursor + insert.length)
    }

    private fun insertBlock(text: String, selStart: Int, selEnd: Int, block: String, cursorAtEnd: Boolean): TextEdit {
        val s = minOf(selStart, selEnd)
        val e = maxOf(selStart, selEnd)
        val before = when {
            s == 0 -> ""
            text[s - 1] != '\n' -> "\n\n"
            s >= 2 && text[s - 2] != '\n' -> "\n"
            else -> ""
        }
        val after = if (e < text.length && text[e] != '\n') "\n\n" else "\n"
        val insert = before + block + after
        val cursor = if (cursorAtEnd) s + insert.length else s + before.length
        return TextEdit(s, e, insert, cursor, cursor)
    }

    /** Applies [transform] to all lines touched by the selection, as one edit. */
    private fun mapLines(text: String, selStart: Int, selEnd: Int, transform: (List<String>) -> List<String>): TextEdit {
        val s = minOf(selStart, selEnd)
        val e = maxOf(selStart, selEnd)
        val start = text.lastIndexOf('\n', s - 1) + 1
        // A selection ending right after a line break doesn't include the next line.
        val effectiveEnd = if (e > s && text[e - 1] == '\n') e - 1 else e
        val end = text.indexOf('\n', effectiveEnd).let { if (it < 0) text.length else it }
        val original = text.substring(start, end)
        val replaced = transform(original.split('\n')).joinToString("\n")
        return if (s == e) {
            // Keep the cursor at the same place relative to the end of its line.
            val fromEnd = end - s
            val cursor = (start + replaced.length - fromEnd).coerceIn(start, start + replaced.length)
            TextEdit(start, end, replaced, cursor, cursor)
        } else {
            TextEdit(start, end, replaced, start, start + replaced.length)
        }
    }
}
