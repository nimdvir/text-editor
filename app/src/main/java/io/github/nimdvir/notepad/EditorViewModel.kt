package io.github.nimdvir.notepad

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Properties

/** What is open: where it lives and how to write it back. `uri == null` means never saved. */
data class DocInfo(
    val uri: Uri? = null,
    val name: String = UNTITLED,
    val encoding: TextEncoding = TextEncoding.UTF8,
    val lineEnding: LineEnding = TextCodec.DEFAULT_LINE_ENDING,
) {
    val isMarkdown get() = DocumentStore.isMarkdown(name)

    companion object {
        const val UNTITLED = "Untitled.txt"
        const val UNTITLED_MD = "Untitled.md"
    }
}

/** Actions that would discard the current text, so they go through the "save changes?" prompt. */
sealed interface PendingAction {
    data class New(val markdown: Boolean) : PendingAction
    data object OpenPicker : PendingAction
    data class OpenUri(val uri: Uri) : PendingAction
    data object Exit : PendingAction
}

sealed interface DialogState {
    data class UnsavedChanges(val then: PendingAction) : DialogState
    data class LargeFile(val uri: Uri, val sizeBytes: Long) : DialogState
    data object GoTo : DialogState
    data object Recent : DialogState
}

sealed interface UiEvent {
    data object LaunchOpenPicker : UiEvent
    data class LaunchSaveAs(val suggestedName: String) : UiEvent
    data class Message(val text: String) : UiEvent
    data object FocusEditor : UiEvent
    data object Finish : UiEvent
}

@OptIn(ExperimentalFoundationApi::class)
class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val store = DocumentStore(app.contentResolver)
    private val prefs = Prefs(app)
    private val draftText = File(app.filesDir, "draft.txt")
    private val draftMeta = File(app.filesDir, "draft.properties")

    val text = TextFieldState()

    var doc by mutableStateOf(DocInfo())
        private set

    /** Text as last saved/opened; null when restored from a draft (always counts as unsaved). */
    private var savedText by mutableStateOf<String?>("")

    /** Encoding or line-ending changed since last save. */
    private var formatChanged by mutableStateOf(false)

    val isDirty by derivedStateOf {
        val saved = savedText
        saved == null || formatChanged || !text.text.contentEquals(saved)
    }

    var settings by mutableStateOf(prefs.loadViewSettings())
        private set
    var recent by mutableStateOf(prefs.loadRecent())
        private set
    var busy by mutableStateOf(false)
        private set
    var preview by mutableStateOf(false)
    var dialog by mutableStateOf<DialogState?>(null)

    // Find / Replace bar
    var findVisible by mutableStateOf(false)
        private set
    var replaceVisible by mutableStateOf(false)
        private set
    var findQuery by mutableStateOf("")
    var replaceWith by mutableStateOf("")
    var matchCase by mutableStateOf(false)

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    /** Runs after a Save / Save As triggered from the unsaved-changes prompt completes. */
    private var afterSave: PendingAction? = null

    private var exiting = false

    init {
        restoreDraft()
    }

    // ---- File ----

    fun request(action: PendingAction) {
        if (isDirty) dialog = DialogState.UnsavedChanges(action) else perform(action)
    }

    fun onUnsavedChoice(save: Boolean?) {
        val pending = (dialog as? DialogState.UnsavedChanges)?.then ?: return
        dialog = null
        when (save) {
            true -> save(then = pending)
            false -> perform(pending)
            null -> Unit // cancel
        }
    }

    private fun perform(action: PendingAction) {
        when (action) {
            is PendingAction.New -> loadDocument(
                DocInfo(name = if (action.markdown) DocInfo.UNTITLED_MD else DocInfo.UNTITLED), "",
            )
            PendingAction.OpenPicker -> send(UiEvent.LaunchOpenPicker)
            is PendingAction.OpenUri -> openDocument(action.uri, confirmLarge = true)
            PendingAction.Exit -> {
                exiting = true // the user chose to discard; don't write a draft on the way out
                deleteDraft()
                send(UiEvent.Finish)
            }
        }
    }

    fun onOpenPicked(uri: Uri?) {
        if (uri == null) return
        store.persistPermission(uri)
        openDocument(uri, confirmLarge = true)
    }

    fun openLargeFileAnyway() {
        val d = dialog as? DialogState.LargeFile ?: return
        dialog = null
        openDocument(d.uri, confirmLarge = false)
    }

    /** Opens a file shared with us by another app (e.g. "Open with" from the Drive app). */
    fun openFromIntent(uri: Uri) {
        store.persistPermission(uri)
        request(PendingAction.OpenUri(uri))
    }

    fun openRecent(file: RecentFile) {
        dialog = null
        request(PendingAction.OpenUri(file.uri))
    }

    fun clearRecent() {
        recent = emptyList()
        prefs.saveRecent(recent)
    }

    private fun openDocument(uri: Uri, confirmLarge: Boolean) {
        viewModelScope.launch {
            busy = true
            try {
                val size = withContext(Dispatchers.IO) { store.size(uri) }
                if (confirmLarge && size != null && size > DocumentStore.WARN_SIZE_BYTES) {
                    dialog = DialogState.LargeFile(uri, size)
                    return@launch
                }
                val (name, decoded) = withContext(Dispatchers.IO) { store.displayName(uri) to store.read(uri) }
                loadDocument(DocInfo(uri, name, decoded.encoding, decoded.lineEnding), decoded.text)
                addRecent(uri, name)
            } catch (e: DocumentStore.FileTooLargeException) {
                send(UiEvent.Message("This file is too large to open (${formatSize(e.size)})."))
            } catch (e: Exception) {
                removeRecent(uri)
                send(UiEvent.Message("Couldn't open the file: ${e.readable()}"))
            } finally {
                busy = false
            }
        }
    }

    private fun loadDocument(info: DocInfo, content: String) {
        text.edit {
            replace(0, length, content)
            selection = TextRange(0)
        }
        text.undoState.clearHistory()
        doc = info
        savedText = content
        formatChanged = false
        preview = false
        findVisible = false
        deleteDraft()
    }

    fun save(then: PendingAction? = null) {
        val uri = doc.uri
        if (uri == null) {
            afterSave = then
            send(UiEvent.LaunchSaveAs(doc.name))
        } else {
            writeTo(uri, doc.name, then)
        }
    }

    fun saveAs() {
        afterSave = null
        send(UiEvent.LaunchSaveAs(doc.name))
    }

    fun onSaveAsPicked(uri: Uri?) {
        val then = afterSave
        afterSave = null
        if (uri == null) return
        store.persistPermission(uri)
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { store.displayName(uri) }
            writeTo(uri, name, then)
        }
    }

    private fun writeTo(uri: Uri, name: String, then: PendingAction?) {
        val content = text.text.toString()
        val info = doc.copy(uri = uri, name = name)
        viewModelScope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    store.write(uri, TextCodec.encode(content, info.encoding, info.lineEnding))
                }
                doc = info
                savedText = content
                formatChanged = false
                deleteDraft()
                addRecent(uri, name)
                send(UiEvent.Message("Saved $name"))
                then?.let(::perform)
            } catch (e: Exception) {
                // Typically a file opened read-only (e.g. shared from another app): let the user pick a new place.
                afterSave = then
                send(UiEvent.Message("Couldn't save to $name: ${e.readable()}. Choose where to save a copy."))
                send(UiEvent.LaunchSaveAs(name))
            } finally {
                busy = false
            }
        }
    }

    fun setEncoding(encoding: TextEncoding) {
        if (encoding == doc.encoding) return
        doc = doc.copy(encoding = encoding)
        formatChanged = true
    }

    fun setLineEnding(lineEnding: LineEnding) {
        if (lineEnding == doc.lineEnding) return
        doc = doc.copy(lineEnding = lineEnding)
        formatChanged = true
    }

    private fun addRecent(uri: Uri, name: String) {
        if (!store.hasPersistedPermission(uri)) return
        recent = (listOf(RecentFile(uri, name)) + recent.filter { it.uri != uri }).take(MAX_RECENT)
        prefs.saveRecent(recent)
    }

    private fun removeRecent(uri: Uri) {
        if (recent.none { it.uri == uri }) return
        recent = recent.filter { it.uri != uri }
        prefs.saveRecent(recent)
    }

    // ---- Edit ----

    fun undo() = text.undoState.undo()
    fun redo() = text.undoState.redo()
    val canUndo get() = text.undoState.canUndo
    val canRedo get() = text.undoState.canRedo

    fun replaceSelection(with: String) {
        text.edit {
            val start = minOf(selection.start, selection.end)
            val end = maxOf(selection.start, selection.end)
            replace(start, end, with)
            selection = TextRange(start + with.length)
        }
    }

    fun selectedText(): String {
        val s = text.selection
        return text.text.subSequence(s.min, s.max).toString()
    }

    fun selectAll() {
        text.edit { selection = TextRange(0, length) }
        send(UiEvent.FocusEditor)
    }

    fun insertTimeDate() {
        val now = Date()
        val stamp = DateFormat.getTimeInstance(DateFormat.SHORT).format(now) + " " +
            DateFormat.getDateInstance(DateFormat.SHORT).format(now)
        replaceSelection(stamp)
    }

    fun showFind(replace: Boolean) {
        findVisible = true
        replaceVisible = replace
        val sel = selectedText()
        if (sel.isNotEmpty() && '\n' !in sel) findQuery = sel
    }

    fun hideFind() {
        findVisible = false
    }

    fun findNext() = find(forward = true)
    fun findPrevious() = find(forward = false)

    private fun find(forward: Boolean): Boolean {
        val q = findQuery
        if (q.isEmpty()) return false
        val sel = text.selection
        val range = if (forward) {
            TextSearch.findNext(text.text, q, sel.max, matchCase)
        } else {
            TextSearch.findPrevious(text.text, q, sel.min, matchCase)
        }
        if (range == null) {
            send(UiEvent.Message("Cannot find \"$q\""))
            return false
        }
        selectAndReveal(range.first, range.last + 1)
        return true
    }

    fun replaceOne() {
        val sel = text.selection
        if (TextSearch.matchesAt(text.text, sel.min until sel.max, findQuery, matchCase)) {
            replaceSelection(replaceWith)
        }
        findNext()
    }

    fun replaceAll() {
        val (result, count) = TextSearch.replaceAll(text.text.toString(), findQuery, replaceWith, matchCase)
        if (count > 0) {
            text.edit {
                replace(0, length, result)
                selection = TextRange(0)
            }
        }
        send(UiEvent.Message(if (count == 0) "Cannot find \"$findQuery\"" else "Replaced $count occurrence(s)"))
    }

    fun goToLine(line: Int): Boolean {
        val offset = TextSearch.lineStartOffset(text.text, line) ?: return false
        dialog = null
        selectAndReveal(offset, offset)
        return true
    }

    private fun selectAndReveal(start: Int, end: Int) {
        text.edit { selection = TextRange(start, end) }
        preview = false
        send(UiEvent.FocusEditor)
    }

    // ---- View ----

    fun updateSettings(change: (ViewSettings) -> ViewSettings) {
        val s = change(settings)
        settings = s.copy(fontSizeSp = s.fontSizeSp.coerceIn(ViewSettings.MIN_FONT_SIZE, ViewSettings.MAX_FONT_SIZE))
        prefs.saveViewSettings(settings)
    }

    fun zoomIn() = updateSettings { it.copy(fontSizeSp = it.fontSizeSp + 2) }
    fun zoomOut() = updateSettings { it.copy(fontSizeSp = it.fontSizeSp - 2) }
    fun zoomReset() = updateSettings { it.copy(fontSizeSp = ViewSettings.DEFAULT_FONT_SIZE) }

    // ---- Unsaved work survives the app being closed by the system ----

    /** Called from onStop. Keeps unsaved text on disk so it comes back if Android kills the app. */
    fun saveDraft() {
        if (exiting || !isDirty) {
            deleteDraft()
            return
        }
        val content = text.text.toString()
        val info = doc
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                draftText.writeText(content)
                val p = Properties()
                info.uri?.let { p["uri"] = it.toString() }
                p["name"] = info.name
                p["encoding"] = info.encoding.name
                p["lineEnding"] = info.lineEnding.name
                draftMeta.outputStream().use { p.store(it, null) }
            }
        }
    }

    private fun restoreDraft() {
        if (!draftText.exists() || !draftMeta.exists()) return
        runCatching {
            val p = Properties().apply { draftMeta.inputStream().use { load(it) } }
            val info = DocInfo(
                uri = p.getProperty("uri")?.let(Uri::parse),
                name = p.getProperty("name") ?: DocInfo.UNTITLED,
                encoding = TextEncoding.valueOf(p.getProperty("encoding") ?: TextEncoding.UTF8.name),
                lineEnding = LineEnding.valueOf(p.getProperty("lineEnding") ?: TextCodec.DEFAULT_LINE_ENDING.name),
            )
            val content = draftText.readText()
            text.edit {
                replace(0, length, content)
                selection = TextRange(0)
            }
            text.undoState.clearHistory()
            doc = info
            savedText = null
        }
    }

    private fun deleteDraft() {
        draftText.delete()
        draftMeta.delete()
    }

    private fun send(event: UiEvent) {
        _events.trySend(event)
    }

    private fun Exception.readable(): String = when (this) {
        is SecurityException -> "permission denied"
        is java.io.FileNotFoundException -> "file not found or not available offline"
        else -> message ?: javaClass.simpleName
    }

    companion object {
        const val MAX_RECENT = 10

        fun formatSize(bytes: Long): String = when {
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
            else -> "$bytes bytes"
        }
    }
}
