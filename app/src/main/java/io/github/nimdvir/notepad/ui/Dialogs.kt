package io.github.nimdvir.notepad.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
