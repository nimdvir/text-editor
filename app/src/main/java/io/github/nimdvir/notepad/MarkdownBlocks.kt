package io.github.nimdvir.notepad

/**
 * A top-level piece of a Markdown document, with where it sits in the source. The preview renders one
 * block per row, which lets you tap a block to edit it and keeps the text and preview scrolled together.
 */
data class MdBlock(
    val kind: Kind,
    /** Offset of the first character in the source. */
    val start: Int,
    /** Offset just past the last character (excluding the final line break). */
    val end: Int,
    val source: String,
    /** For list items: indentation level (0 = top), the marker as written, and task state. */
    val indent: Int = 0,
    val marker: String = "",
    val task: Boolean? = null,
    /** For list items: the item text without its marker/checkbox. */
    val content: String = "",
) {
    enum class Kind { HEADING, PARAGRAPH, LIST_ITEM, CODE, QUOTE, TABLE, RULE }
}

object MarkdownBlocks {
    private val HEADING = Regex("^#{1,6}([ \\t]|$)")
    private val FENCE = Regex("^\\s{0,3}(```|~~~)")
    private val RULE = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
    private val QUOTE = Regex("^\\s{0,3}>")
    private val LIST = Regex("^(\\s*)([-*+]|\\d{1,9}[.)])[ \\t]+(\\[([ xX])\\][ \\t]?)?")
    private val TABLE = Regex("^\\s{0,3}\\|")

    private data class Line(val start: Int, val end: Int, val text: String)

    fun parse(text: CharSequence): List<MdBlock> {
        val lines = splitLines(text)
        val blocks = ArrayList<MdBlock>()
        var i = 0
        fun block(kind: MdBlock.Kind, from: Int, to: Int) {
            val start = lines[from].start
            val end = lines[to].end
            blocks += MdBlock(kind, start, end, text.substring(start, end))
        }
        while (i < lines.size) {
            val line = lines[i].text
            when {
                line.isBlank() -> i++
                FENCE.containsMatchIn(line) -> {
                    val fence = FENCE.find(line)!!.groupValues[1]
                    var j = i + 1
                    while (j < lines.size && !lines[j].text.trimStart().startsWith(fence)) j++
                    val last = minOf(j, lines.size - 1)
                    block(MdBlock.Kind.CODE, i, last)
                    i = last + 1
                }
                HEADING.containsMatchIn(line) -> { block(MdBlock.Kind.HEADING, i, i); i++ }
                RULE.matches(line) -> { block(MdBlock.Kind.RULE, i, i); i++ }
                QUOTE.containsMatchIn(line) -> {
                    var j = i
                    while (j + 1 < lines.size && QUOTE.containsMatchIn(lines[j + 1].text)) j++
                    block(MdBlock.Kind.QUOTE, i, j)
                    i = j + 1
                }
                TABLE.containsMatchIn(line) -> {
                    var j = i
                    while (j + 1 < lines.size && TABLE.containsMatchIn(lines[j + 1].text)) j++
                    block(MdBlock.Kind.TABLE, i, j)
                    i = j + 1
                }
                LIST.containsMatchIn(line) -> {
                    val m = LIST.find(line)!!
                    // Continuation lines: indented, non-blank lines that don't start a new item.
                    var j = i
                    while (j + 1 < lines.size) {
                        val next = lines[j + 1].text
                        if (next.isBlank() || LIST.containsMatchIn(next) || !next.startsWith(" ")) break
                        j++
                    }
                    val start = lines[i].start
                    val end = lines[j].end
                    val source = text.substring(start, end)
                    val indentChars = m.groupValues[1].replace("\t", "    ").length
                    val taskMark = m.groups[4]?.value
                    blocks += MdBlock(
                        kind = MdBlock.Kind.LIST_ITEM,
                        start = start,
                        end = end,
                        source = source,
                        indent = indentChars / 2,
                        marker = m.groupValues[2],
                        task = taskMark?.let { it != " " },
                        content = source.substring(m.range.last + 1).trimIndentLines(),
                    )
                    i = j + 1
                }
                else -> {
                    var j = i
                    while (j + 1 < lines.size) {
                        val next = lines[j + 1].text
                        if (next.isBlank() || startsBlock(next)) break
                        j++
                    }
                    block(MdBlock.Kind.PARAGRAPH, i, j)
                    i = j + 1
                }
            }
        }
        return blocks
    }

    /** Index of the block that contains [offset], or the nearest one after it (or the last block). */
    fun indexAt(blocks: List<MdBlock>, offset: Int): Int {
        if (blocks.isEmpty()) return -1
        var lo = 0
        var hi = blocks.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (blocks[mid].end < offset) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun startsBlock(line: String) =
        FENCE.containsMatchIn(line) || HEADING.containsMatchIn(line) || RULE.matches(line) ||
            QUOTE.containsMatchIn(line) || TABLE.containsMatchIn(line) || LIST.containsMatchIn(line)

    private fun splitLines(text: CharSequence): List<Line> {
        val lines = ArrayList<Line>()
        var start = 0
        while (true) {
            var end = start
            while (end < text.length && text[end] != '\n') end++
            lines += Line(start, end, text.substring(start, end))
            if (end >= text.length) break
            start = end + 1
        }
        return lines
    }

    private fun String.trimIndentLines(): String = lines().joinToString("\n") { it.trimStart() }
}
