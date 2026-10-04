package io.github.nimdvir.notepad.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import io.github.nimdvir.notepad.MarkdownEdits
import io.github.nimdvir.notepad.MarkdownHighlighter
import io.github.nimdvir.notepad.MdStyle

/**
 * Makes Markdown look formatted while you type: big bold headings, bold/italic text, dimmed symbols.
 * Display only — the text itself (and undo history) is untouched.
 */
class MarkdownOutputTransformation(colors: ColorScheme) : OutputTransformation {
    private val styles: Map<MdStyle, SpanStyle> = mapOf(
        MdStyle.H1 to SpanStyle(fontSize = 1.6.em, fontWeight = FontWeight.Bold),
        MdStyle.H2 to SpanStyle(fontSize = 1.4.em, fontWeight = FontWeight.Bold),
        MdStyle.H3 to SpanStyle(fontSize = 1.25.em, fontWeight = FontWeight.Bold),
        MdStyle.H4 to SpanStyle(fontSize = 1.1.em, fontWeight = FontWeight.Bold),
        MdStyle.H5 to SpanStyle(fontWeight = FontWeight.Bold),
        MdStyle.H6 to SpanStyle(fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant),
        MdStyle.MARKER to SpanStyle(color = colors.onSurface.copy(alpha = 0.38f)),
        MdStyle.BOLD to SpanStyle(fontWeight = FontWeight.Bold),
        MdStyle.ITALIC to SpanStyle(fontStyle = FontStyle.Italic),
        MdStyle.STRIKE to SpanStyle(textDecoration = TextDecoration.LineThrough),
        MdStyle.CODE to SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceVariant),
        MdStyle.CODE_BLOCK to SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceVariant),
        MdStyle.QUOTE to SpanStyle(color = colors.onSurfaceVariant, fontStyle = FontStyle.Italic),
        MdStyle.LIST_MARKER to SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold),
        MdStyle.TASK_BOX to SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold),
        MdStyle.TASK_DONE to SpanStyle(color = colors.onSurfaceVariant, textDecoration = TextDecoration.LineThrough),
        MdStyle.LINK_TEXT to SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline),
        MdStyle.LINK_URL to SpanStyle(color = colors.onSurface.copy(alpha = 0.45f)),
    )

    override fun TextFieldBuffer.transformOutput() {
        val spans = MarkdownHighlighter.highlight(asCharSequence())
        for (span in spans) {
            if (span.end <= span.start || span.end > length) continue
            styles[span.style]?.let { addStyle(it, span.start, span.end) }
        }
    }

    override fun equals(other: Any?) = other is MarkdownOutputTransformation && other.styles == styles
    override fun hashCode() = styles.hashCode()
}

/** Smart Enter for Markdown lists: continues bullets, numbers and task boxes; Enter on an empty item ends the list. */
@OptIn(ExperimentalFoundationApi::class)
object MarkdownListContinuation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (changes.changeCount != 1) return
        val range = changes.getRange(0)
        val original = changes.getOriginalRange(0)
        if (original.length != 0 || range.length != 1 || charAt(range.start) != '\n') return
        val edit = MarkdownEdits.continueList(originalText.toString(), original.start) ?: return
        revertAllChanges()
        replace(edit.start, edit.end, edit.replacement)
        selection = TextRange(edit.selStart, edit.selEnd)
    }
}
