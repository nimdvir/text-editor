package io.github.nimdvir.notepad.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nimdvir.notepad.ViewSettings

/** Vertical padding inside the editor, so callers can convert scroll positions to text lines. */
val EditorPaddingTop = 8.dp

private val EditorPadding = TextFieldDecorator { innerTextField ->
    Box(Modifier.padding(horizontal = 12.dp, vertical = EditorPaddingTop)) { innerTextField() }
}

@Composable
fun editorTextStyle(settings: ViewSettings): TextStyle = TextStyle(
    fontFamily = if (settings.monospace) FontFamily.Monospace else FontFamily.Default,
    fontSize = settings.fontSizeSp.sp,
    lineHeight = (settings.fontSizeSp * 1.35f).sp,
    color = MaterialTheme.colorScheme.onSurface,
)

/**
 * The main editing area. Undo/redo history lives in [state]. For Markdown files the text is styled
 * as you type and list items continue on Enter.
 */
@Composable
fun Editor(
    state: TextFieldState,
    settings: ViewSettings,
    focusRequester: FocusRequester,
    markdown: Boolean,
    scrollState: ScrollState,
    onLayout: (getLayout: () -> TextLayoutResult?) -> Unit = {},
    onFocused: () -> Unit = {},
) {
    val style = editorTextStyle(settings)
    val keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    val cursor = SolidColor(MaterialTheme.colorScheme.primary)
    val colors = MaterialTheme.colorScheme
    val output = remember(markdown, colors) { if (markdown) MarkdownOutputTransformation(colors) else null }
    val input = if (markdown) MarkdownListContinuation else null
    val layoutRef = remember { arrayOfNulls<() -> TextLayoutResult?>(1) }
    var focused by remember { mutableStateOf(false) }
    val common = Modifier
        .focusRequester(focusRequester)
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocused()
        }
    val reportLayout: (() -> TextLayoutResult?) -> Unit = { get ->
        layoutRef[0] = get
        onLayout(get)
    }

    // When the keyboard opens (or resizes) the editor gets shorter: keep the line being typed on screen.
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom, focused) {
        if (!focused) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        val layout = layoutRef[0]?.invoke() ?: return@LaunchedEffect
        val offset = state.selection.end.coerceIn(0, layout.layoutInput.text.length)
        val caret = layout.getCursorRect(offset)
        val viewport = scrollState.viewportSize
        if (viewport <= 0) return@LaunchedEffect
        val margin = caret.height.toInt()
        when {
            caret.bottom + margin > scrollState.value + viewport ->
                scrollState.scrollTo((caret.bottom + margin - viewport).toInt())
            caret.top < scrollState.value ->
                scrollState.scrollTo(caret.top.toInt())
        }
    }

    if (settings.wordWrap) {
        BasicTextField(
            state = state,
            modifier = Modifier.fillMaxSize().then(common),
            inputTransformation = input,
            textStyle = style,
            keyboardOptions = keyboard,
            onTextLayout = { getResult -> reportLayout(getResult) },
            cursorBrush = cursor,
            outputTransformation = output,
            decorator = EditorPadding,
            scrollState = scrollState,
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
                    .then(common),
                inputTransformation = input,
                textStyle = style,
                keyboardOptions = keyboard,
                onTextLayout = { getResult -> reportLayout(getResult) },
                cursorBrush = cursor,
                outputTransformation = output,
                decorator = EditorPadding,
                scrollState = scrollState,
            )
        }
    }
}

/** A small styled Markdown field used to edit one block inside the formatted view. */
@Composable
fun BlockEditorField(state: TextFieldState, settings: ViewSettings, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val output = remember(colors) { MarkdownOutputTransformation(colors) }
    BasicTextField(
        state = state,
        modifier = modifier,
        inputTransformation = MarkdownListContinuation,
        textStyle = editorTextStyle(settings),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        cursorBrush = SolidColor(colors.primary),
        outputTransformation = output,
    )
}
