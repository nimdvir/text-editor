package io.github.nimdvir.notepad.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import io.github.nimdvir.notepad.ViewSettings

private val EditorPadding = TextFieldDecorator { innerTextField ->
    Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { innerTextField() }
}

/** The plain-text editing area. Undo/redo history lives in [state]. */
@Composable
fun Editor(state: TextFieldState, settings: ViewSettings, focusRequester: FocusRequester) {
    val style = TextStyle(
        fontFamily = if (settings.monospace) FontFamily.Monospace else FontFamily.Default,
        fontSize = settings.fontSizeSp.sp,
        lineHeight = (settings.fontSizeSp * 1.35f).sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    val cursor = SolidColor(MaterialTheme.colorScheme.primary)

    if (settings.wordWrap) {
        BasicTextField(
            state = state,
            modifier = Modifier.fillMaxSize().focusRequester(focusRequester),
            textStyle = style,
            keyboardOptions = keyboard,
            cursorBrush = cursor,
            decorator = EditorPadding,
        )
    } else {
        // Without word wrap, lines keep their full width and the editor scrolls sideways.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewportWidth = maxWidth
            BasicTextField(
                state = state,
                modifier = Modifier
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState())
                    .widthIn(min = viewportWidth)
                    .focusRequester(focusRequester),
                textStyle = style,
                keyboardOptions = keyboard,
                cursorBrush = cursor,
                decorator = EditorPadding,
            )
        }
    }
}

@Composable
fun MarkdownPreview(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (text.isBlank()) {
            Text("Nothing to preview yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Markdown(text)
        }
    }
}
