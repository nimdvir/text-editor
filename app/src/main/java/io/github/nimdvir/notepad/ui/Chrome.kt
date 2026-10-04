package io.github.nimdvir.notepad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nimdvir.notepad.DialogState
import io.github.nimdvir.notepad.EditorViewModel
import io.github.nimdvir.notepad.LineEnding
import io.github.nimdvir.notepad.PendingAction
import io.github.nimdvir.notepad.TextEncoding
import io.github.nimdvir.notepad.TextSearch
import io.github.nimdvir.notepad.ViewSettings
import kotlin.math.roundToInt

@Composable
fun TitleBar(vm: EditorViewModel) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(48.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = (if (vm.isDirty) "*" else "") + vm.doc.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (vm.doc.isMarkdown || vm.preview) {
                TextButton(onClick = { vm.preview = !vm.preview }) {
                    Text(if (vm.preview) "Edit" else "Preview")
                }
            }
            if (vm.isDirty) {
                TextButton(onClick = { vm.save() }) { Text("Save") }
            }
        }
    }
}

@Composable
fun MenuBar(vm: EditorViewModel, onPaste: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Menu("File") { close ->
                Item("New", "Ctrl+N") { close(); vm.request(PendingAction.New(markdown = false)) }
                Item("New Markdown file") { close(); vm.request(PendingAction.New(markdown = true)) }
                Item("Open…", "Ctrl+O") { close(); vm.request(PendingAction.OpenPicker) }
                Item("Recent files…", enabled = vm.recent.isNotEmpty()) { close(); vm.dialog = DialogState.Recent }
                HorizontalDivider()
                Item("Save", "Ctrl+S") { close(); vm.save() }
                Item("Save as…", "Ctrl+Shift+S") { close(); vm.saveAs() }
                HorizontalDivider()
                Item("Exit") { close(); vm.request(PendingAction.Exit) }
            }
            Menu("Edit") { close ->
                val hasSelection = !vm.text.selection.collapsed
                Item("Undo", "Ctrl+Z", enabled = vm.canUndo) { close(); vm.undo() }
                Item("Redo", "Ctrl+Y", enabled = vm.canRedo) { close(); vm.redo() }
                HorizontalDivider()
                Item("Cut", "Ctrl+X", enabled = hasSelection) {
                    close()
                    clipboard.setText(AnnotatedString(vm.selectedText()))
                    vm.replaceSelection("")
                }
                Item("Copy", "Ctrl+C", enabled = hasSelection) {
                    close()
                    clipboard.setText(AnnotatedString(vm.selectedText()))
                }
                Item("Paste", "Ctrl+V") {
                    close()
                    clipboard.getText()?.text?.let(onPaste)
                }
                Item("Delete", "Del", enabled = hasSelection) { close(); vm.replaceSelection("") }
                HorizontalDivider()
                Item("Find…", "Ctrl+F") { close(); vm.showFind(replace = false) }
                Item("Find next", "F3") { close(); vm.findNext() }
                Item("Find previous", "Shift+F3") { close(); vm.findPrevious() }
                Item("Replace…", "Ctrl+H") { close(); vm.showFind(replace = true) }
                Item("Go to…", "Ctrl+G") { close(); vm.dialog = DialogState.GoTo }
                HorizontalDivider()
                Item("Select all", "Ctrl+A") { close(); vm.selectAll() }
                Item("Time/Date", "F5") { close(); vm.insertTimeDate() }
            }
            Menu("View") { close ->
                val s = vm.settings
                Item("Zoom in", "Ctrl+Plus") { vm.zoomIn() }
                Item("Zoom out", "Ctrl+Minus") { vm.zoomOut() }
                Item("Restore default zoom", "Ctrl+0") { vm.zoomReset() }
                HorizontalDivider()
                Item("Word wrap", checked = s.wordWrap) { close(); vm.updateSettings { it.copy(wordWrap = !it.wordWrap) } }
                Item("Status bar", checked = s.statusBar) { close(); vm.updateSettings { it.copy(statusBar = !it.statusBar) } }
                Item("Monospace font", checked = s.monospace) { close(); vm.updateSettings { it.copy(monospace = !it.monospace) } }
                Item("Markdown preview", checked = vm.preview) { close(); vm.preview = !vm.preview }
            }
        }
    }
}

@Composable
fun StatusBar(vm: EditorViewModel) {
    val state = vm.text
    val position by remember(state) { derivedStateOf { TextSearch.lineAndColumn(state.text, state.selection.end) } }
    val chars by remember(state) { derivedStateOf { state.text.length } }
    val zoom = (vm.settings.fontSizeSp / ViewSettings.DEFAULT_FONT_SIZE * 100).roundToInt()
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .height(32.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusText("Ln ${position.first}, Col ${position.second}")
            StatusText("$chars characters")
            StatusText("$zoom%")
            StatusPicker(vm.doc.lineEnding.label, LineEnding.entries.map { it to it.label }, vm::setLineEnding)
            StatusPicker(vm.doc.encoding.label, TextEncoding.entries.map { it to it.label }, vm::setEncoding)
        }
    }
}

@Composable
private fun StatusText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = modifier)
}

/** A status-bar field that opens a menu when tapped (line endings, encoding). */
@Composable
private fun <T> StatusPicker(current: String, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        StatusText(current, Modifier.clickable { open = true }.padding(vertical = 6.dp))
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((value, label) in options) {
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { CheckMark(label == current) },
                    onClick = { open = false; onPick(value) },
                )
            }
        }
    }
}

class MenuScope(val close: () -> Unit)

@Composable
private fun Menu(title: String, content: @Composable MenuScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(title, fontWeight = FontWeight.Normal) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val close = { open = false }
            MenuScope(close).content(close)
        }
    }
}

@Composable
private fun MenuScope.Item(
    label: String,
    shortcut: String? = null,
    enabled: Boolean = true,
    checked: Boolean? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = if (checked != null) {
            { CheckMark(checked) }
        } else {
            null
        },
        trailingIcon = if (shortcut != null) {
            {
                Text(
                    shortcut,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun CheckMark(checked: Boolean) {
    if (checked) Icon(Icons.Filled.Check, contentDescription = "Checked") else Spacer(Modifier.size(24.dp))
}
