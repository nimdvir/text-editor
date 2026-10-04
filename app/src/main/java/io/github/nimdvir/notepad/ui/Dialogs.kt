package io.github.nimdvir.notepad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nimdvir.notepad.RecentFile

/** [onChoice]: true = Save, false = Don't save, null = Cancel. */
@Composable
fun UnsavedChangesDialog(name: String, onChoice: (Boolean?) -> Unit) {
    AlertDialog(
        onDismissRequest = { onChoice(null) },
        title = { Text("Notepad") },
        text = { Text("Do you want to save changes to $name?") },
        confirmButton = { TextButton(onClick = { onChoice(true) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onChoice(false) }) { Text("Don't save") }
                TextButton(onClick = { onChoice(null) }) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun LargeFileDialog(size: String, onOpen: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Large file") },
        text = { Text("This file is $size. Editing very large files on a phone can be slow. Open it anyway?") },
        confirmButton = { TextButton(onClick = onOpen) { Text("Open") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/** [onGo] returns false when the line doesn't exist. */
@Composable
fun GoToLineDialog(onGo: (Int) -> Boolean, onCancel: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val submit = { error = !(input.toIntOrNull()?.let(onGo) ?: false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Go To Line") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { v -> input = v.filter { it.isDigit() }.take(9); error = false },
                label = { Text("Line number") },
                singleLine = true,
                isError = error,
                supportingText = if (error) {
                    { Text("The line number is beyond the total number of lines.") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = submit) { Text("Go To") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
fun RecentFilesDialog(
    files: List<RecentFile>,
    onOpen: (RecentFile) -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Recent files") },
        text = {
            if (files.isEmpty()) {
                Text("No recent files.")
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(files, key = { it.uri.toString() }) { file ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(file) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                file.uri.authority.orEmpty().let(::providerLabel),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text("Close") } },
        dismissButton = {
            if (files.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear list") }
        },
    )
}

private fun providerLabel(authority: String): String = when {
    "google.android.apps.docs" in authority -> "Google Drive"
    authority == "com.android.externalstorage.documents" -> "This device"
    authority == "com.android.providers.downloads.documents" -> "Downloads"
    else -> authority
}
