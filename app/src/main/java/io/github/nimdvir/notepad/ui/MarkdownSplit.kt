package io.github.nimdvir.notepad.ui

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import io.github.nimdvir.notepad.EditorViewModel
import io.github.nimdvir.notepad.MarkdownBlocks
import io.github.nimdvir.notepad.MdBlock
import io.github.nimdvir.notepad.SplitLayout
import io.github.nimdvir.notepad.ViewMode
import kotlinx.coroutines.flow.collectLatest

/** Holds the editor's latest text layout without triggering recomposition. */
private class LayoutRef {
    var get: (() -> TextLayoutResult?)? = null
    fun layout(): TextLayoutResult? = get?.invoke()
}

/**
 * Markdown workspace: the styled editor, the formatted view, or both side by side. In split view the two
 * scroll together; switching views keeps your place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarkdownWorkspace(vm: EditorViewModel, editorFocus: FocusRequester) {
    val editorScroll = rememberScrollState()
    val listState = rememberLazyListState()
    val layoutRef = remember { LayoutRef() }
    val blocks by remember { derivedStateOf { MarkdownBlocks.parse(vm.text.text) } }
    val mode = vm.mode
    val split = mode == ViewMode.SPLIT
    val sync by rememberUpdatedState(split && vm.settings.syncScroll)
    val currentBlocks by rememberUpdatedState(blocks)

    val editorPane: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier) {
            Editor(
                state = vm.text,
                settings = vm.settings,
                focusRequester = editorFocus,
                markdown = true,
                scrollState = editorScroll,
                onLayout = { layoutRef.get = it },
                onFocused = { vm.commitBlockEdit() },
            )
        }
        LaunchedEffect(Unit) {
            // Restore the reading position once the text has been laid out.
            repeat(20) {
                withFrameNanos { }
                val layout = layoutRef.layout() ?: return@repeat
                editorScroll.scrollTo(offsetToY(layout, vm.anchorOffset).toInt())
                return@LaunchedEffect
            }
        }
        LaunchedEffect(Unit) {
            snapshotFlow { editorScroll.value }.collectLatest { y ->
                val layout = layoutRef.layout() ?: return@collectLatest
                if (listState.isScrollInProgress && sync) return@collectLatest // the preview is driving
                vm.anchorOffset = layout.getLineStart(layout.getLineForVerticalPosition(y.toFloat()))
                if (sync) syncPreviewFromEditor(layout, y.toFloat(), currentBlocks, listState)
            }
        }
    }

    val previewPane: @Composable (Modifier) -> Unit = { modifier ->
        MarkdownPreviewList(vm, blocks, listState, modifier)
        LaunchedEffect(Unit) {
            val index = MarkdownBlocks.indexAt(blocks, vm.anchorOffset)
            if (index >= 0 && !split) listState.scrollToItem(index)
        }
        LaunchedEffect(Unit) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collectLatest { (index, offset) ->
                    if (editorScroll.isScrollInProgress && sync) return@collectLatest // the editor is driving
                    val b = currentBlocks.getOrNull(index) ?: return@collectLatest
                    if (!sync) {
                        vm.anchorOffset = b.start
                        return@collectLatest
                    }
                    if (!listState.isScrollInProgress) return@collectLatest // moved by the editor, not the user
                    vm.anchorOffset = b.start
                    val layout = layoutRef.layout() ?: return@collectLatest
                    val size = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: return@collectLatest
                    val top = offsetToY(layout, b.start)
                    val bottom = offsetToY(layout, b.end, bottom = true)
                    val y = top + (bottom - top) * (offset.toFloat() / size.coerceAtLeast(1))
                    editorScroll.scrollTo(y.toInt())
                }
        }
    }

    when (mode) {
        ViewMode.PREVIEW -> previewPane(Modifier.fillMaxSize())
        // Never decide by the free space: the keyboard would flip the layout, which rebuilds the editor,
        // drops focus and closes the keyboard again.
        ViewMode.SPLIT -> Box(Modifier.fillMaxSize()) {
            if (isSideBySide(vm.settings.splitLayout)) {
                Row(Modifier.fillMaxSize()) {
                    editorPane(Modifier.weight(1f))
                    VerticalDivider()
                    previewPane(Modifier.weight(1f))
                }
            } else {
                // While typing, give the text more room than the formatted view.
                val typing = WindowInsets.isImeVisible
                Column(Modifier.fillMaxSize()) {
                    editorPane(Modifier.weight(if (typing) 0.6f else 1f))
                    HorizontalDivider(thickness = 2.dp)
                    previewPane(Modifier.weight(if (typing) 0.4f else 1f))
                }
            }
        }
        else -> editorPane(Modifier.fillMaxSize())
    }
}

/** Whether Split view shows the panes side by side for this setting (Automatic = when the phone is sideways). */
@Composable
fun isSideBySide(layout: SplitLayout): Boolean = when (layout) {
    SplitLayout.SIDE_BY_SIDE -> true
    SplitLayout.STACKED -> false
    SplitLayout.AUTO -> LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
}

private fun offsetToY(layout: TextLayoutResult, offset: Int, bottom: Boolean = false): Float {
    val length = layout.layoutInput.text.length
    val line = layout.getLineForOffset(offset.coerceIn(0, length))
    return if (bottom) layout.getLineBottom(line) else layout.getLineTop(line)
}

private suspend fun syncPreviewFromEditor(layout: TextLayoutResult, y: Float, blocks: List<MdBlock>, list: LazyListState) {
    val offset = layout.getLineStart(layout.getLineForVerticalPosition(y))
    val index = MarkdownBlocks.indexAt(blocks, offset)
    if (index < 0) return
    val b = blocks[index]
    val top = offsetToY(layout, b.start)
    val bottom = offsetToY(layout, b.end, bottom = true)
    val fraction = ((y - top) / (bottom - top).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val size = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
    list.scrollToItem(index, (fraction * size).toInt())
}

/** The formatted view: one row per block. Tap a block to edit it; tap a checkbox to tick it. */
@Composable
fun MarkdownPreviewList(vm: EditorViewModel, blocks: List<MdBlock>, listState: LazyListState, modifier: Modifier) {
    val editing = vm.blockEdit
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(blocks) { _, block ->
            if (editing != null && editing.start == block.start) {
                BlockEditorCard(vm)
            } else {
                BlockView(vm, block)
            }
        }
        item {
            val newBlock = editing != null && blocks.none { it.start == editing.start }
            if (newBlock) {
                BlockEditorCard(vm)
            } else {
                TextButton(onClick = { vm.startNewBlock() }) {
                    Text(if (blocks.isEmpty()) "Tap to start writing" else "+ Add text")
                }
            }
        }
    }
}

@Composable
private fun BlockView(vm: EditorViewModel, block: MdBlock) {
    val edit = Modifier
        .fillMaxWidth()
        .clickable { vm.startBlockEdit(block) }
        .padding(vertical = 2.dp)
    when (block.kind) {
        MdBlock.Kind.LIST_ITEM -> Row(
            edit.padding(start = (block.indent * 20).dp),
            verticalAlignment = Alignment.Top,
        ) {
            when {
                block.task != null -> CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Checkbox(
                        checked = block.task,
                        onCheckedChange = { vm.toggleTask(block) },
                        modifier = Modifier.padding(end = 8.dp, top = 2.dp),
                    )
                }
                else -> Text(
                    if (block.marker.first().isDigit()) block.marker else "•",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(28.dp).padding(top = 2.dp),
                )
            }
            Markdown(block.content, modifier = Modifier.weight(1f))
        }
        MdBlock.Kind.RULE -> Box(edit.padding(vertical = 8.dp)) { HorizontalDivider() }
        else -> Box(edit) { Markdown(block.source, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun BlockEditorCard(vm: EditorViewModel) {
    val edit = vm.blockEdit ?: return
    val focus = remember { FocusRequester() }
    val hadFocus = remember { booleanArrayOf(false) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(8.dp)) {
            BlockEditorField(
                state = edit.state,
                settings = vm.settings,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged {
                        // Leaving the block saves it into the document.
                        if (hadFocus[0] && !it.isFocused) vm.commitBlockEdit()
                        hadFocus[0] = it.isFocused
                    },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { vm.cancelBlockEdit() }) { Text("Cancel") }
                TextButton(onClick = { vm.commitBlockEdit() }) { Text("Done") }
            }
        }
    }
    LaunchedEffect(edit) { runCatching { focus.requestFocus() } }
}
