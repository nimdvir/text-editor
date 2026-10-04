package io.github.nimdvir.notepad

/** What a highlighted range means; the UI maps each kind to a text style. */
enum class MdStyle {
    H1, H2, H3, H4, H5, H6,
    /** Syntax characters such as `#`, `**`, `>`, brackets: shown dimmed. */
    MARKER,
    BOLD, ITALIC, STRIKE, CODE, CODE_BLOCK, QUOTE, LIST_MARKER, TASK_BOX, TASK_DONE, LINK_TEXT, LINK_URL,
}

data class MdSpan(val style: MdStyle, val start: Int, val end: Int)

/**
 * Line-based Markdown scanner used to style the editor while you type. It is deliberately forgiving and
 * fast rather than a full CommonMark parser: it only decides how characters look, never changes them.
 */
object MarkdownHighlighter {
    private val HEADING = Regex("^(#{1,6})([ \\t]+|$)")
    private val FENCE = Regex("^\\s{0,3}(```|~~~)")
    private val HR = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
    private val QUOTE = Regex("^\\s{0,3}(>\\s?)+")
    private val LIST = Regex("^(\\s*)([-*+]|\\d{1,9}[.)])([ \\t]+)")
    private val TASK = Regex("^\\[([ xX])\\]([ \\t]+|$)")
    private val INLINE_CODE = Regex("(`+)(.+?)\\1")
    private val BOLD = Regex("(\\*\\*|__)(?=\\S)(.+?)(?<=\\S)\\1")
    private val ITALIC_STAR = Regex("(?<![*\\\\])\\*(?=[^\\s*])(.+?)(?<=[^\\s*\\\\])\\*(?!\\*)")
    private val ITALIC_UNDERSCORE = Regex("(?<![_\\w])_(?=[^\\s_])(.+?)(?<=[^\\s_])_(?![_\\w])")
    private val STRIKE = Regex("~~(?=\\S)(.+?)(?<=\\S)~~")
    private val LINK = Regex("(!?\\[)([^\\]\\n]*)(\\]\\()([^)\\s]*)(\\))")

    fun highlight(text: CharSequence): List<MdSpan> {
        val spans = ArrayList<MdSpan>()
        var lineStart = 0
        var inFence = false
        while (lineStart <= text.length) {
            var lineEnd = lineStart
            while (lineEnd < text.length && text[lineEnd] != '\n') lineEnd++
            val line = text.subSequence(lineStart, lineEnd).toString()

            if (FENCE.containsMatchIn(line)) {
                spans += MdSpan(MdStyle.MARKER, lineStart, lineEnd)
                inFence = !inFence
            } else if (inFence) {
                if (lineEnd > lineStart) spans += MdSpan(MdStyle.CODE_BLOCK, lineStart, lineEnd)
            } else {
                highlightLine(line, lineStart, spans)
            }
            if (lineEnd >= text.length) break
            lineStart = lineEnd + 1
        }
        return spans
    }

    private fun highlightLine(line: String, base: Int, out: MutableList<MdSpan>) {
        if (line.isBlank()) return

        HEADING.find(line)?.let { m ->
            val level = m.groupValues[1].length
            out += MdSpan(MdStyle.MARKER, base, base + m.range.last + 1)
            out += MdSpan(MdStyle.entries[level - 1], base, base + line.length)
            highlightInline(line, m.range.last + 1, base, out)
            return
        }
        if (HR.matches(line)) {
            out += MdSpan(MdStyle.MARKER, base, base + line.length)
            return
        }

        var contentStart = 0
        QUOTE.find(line)?.let { m ->
            out += MdSpan(MdStyle.QUOTE, base, base + line.length)
            out += MdSpan(MdStyle.MARKER, base, base + m.range.last + 1)
            contentStart = m.range.last + 1
        }
        LIST.find(line.substring(contentStart))?.let { m ->
            val markerStart = contentStart + m.groupValues[1].length
            val markerEnd = contentStart + m.range.last + 1
            out += MdSpan(MdStyle.LIST_MARKER, base + markerStart, base + markerEnd)
            contentStart = markerEnd
            TASK.find(line.substring(contentStart))?.let { t ->
                out += MdSpan(MdStyle.TASK_BOX, base + contentStart, base + contentStart + 3)
                val done = t.groupValues[1] != " "
                contentStart += t.range.last + 1
                if (done && contentStart < line.length) {
                    out += MdSpan(MdStyle.TASK_DONE, base + contentStart, base + line.length)
                }
            }
        }
        if (line.trimStart().startsWith("|")) {
            line.forEachIndexed { i, c -> if (c == '|') out += MdSpan(MdStyle.MARKER, base + i, base + i + 1) }
        }
        highlightInline(line, contentStart, base, out)
    }

    private fun highlightInline(line: String, from: Int, base: Int, out: MutableList<MdSpan>) {
        if (from >= line.length) return
        // Code spans win: nothing inside them is formatting.
        val codeRanges = ArrayList<IntRange>()
        for (m in INLINE_CODE.findAll(line, from)) {
            codeRanges += m.range
            val tick = m.groupValues[1].length
            out += MdSpan(MdStyle.CODE, base + m.range.first, base + m.range.last + 1)
            out += MdSpan(MdStyle.MARKER, base + m.range.first, base + m.range.first + tick)
            out += MdSpan(MdStyle.MARKER, base + m.range.last + 1 - tick, base + m.range.last + 1)
        }
        fun outsideCode(r: IntRange) = codeRanges.none { it.first <= r.last && r.first <= it.last }

        fun wrapped(regex: Regex, style: MdStyle, markerLen: (MatchResult) -> Int) {
            for (m in regex.findAll(line, from)) {
                if (!outsideCode(m.range)) continue
                val n = markerLen(m)
                out += MdSpan(style, base + m.range.first, base + m.range.last + 1)
                out += MdSpan(MdStyle.MARKER, base + m.range.first, base + m.range.first + n)
                out += MdSpan(MdStyle.MARKER, base + m.range.last + 1 - n, base + m.range.last + 1)
            }
        }
        wrapped(BOLD, MdStyle.BOLD) { 2 }
        wrapped(ITALIC_STAR, MdStyle.ITALIC) { 1 }
        wrapped(ITALIC_UNDERSCORE, MdStyle.ITALIC) { 1 }
        wrapped(STRIKE, MdStyle.STRIKE) { 2 }

        for (m in LINK.findAll(line, from)) {
            if (!outsideCode(m.range)) continue
            val g = m.groups
            fun span(style: MdStyle, i: Int) {
                val r = g[i]?.range ?: return
                if (r.isEmpty()) return
                out += MdSpan(style, base + r.first, base + r.last + 1)
            }
            span(MdStyle.MARKER, 1)
            span(MdStyle.LINK_TEXT, 2)
            span(MdStyle.MARKER, 3)
            span(MdStyle.LINK_URL, 4)
            span(MdStyle.MARKER, 5)
        }
    }
}
