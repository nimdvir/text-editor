package io.github.nimdvir.notepad.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nimdvir.notepad.Csv
import io.github.nimdvir.notepad.CsvDoc
import io.github.nimdvir.notepad.CsvSort
import io.github.nimdvir.notepad.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val RowNumberWidth = 48.dp
private val CellHeight = 40.dp

private data class CellRef(val row: Int, val col: Int, val value: String, val isHeader: Boolean)

/**
 * CSV as a table. Sorting only changes what you see; the file changes only when you edit a cell,
 * add/delete rows or columns, or choose "Save in this order".
 */
@Composable
fun CsvTable(vm: EditorViewModel) {
    val source = vm.text.text
    val name = vm.doc.name
    val doc by produceState<CsvDoc?>(null, source, name) {
        value = withContext(Dispatchers.Default) { Csv.parse(source, Csv.detectDelimiter(source, name)) }
    }
    val d = doc ?: return
    val hasHeader = vm.csvHasHeader && d.rows.isNotEmpty()
    val columns = maxOf(d.columnCount, 1)
    val sort = vm.csvSort
    val order = remember(d, sort, hasHeader) {
        if (sort != null) Csv.sortedOrder(d, hasHeader, sort.column, sort.ascending)
        else ((if (hasHeader) 1 else 0) until d.rows.size).toList()
    }
    val widths = remember(d, columns) { columnWidths(d, columns) }
    val hScroll = rememberScrollState()
    var editing by remember { mutableStateOf<CellRef?>(null) }

    Column(Modifier.fillMaxSize()) {
        if (sort != null) SortBanner(vm, d, hasHeader, sort)
        HeaderRow(vm, d, hasHeader, columns, widths, hScroll, sort) { col ->
            if (hasHeader) editing = CellRef(0, col, d.cell(0, col), isHeader = true)
        }
        HorizontalDivider()
        if (order.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = { vm.csvInsertRow(d.rows.size) }) { Text("+ Add a row") }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(order) { row ->
                DataRow(vm, d, row, columns, widths, hScroll) { col ->
                    editing = CellRef(row, col, d.cell(row, col), isHeader = false)
                }
            }
            if (order.isNotEmpty()) {
                item {
                    TextButton(onClick = { vm.csvInsertRow(d.rows.size) }, modifier = Modifier.padding(8.dp)) {
                        Text("+ Add row")
                    }
                }
            }
        }
    }

    editing?.let { cell ->
        EditCellDialog(
            title = if (cell.isHeader) "Column name" else "Row ${cell.row + 1}, ${columnLabel(d, hasHeader, cell.col)}",
            initial = cell.value,
            onSave = { vm.csvSetCell(cell.row, cell.col, it); editing = null },
            onCancel = { editing = null },
        )
    }
}

@Composable
private fun SortBanner(vm: EditorViewModel, d: CsvDoc, hasHeader: Boolean, sort: CsvSort) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Sorted by ${columnLabel(d, hasHeader, sort.column)} ${if (sort.ascending) "↑" else "↓"} — view only",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.csvSaveSortOrder() }) { Text("Save order") }
            TextButton(onClick = { vm.csvSort = null }) { Text("Clear") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeaderRow(
    vm: EditorViewModel,
    d: CsvDoc,
    hasHeader: Boolean,
    columns: Int,
    widths: List<Dp>,
    hScroll: ScrollState,
    sort: CsvSort?,
    onRename: (Int) -> Unit,
) {
    Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Box(Modifier.width(RowNumberWidth).height(CellHeight).gridBorder(), contentAlignment = Alignment.Center) {
            Text("#", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.horizontalScroll(hScroll)) {
            for (col in 0 until columns) {
                var menu by remember { mutableStateOf(false) }
                val arrow = if (sort != null && sort.column == col) (if (sort.ascending) " ↑" else " ↓") else ""
                Box(
                    Modifier
                        .width(widths[col])
                        .height(CellHeight)
                        .gridBorder()
                        .combinedClickable(onClick = { vm.csvCycleSort(col) }, onLongClick = { menu = true })
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        columnLabel(d, hasHeader, col) + arrow,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Sort A→Z / 1→9") }, onClick = {
                            menu = false; vm.csvSort = CsvSort(col, ascending = true)
                        })
                        DropdownMenuItem(text = { Text("Sort Z→A / 9→1") }, onClick = {
                            menu = false; vm.csvSort = CsvSort(col, ascending = false)
                        })
                        if (hasHeader) {
                            DropdownMenuItem(text = { Text("Rename column") }, onClick = { menu = false; onRename(col) })
                        }
                        DropdownMenuItem(text = { Text("Insert column left") }, onClick = { menu = false; vm.csvInsertColumn(col) })
                        DropdownMenuItem(text = { Text("Insert column right") }, onClick = { menu = false; vm.csvInsertColumn(col + 1) })
                        DropdownMenuItem(text = { Text("Delete column") }, onClick = { menu = false; vm.csvDeleteColumn(col) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DataRow(
    vm: EditorViewModel,
    d: CsvDoc,
    row: Int,
    columns: Int,
    widths: List<Dp>,
    hScroll: ScrollState,
    onEdit: (Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row {
        Box(
            Modifier
                .width(RowNumberWidth)
                .heightIn(min = CellHeight)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .gridBorder()
                .combinedClickable(onClick = { menu = true }, onLongClick = { menu = true }),
            contentAlignment = Alignment.Center,
        ) {
            Text("${row + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Insert row above") }, onClick = { menu = false; vm.csvInsertRow(row) })
                DropdownMenuItem(text = { Text("Insert row below") }, onClick = { menu = false; vm.csvInsertRow(row + 1) })
                DropdownMenuItem(text = { Text("Delete row") }, onClick = { menu = false; vm.csvDeleteRow(row) })
            }
        }
        Row(Modifier.horizontalScroll(hScroll)) {
            for (col in 0 until columns) {
                Box(
                    Modifier
                        .width(widths[col])
                        .heightIn(min = CellHeight)
                        .gridBorder()
                        .combinedClickable(onClick = { onEdit(col) })
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(d.cell(row, col), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EditCellDialog(title: String, initial: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun Modifier.gridBorder(): Modifier =
    border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

private fun columnLabel(d: CsvDoc, hasHeader: Boolean, col: Int): String {
    val name = if (hasHeader) d.cell(0, col).trim() else ""
    return name.ifEmpty { spreadsheetLetters(col) }
}

/** A, B, …, Z, AA, AB, … like a spreadsheet. */
private fun spreadsheetLetters(index: Int): String {
    var n = index + 1
    val sb = StringBuilder()
    while (n > 0) {
        val rem = (n - 1) % 26
        sb.append('A' + rem)
        n = (n - 1) / 26
    }
    return sb.reverse().toString()
}

/** Column widths from the longest values (first 300 rows), clamped so one huge cell can't dominate. */
private fun columnWidths(d: CsvDoc, columns: Int): List<Dp> = (0 until columns).map { col ->
    val longest = d.rows.asSequence().take(300).maxOfOrNull { row ->
        row.getOrNull(col)?.value?.lineSequence()?.firstOrNull()?.length ?: 0
    } ?: 0
    (longest.coerceIn(4, 32) * 8 + 24).dp
}
