package io.github.nimdvir.notepad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.nimdvir.notepad.EditorViewModel

@Composable
fun FindReplaceBar(vm: EditorViewModel) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = vm.findQuery,
                    onValueChange = { vm.findQuery = it },
                    placeholder = { Text("Find") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.findNext() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.findPrevious() }) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Find previous")
                }
                IconButton(onClick = { vm.findNext() }) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Find next")
                }
                IconButton(onClick = { vm.hideFind() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close find")
                }
            }
            if (vm.replaceVisible) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = vm.replaceWith,
                        onValueChange = { vm.replaceWith = it },
                        placeholder = { Text("Replace with") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.replaceOne() }) { Text("Replace") }
                    TextButton(onClick = { vm.replaceAll() }) { Text("All") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = vm.matchCase, onCheckedChange = { vm.matchCase = it })
                Text("Match case", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
