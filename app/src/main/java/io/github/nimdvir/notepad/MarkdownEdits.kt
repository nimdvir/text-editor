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

    /** Heading, quote, list and task markers at the start of a line (possibly nested, e.g. "> - "). */
    private val LINE_MARKUP = Regex("^(?:\\s*(?:#{1,6} |> ?|[-*+] \\[[ xX]\\] |[-*+] |\\d{1,9}[.)] ))*")

    /**
     * Bold/italic/strike/code. Tapping again removes it.
     * - Text selected on one line: applies to the selection.
     * - Text selected across lines: applies to each line separately (Markdown can't span lines).
     * - No selection, cursor at the start or end of a line: applies to the whole line (after any list/heading marker).
     * - No selection, cursor inside a word: applies to that word.
     * - Otherwise: inserts the markers around a selected [placeholder], ready to type over.
     */
    fun toggleWrap(text: String, selStart: Int, selEnd: Int, marker: String, placeholder: String = ""): TextEdit {
        val s = minOf(selStart, selEnd)
        val e = maxOf(selStart, selEnd)
        if (s == e) {
            val target = autoRange(text, s)
                ?: return TextEdit(s, s, marker + placeholder + marker, s + marker.length, s + marker.length + placeholder.length)
            return wrapRange(text, target.first, target.last + 1, marker)
        }
        if (text.substring(s, e).contains('\n')) return wrapLines(text, s, e, marker)
        return wrapRange(text, s, e, marker)
    }

    /** Wraps [s, e) in [marker], or unwraps it if it already carries that marker (just outside or just inside). */
    private fun wrapRange(text: String, s: Int, e: Int, marker: String): TextEdit {
        val n = marker.length
        val c = marker[0]
        val before = runBefore(text, s, c)
        val after = runAfter(text, e, c)
        if (hasMarker(minOf(before, after), marker)) {
            val inner = text.substring(s, e)
            return TextEdit(s - n, e + n, inner, s - n, s - n + inner.length)
        }
        val selected = text.substring(s, e)
        if (isWrapped(selected, marker)) {
            val inner = selected.substring(n, selected.length - n)
            return TextEdit(s, e, inner, s, s + inner.length)
        }
        return TextEdit(s, e, marker + selected + marker, s + n, s + n + selected.length)
    }

    private fun wrapLines(text: String, selStart: Int, selEnd: Int, marker: String): TextEdit =
        mapLines(text, selStart, selEnd) { lines ->
            val parts = lines.map { splitLine(it) }
            val contents = parts.map { it.second }.filter { it.isNotBlank() }
            val unwrap = contents.isNotEmpty() && contents.all { isWrapped(it.trim(), marker) }
            parts.map { (prefix, content, trailing) ->
                when {
                    content.isBlank() -> prefix + content + trailing
                    unwrap -> prefix + content.substring(marker.length, content.length - marker.length) + trailing
                    isWrapped(content, marker) -> prefix + content + trailing
                    else -> prefix + marker + content + marker + trailing
                }
            }
        }

    /** Splits a line into (markup prefix, content, trailing spaces). */
    private fun splitLine(line: String): Triple<String, String, String> {
        val markup = LINE_MARKUP.find(line)?.value.orEmpty()
        val prefix = markup + line.substring(markup.length).takeWhile { it == ' ' || it == '\t' }
        val rest = line.substring(prefix.length)
        val content = rest.trimEnd()
        return Triple(prefix, content, rest.substring(content.length))
    }

    /** What a toolbar tap with no selection should format: the line's content or the word at [pos]. */
    private fun autoRange(text: String, pos: Int): IntRange? {
        val lineStart = text.lastIndexOf('\n', pos - 1) + 1
        val lineEnd = text.indexOf('\n', pos).let { if (it < 0) text.length else it }
        val (prefix, content, _) = splitLine(text.substring(lineStart, lineEnd))
        if (content.isBlank()) return null
        val contentStart = lineStart + prefix.length
        val contentEnd = contentStart + content.length
        if (pos <= contentStart || pos >= contentEnd) return contentStart until contentEnd
        fun isWord(i: Int) = i in contentStart until contentEnd && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '\'')
        if (!isWord(pos - 1) && !isWord(pos)) return null
        var a = pos
        var b = pos
        while (isWord(a - 1)) a--
        while (isWord(b)) b++
        return a until b
    }

    /**
     * Whether a run of [run] marker characters means [marker] is applied. Bold and italic share `*`, so a
     * run of 3 is both, 2 is bold only, 1 is italic only.
     */
    private fun hasMarker(run: Int, marker: String): Boolean = when (marker) {
        "*", "_" -> run % 2 == 1
        else -> run >= marker.length
    }

    private fun isWrapped(s: String, marker: String): Boolean {
        val c = marker[0]
        if (s.length < 2 * marker.length) return false
        val lead = s.takeWhile { it == c }.length
        val trail = s.takeLastWhile { it == c }.length
        if (lead >= s.length) return false
        return hasMarker(minOf(lead, trail), marker)
    }

    private fun runBefore(text: String, i: Int, c: Char): Int {
        var n = 0
        while (i - n - 1 >= 0 && text[i - n - 1] == c) n++
        return n
    }

    private fun runAfter(text: String, i: Int, c: Char): Int {
        var n = 0
        while (i + n < text.length && text[i + n] == c) n++
        return n
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
