package io.github.nimdvir.notepad.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nimdvir.notepad.EditorViewModel
import io.github.nimdvir.notepad.MarkdownEdits
import io.github.nimdvir.notepad.MarkdownEdits.LineStyle

/** One-tap Markdown formatting, shown above the keyboard. Each button toggles, so tapping again undoes it. */
@Composable
fun MarkdownToolbar(vm: EditorViewModel) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeadingButton(vm)
            Tool("B", "Bold", SpanStyle(fontWeight = FontWeight.Bold)) {
                vm.applyMarkdown { t, s, e -> MarkdownEdits.toggleWrap(t, s, e, "**", "bold text") }
            }
            Tool("I", "Italic", SpanStyle(fontStyle = FontStyle.Italic)) {
                vm.applyMarkdown { t, s, e -> MarkdownEdits.toggleWrap(t, s, e, "*", "italic text") }
            }
            Tool("S", "Strikethrough", SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                vm.applyMarkdown { t, s, e -> MarkdownEdits.toggleWrap(t, s, e, "~~", "text") }
            }
            Tool("</>", "Inline code", SpanStyle(fontFamily = FontFamily.Monospace)) {
                vm.applyMarkdown { t, s, e -> MarkdownEdits.toggleWrap(t, s, e, "`", "code") }
            }
            Tool("{ }", "Code block", SpanStyle(fontFamily = FontFamily.Monospace)) {
                vm.applyMarkdown(MarkdownEdits::codeBlock)
            }
            Tool("•", "Bullet list") {
                vm.applyMarkdown { t, s, e -> MarkdownEdits.toggleLineStyle(t, s, e, LineStyle.BULLET) }
            }
            Tool("1.", "Numbered list") {
                vm.applyMarkdown { t, s, e -> MarkdownEdits.toggleLineStyle(t, s, e, LineStyle.NUMBERED) }
            }
            Tool("☐", "Task list") {
                vm.applyMarkdown { t, s, e -> MarkdownEdits.toggleLineStyle(t, s, e, LineStyle.TASK) }
            }
            Tool("☑", "Check / uncheck task") {
                vm.applyMarkdown(MarkdownEdits::toggleTask)
            }
            Tool("❝", "Quote") {
                vm.applyMarkdown { t, s, e -> MarkdownEdits.toggleLineStyle(t, s, e, LineStyle.QUOTE) }
            }
            Tool("Link", "Link", SpanStyle(textDecoration = TextDecoration.Underline)) {
                vm.applyMarkdown { t, s, e -> MarkdownEdits.link(t, s, e) }
            }
            Tool("Image", "Image") {
                vm.applyMarkdown { t, s, e -> MarkdownEdits.link(t, s, e, image = true) }
            }
            Tool("Table", "Table") { vm.applyMarkdown(MarkdownEdits::table) }
            Tool("―", "Horizontal line") { vm.applyMarkdown(MarkdownEdits::horizontalRule) }
            Tool("⇥", "Indent") { vm.applyMarkdown(MarkdownEdits::indent) }
            Tool("⇤", "Outdent") { vm.applyMarkdown(MarkdownEdits::outdent) }
        }
    }
}

@Composable
private fun HeadingButton(vm: EditorViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        Tool("H▾", "Heading", SpanStyle(fontWeight = FontWeight.Bold)) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (level in 1..6) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "Heading $level",
                            fontWeight = FontWeight.Bold,
                            fontSize = (24 - level * 2).sp,
                        )
                    },
                    onClick = {
                        open = false
                        vm.applyMarkdown { t, s, e -> MarkdownEdits.setHeading(t, s, e, level) }
                    },
                )
            }
        }
    }
}

@Composable
private fun Tool(label: String, description: String, style: SpanStyle? = null, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.widthIn(min = 40.dp).semantics { contentDescription = description },
    ) {
        Text(
            text = buildAnnotatedString {
                if (style != null) withStyle(style) { append(label) } else append(label)
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
