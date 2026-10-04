package io.github.nimdvir.notepad

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Properties

enum class DocType(val untitledName: String, val initialText: String) {
    TEXT("Untitled.txt", ""),
    MARKDOWN("Untitled.md", ""),
    CSV("Untitled.csv", "Column 1,Column 2,Column 3\n,,\n"),
}

/** Edit: plain/styled text. Split: text + formatted side by side. Preview: formatted only. Table: CSV grid. */
enum class ViewMode { EDIT, SPLIT, PREVIEW, TABLE }

/** What is open: where it lives and how to write it back. `uri == null` means never saved. */
data class DocInfo(
    val uri: Uri? = null,
    val name: String = DocType.MARKDOWN.untitledName,
    val encoding: TextEncoding = TextEncoding.UTF8,
    val lineEnding: LineEnding = TextCodec.DEFAULT_LINE_ENDING,
) {
    val type: DocType
        get() = when {
            DocumentStore.isMarkdown(name) -> DocType.MARKDOWN
            Csv.isCsvName(name) -> DocType.CSV
            else -> DocType.TEXT
        }
}

/** Actions that would discard the current text, so they go through the "save changes?" prompt. */
sealed interface PendingAction {
    data class New(val type: DocType) : PendingAction
    data object OpenPicker : PendingAction
    data class OpenUri(val uri: Uri, val temporary: Boolean = false) : PendingAction
    data object Exit : PendingAction
}

sealed interface DialogState {
    data class UnsavedChanges(val then: PendingAction) : DialogState
    data class LargeFile(val uri: Uri, val sizeBytes: Long, val temporary: Boolean) : DialogState
    data object GoTo : DialogState
    data object Library : DialogState
}

sealed interface UiEvent {
    data object LaunchOpenPicker : UiEvent
    data class LaunchSaveAs(val suggestedName: String) : UiEvent
    data object LaunchFolderPicker : UiEvent
    data class Message(val text: String) : UiEvent
    data object FocusEditor : UiEvent
    data object Finish : UiEvent
}

/** A Markdown block being edited inside the formatted view. */
class BlockEdit(val start: Int, val end: Int, val state: TextFieldState)

data class CsvSort(val column: Int, val ascending: Boolean)

@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val store = DocumentStore(app.contentResolver)
    private val prefs = Prefs(app)
    private val libraryStore = Library(File(app.filesDir, "library.json"))
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
        val current = text.text
        when {
            // A new, untouched file (empty or just the starting template) has nothing worth saving.
            doc.uri == null && (current.isBlank() || current.contentEquals(doc.type.initialText)) -> false
            else -> saved == null || formatChanged || !current.contentEquals(saved)
        }
    }

    var settings by mutableStateOf(prefs.loadViewSettings())
        private set
    var library by mutableStateOf(libraryStore.load())
        private set
    var busy by mutableStateOf(false)
        private set
    var mode by mutableStateOf(ViewMode.EDIT)
    var dialog by mutableStateOf<DialogState?>(null)

    /** Status-bar note for saves, e.g. "Saved 10:42". */
    var saveStatus by mutableStateOf<String?>(null)
        private set

    val isFavorite by derivedStateOf { doc.uri != null && library.favoriteFiles.any { it.uri == doc.uri } }

    // Find / Replace bar
    var findVisible by mutableStateOf(false)
        private set
    var replaceVisible by mutableStateOf(false)
        private set
    var findQuery by mutableStateOf("")
    var replaceWith by mutableStateOf("")
    var matchCase by mutableStateOf(false)

    // Markdown
    var blockEdit by mutableStateOf<BlockEdit?>(null)
        private set

    /** Offset of the text at the top of the visible pane, so switching views keeps your place. */
    var anchorOffset = 0

    // CSV table
    var csvHasHeader by mutableStateOf(true)
    var csvSort by mutableStateOf<CsvSort?>(null)

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    /** Runs after a Save / Save As triggered from the unsaved-changes prompt completes. */
    private var afterSave: PendingAction? = null

    private var exiting = false
    private var saving = false

    init {
        restoreDraft()
        viewModelScope.launch {
            snapshotFlow { text.text }.debounce(AUTOSAVE_DELAY_MS).collect { autoSave() }
        }
    }

    // ---- File ----

    fun request(action: PendingAction) {
        commitBlockEdit()
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
            is PendingAction.New -> loadDocument(DocInfo(name = action.type.untitledName), action.type.initialText)
            PendingAction.OpenPicker -> send(UiEvent.LaunchOpenPicker)
            is PendingAction.OpenUri -> openDocument(action.uri, confirmLarge = true, temporary = action.temporary)
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
        openDocument(uri, confirmLarge = true, temporary = false)
    }

    fun openLargeFileAnyway() {
        val d = dialog as? DialogState.LargeFile ?: return
        dialog = null
        openDocument(d.uri, confirmLarge = false, temporary = d.temporary)
    }

    /** Opens a file shared with us by another app (e.g. "Open with" from the Drive app). */
    fun openFromIntent(uri: Uri) {
        store.persistPermission(uri)
        request(PendingAction.OpenUri(uri, temporary = !store.hasPersistedPermission(uri)))
    }

    /** Opens a file from the Library (history, favorites, or a favorite folder). */
    fun openFromLibrary(uri: Uri) {
        dialog = null
        request(PendingAction.OpenUri(uri))
    }

    private fun openDocument(uri: Uri, confirmLarge: Boolean, temporary: Boolean) {
        viewModelScope.launch {
            busy = true
            try {
                val size = withContext(Dispatchers.IO) { store.size(uri) }
                if (confirmLarge && size != null && size > DocumentStore.WARN_SIZE_BYTES) {
                    dialog = DialogState.LargeFile(uri, size, temporary)
                    return@launch
                }
                val (name, decoded) = withContext(Dispatchers.IO) { store.displayName(uri) to store.read(uri) }
                loadDocument(DocInfo(uri, name, decoded.encoding, decoded.lineEnding), decoded.text)
                updateLibrary { Library.recordOpened(it, uri, name, System.currentTimeMillis(), temporary) }
            } catch (e: DocumentStore.FileTooLargeException) {
                send(UiEvent.Message("This file is too large to open (${formatSize(e.size)})."))
            } catch (e: Exception) {
                send(UiEvent.Message("Couldn't open the file: ${e.readable()}"))
            } finally {
                busy = false
            }
        }
    }

    private fun loadDocument(info: DocInfo, content: String) {
        blockEdit = null
        text.edit {
            replace(0, length, content)
            selection = TextRange(0)
        }
        text.undoState.clearHistory()
        doc = info
        savedText = content
        formatChanged = false
        mode = if (info.type == DocType.CSV) ViewMode.TABLE else ViewMode.EDIT
        anchorOffset = 0
        csvSort = null
        csvHasHeader = true
        findVisible = false
        saveStatus = null
        deleteDraft()
    }

    fun save(then: PendingAction? = null) {
        commitBlockEdit()
        val uri = doc.uri
        if (uri == null) {
            afterSave = then
            send(UiEvent.LaunchSaveAs(doc.name))
        } else {
            writeTo(uri, doc.name, then)
        }
    }

    fun saveAs() {
        commitBlockEdit()
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

    /** Saves the current text as a new file [name] inside a favorite folder. */
    fun saveIntoFolder(treeUri: Uri, documentId: String, name: String) {
        commitBlockEdit()
        dialog = null
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) { store.createInFolder(treeUri, documentId, name) }
                val actualName = withContext(Dispatchers.IO) { store.displayName(uri) }
                writeTo(uri, actualName, then = null)
            } catch (e: Exception) {
                send(UiEvent.Message("Couldn't create $name: ${e.readable()}"))
            }
        }
    }

    private fun writeTo(uri: Uri, name: String, then: PendingAction?, quiet: Boolean = false) {
        val content = text.text.toString()
        val info = doc.copy(uri = uri, name = name)
        val typeChanged = info.type != doc.type
        viewModelScope.launch {
            saving = true
            if (quiet) saveStatus = "Saving…" else busy = true
            try {
                withContext(Dispatchers.IO) {
                    store.write(uri, TextCodec.encode(content, info.encoding, info.lineEnding))
                }
                doc = info
                savedText = content
                formatChanged = false
                deleteDraft()
                updateLibrary { Library.recordEdited(it, uri, name, System.currentTimeMillis()) }
                saveStatus = "Saved " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
                if (typeChanged) mode = if (info.type == DocType.CSV) ViewMode.TABLE else ViewMode.EDIT
                if (!quiet) send(UiEvent.Message("Saved $name"))
                then?.let(::perform)
            } catch (e: Exception) {
                if (quiet) {
                    saveStatus = "Auto-save failed"
                    send(UiEvent.Message("Auto-save failed: ${e.readable()}. Use Save as to save a copy."))
                } else {
                    // Typically a file opened read-only (e.g. shared from another app): pick a new place.
                    afterSave = then
                    send(UiEvent.Message("Couldn't save to $name: ${e.readable()}. Choose where to save a copy."))
                    send(UiEvent.LaunchSaveAs(name))
                }
            } finally {
                saving = false
                busy = false
            }
        }
    }

    /** Auto-save: called a moment after typing stops, and when the app goes to the background. */
    fun autoSave() {
        val uri = doc.uri ?: return
        if (!settings.autoSave || !isDirty || saving || blockEdit != null) return
        writeTo(uri, doc.name, then = null, quiet = true)
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

    // ---- Library: favorites and history ----

    private fun updateLibrary(change: (LibraryData) -> LibraryData) {
        library = change(library)
        val snapshot = library
        viewModelScope.launch(Dispatchers.IO) { libraryStore.save(snapshot) }
    }

    fun toggleFavoriteCurrent() {
        val uri = doc.uri
        if (uri == null) {
            send(UiEvent.Message("Save the file first, then star it."))
            return
        }
        toggleFavoriteFile(uri, doc.name)
    }

    fun toggleFavoriteFile(uri: Uri, name: String) = updateLibrary { lib ->
        if (lib.favoriteFiles.any { it.uri == uri }) {
            lib.copy(favoriteFiles = lib.favoriteFiles.filter { it.uri != uri })
        } else {
            lib.copy(favoriteFiles = lib.favoriteFiles + FavoriteFile(uri, name))
        }
    }

    fun addFavoriteFolder() = send(UiEvent.LaunchFolderPicker)

    fun onFolderPicked(treeUri: Uri?) {
        if (treeUri == null) return
        viewModelScope.launch {
            try {
                val name = withContext(Dispatchers.IO) {
                    store.persistTreePermission(treeUri)
                    store.folderName(treeUri)
                }
                updateLibrary { lib ->
                    lib.copy(favoriteFolders = lib.favoriteFolders.filter { it.treeUri != treeUri } + FavoriteFolder(treeUri, name))
                }
            } catch (e: Exception) {
                send(UiEvent.Message("This location doesn't allow folder access: ${e.readable()}"))
            }
        }
    }

    fun removeFavoriteFolder(folder: FavoriteFolder) {
        store.releaseTreePermission(folder.treeUri)
        updateLibrary { lib -> lib.copy(favoriteFolders = lib.favoriteFolders.filter { it.treeUri != folder.treeUri }) }
    }

    suspend fun listFolder(treeUri: Uri, documentId: String): Result<List<FolderItem>> =
        withContext(Dispatchers.IO) { runCatching { store.listFolder(treeUri, documentId) } }

    fun removeHistory(uri: Uri) = updateLibrary { lib -> lib.copy(history = lib.history.filter { it.uri != uri }) }

    fun clearHistory() = updateLibrary { it.copy(history = emptyList()) }

    // ---- Edit ----

    /** The text field the toolbar and menus act on: a Markdown block being edited, or the main text. */
    val activeState: TextFieldState get() = blockEdit?.state ?: text

    fun undo() = activeState.undoState.undo()
    fun redo() = activeState.undoState.redo()
    val canUndo get() = activeState.undoState.canUndo
    val canRedo get() = activeState.undoState.canRedo

    fun replaceSelection(with: String) {
        activeState.edit {
            val start = minOf(selection.start, selection.end)
            val end = maxOf(selection.start, selection.end)
            replace(start, end, with)
            selection = TextRange(start + with.length)
        }
    }

    fun selectedText(): String {
        val s = activeState.selection
        return activeState.text.subSequence(s.min, s.max).toString()
    }

    fun selectAll() {
        activeState.edit { selection = TextRange(0, length) }
        if (blockEdit == null) send(UiEvent.FocusEditor)
    }

    fun insertTimeDate() {
        val now = Date()
        val stamp = DateFormat.getTimeInstance(DateFormat.SHORT).format(now) + " " +
            DateFormat.getDateInstance(DateFormat.SHORT).format(now)
        replaceSelection(stamp)
    }

    /** Applies a Markdown toolbar action to the active text field as one undo step. */
    fun applyMarkdown(action: (text: String, selStart: Int, selEnd: Int) -> TextEdit) {
        val state = activeState
        val sel = state.selection
        val edit = action(state.text.toString(), sel.start, sel.end)
        state.edit {
            replace(edit.start, edit.end, edit.replacement)
            selection = TextRange(edit.selStart, edit.selEnd)
        }
    }

    fun showFind(replace: Boolean) {
        commitBlockEdit()
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
            text.edit {
                replace(sel.min, sel.max, replaceWith)
                selection = TextRange(sel.min + replaceWith.length)
            }
        }
        findNext()
    }

    fun replaceAll() {
        val (result, count) = TextSearch.replaceAll(text.text.toString(), findQuery, replaceWith, matchCase)
        if (count > 0) replaceAllText(result)
        send(UiEvent.Message(if (count == 0) "Cannot find \"$findQuery\"" else "Replaced $count occurrence(s)"))
    }

    fun goToLine(line: Int): Boolean {
        val offset = TextSearch.lineStartOffset(text.text, line) ?: return false
        dialog = null
        selectAndReveal(offset, offset)
        return true
    }

    private fun selectAndReveal(start: Int, end: Int) {
        commitBlockEdit()
        text.edit { selection = TextRange(start, end) }
        if (mode == ViewMode.PREVIEW || mode == ViewMode.TABLE) mode = ViewMode.EDIT
        anchorOffset = start
        send(UiEvent.FocusEditor)
    }

    private fun replaceAllText(newText: String) {
        text.edit {
            replace(0, length, newText)
            selection = TextRange(0)
        }
    }

    // ---- Markdown: editing inside the formatted view ----

    fun startBlockEdit(block: MdBlock) {
        commitBlockEdit()
        blockEdit = BlockEdit(block.start, block.end, TextFieldState(block.source, TextRange(block.source.length)))
    }

    /** Adds a new paragraph at the end of the document and starts editing it. */
    fun startNewBlock() {
        commitBlockEdit()
        val current = text.text
        val prefix = when {
            current.isEmpty() -> ""
            current.endsWith("\n\n") -> ""
            current.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        if (prefix.isNotEmpty()) text.edit { append(prefix) }
        val at = text.text.length
        blockEdit = BlockEdit(at, at, TextFieldState(""))
    }

    /** Writes the block being edited back into the document (one undo step). */
    fun commitBlockEdit() {
        val edit = blockEdit ?: return
        blockEdit = null
        val newSource = edit.state.text.toString()
        val end = edit.end.coerceAtMost(text.text.length)
        val start = edit.start.coerceAtMost(end)
        if (text.text.subSequence(start, end).contentEquals(newSource)) return
        text.edit { replace(start, end, newSource) }
    }

    fun cancelBlockEdit() {
        blockEdit = null
    }

    fun toggleTask(block: MdBlock) {
        commitBlockEdit()
        val edit = MarkdownEdits.toggleTaskAtLine(text.text.toString(), block.start)
        text.edit { replace(edit.start, edit.end, edit.replacement) }
    }

    fun changeMode(newMode: ViewMode) {
        commitBlockEdit()
        mode = newMode
    }

    // ---- CSV table ----

    private fun csvDoc(): CsvDoc = Csv.parse(text.text, Csv.detectDelimiter(text.text, doc.name))

    private fun editCsv(change: (CsvDoc) -> CsvDoc) {
        replaceAllText(Csv.write(change(csvDoc())))
    }

    fun csvSetCell(row: Int, col: Int, value: String) = editCsv { Csv.setCell(it, row, col, value) }
    fun csvInsertRow(at: Int) = editCsv { Csv.insertRow(it, at) }
    fun csvDeleteRow(row: Int) = editCsv { Csv.deleteRow(it, row) }

    fun csvInsertColumn(at: Int) {
        val sort = csvSort
        if (sort != null && sort.column >= at) csvSort = sort.copy(column = sort.column + 1)
        editCsv { Csv.insertColumn(it, at, if (csvHasHeader) "New column" else null) }
    }

    fun csvDeleteColumn(col: Int) {
        val sort = csvSort
        if (sort != null) csvSort = if (sort.column == col) null else if (sort.column > col) sort.copy(column = sort.column - 1) else sort
        editCsv { Csv.deleteColumn(it, col) }
    }

    /** Header tap: ascending → descending → original order. View only; the file is unchanged. */
    fun csvCycleSort(col: Int) {
        val s = csvSort
        csvSort = when {
            s == null || s.column != col -> CsvSort(col, ascending = true)
            s.ascending -> CsvSort(col, ascending = false)
            else -> null
        }
    }

    /** Writes the current sort order into the file. */
    fun csvSaveSortOrder() {
        val sort = csvSort ?: return
        editCsv { d -> Csv.reorder(d, csvHasHeader, Csv.sortedOrder(d, csvHasHeader, sort.column, sort.ascending)) }
        csvSort = null
        send(UiEvent.Message("Rows saved in this order. Undo to revert."))
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

    /** Called from onStop: auto-saves if enabled, and keeps unsaved text on disk in case Android kills the app. */
    fun onBackground() {
        commitBlockEdit()
        autoSave()
        saveDraft()
    }

    private fun saveDraft() {
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
                name = p.getProperty("name") ?: DocType.MARKDOWN.untitledName,
                encoding = TextEncoding.valueOf(p.getProperty("encoding") ?: TextEncoding.UTF8.name),
                lineEnding = LineEnding.valueOf(p.getProperty("lineEnding") ?: TextCodec.DEFAULT_LINE_ENDING.name),
            )
            val content = draftText.readText()
            if (content.isBlank()) return
            text.edit {
                replace(0, length, content)
                selection = TextRange(0)
            }
            text.undoState.clearHistory()
            doc = info
            mode = if (info.type == DocType.CSV) ViewMode.TABLE else ViewMode.EDIT
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
        const val AUTOSAVE_DELAY_MS = 2000L

        fun formatSize(bytes: Long): String = when {
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
            else -> "$bytes bytes"
        }
    }
}
