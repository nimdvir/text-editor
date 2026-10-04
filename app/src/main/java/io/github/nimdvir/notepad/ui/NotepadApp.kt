package io.github.nimdvir.notepad.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import io.github.nimdvir.notepad.DialogState
import io.github.nimdvir.notepad.DocumentStore
import io.github.nimdvir.notepad.EditorViewModel
import io.github.nimdvir.notepad.PendingAction
import io.github.nimdvir.notepad.UiEvent
import kotlinx.coroutines.launch

/** MIME types offered in the Open picker. Drive sometimes reports .md files as octet-stream. */
private val OPEN_MIME_TYPES = arrayOf("text/*", "application/octet-stream", "application/x-markdown")

/** Like [ActivityResultContracts.CreateDocument] but picks the MIME type from the file name. */
private class CreateTextDocument : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(DocumentStore.mimeTypeFor(input))
            .putExtra(Intent.EXTRA_TITLE, input)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

@Composable
fun NotepadApp(vm: EditorViewModel, finish: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val editorFocus = remember { FocusRequester() }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        vm.onOpenPicked(it)
    }
    val saveAsLauncher = rememberLauncherForActivityResult(CreateTextDocument()) {
        vm.onSaveAsPicked(it)
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                UiEvent.LaunchOpenPicker -> openLauncher.launch(OPEN_MIME_TYPES)
                is UiEvent.LaunchSaveAs -> saveAsLauncher.launch(event.suggestedName)
                is UiEvent.Message -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(event.text)
                }
                UiEvent.FocusEditor -> {
                    withFrameNanos { } // let the editor appear if we just left preview mode
                    runCatching { editorFocus.requestFocus() }
                }
                UiEvent.Finish -> finish()
            }
        }
    }

    BackHandler {
        when {
            vm.findVisible -> vm.hideFind()
            vm.preview -> vm.preview = false
            else -> vm.request(PendingAction.Exit)
        }
    }

    Scaffold(
        modifier = Modifier.onPreviewKeyEvent { handleShortcut(it, vm) },
        topBar = {
            Column {
                TitleBar(vm)
                MenuBar(vm, onPaste = { vm.replaceSelection(it) })
                HorizontalDivider()
                if (vm.findVisible) FindReplaceBar(vm)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(Modifier.weight(1f)) {
                if (vm.preview) {
                    MarkdownPreview(vm.text.text.toString())
                } else {
                    Editor(vm.text, vm.settings, editorFocus)
                }
            }
            if (vm.settings.statusBar) {
                HorizontalDivider()
                StatusBar(vm)
            }
        }
    }

    when (val d = vm.dialog) {
        is DialogState.UnsavedChanges -> UnsavedChangesDialog(vm.doc.name, vm::onUnsavedChoice)
        is DialogState.LargeFile -> LargeFileDialog(
            size = EditorViewModel.formatSize(d.sizeBytes),
            onOpen = vm::openLargeFileAnyway,
            onCancel = { vm.dialog = null },
        )
        DialogState.GoTo -> GoToLineDialog(
            onGo = vm::goToLine,
            onCancel = { vm.dialog = null },
        )
        DialogState.Recent -> RecentFilesDialog(
            files = vm.recent,
            onOpen = vm::openRecent,
            onClear = vm::clearRecent,
            onCancel = { vm.dialog = null },
        )
        null -> Unit
    }
}

/** Windows Notepad keyboard shortcuts, for phones/tablets with a hardware keyboard. */
private fun handleShortcut(e: KeyEvent, vm: EditorViewModel): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val ctrl = e.isCtrlPressed
    val shift = e.isShiftPressed
    when {
        ctrl && shift && e.key == Key.S -> vm.saveAs()
        ctrl && e.key == Key.S -> vm.save()
        ctrl && e.key == Key.O -> vm.request(PendingAction.OpenPicker)
        ctrl && e.key == Key.N -> vm.request(PendingAction.New(markdown = false))
        ctrl && e.key == Key.F -> vm.showFind(replace = false)
        ctrl && e.key == Key.H -> vm.showFind(replace = true)
        ctrl && e.key == Key.G -> vm.dialog = DialogState.GoTo
        ctrl && (e.key == Key.Equals || e.key == Key.Plus || e.key == Key.NumPadAdd) -> vm.zoomIn()
        ctrl && (e.key == Key.Minus || e.key == Key.NumPadSubtract) -> vm.zoomOut()
        ctrl && (e.key == Key.Zero || e.key == Key.NumPad0) -> vm.zoomReset()
        e.key == Key.F3 && shift -> vm.findPrevious()
        e.key == Key.F3 -> vm.findNext()
        e.key == Key.F5 -> vm.insertTimeDate()
        e.key == Key.Escape && vm.findVisible -> vm.hideFind()
        else -> return false
    }
    return true
}
