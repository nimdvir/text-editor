package io.github.nimdvir.notepad.ui

import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.nimdvir.notepad.EditorViewModel
import io.github.nimdvir.notepad.FavoriteFolder
import io.github.nimdvir.notepad.FolderItem
import io.github.nimdvir.notepad.HistoryEntry

/** Where you are inside a favorite folder: the folder plus the subfolders you've opened. */
private data class FolderNav(val folder: FavoriteFolder, val path: List<Pair<String, String>>) {
    val documentId get() = path.last().first
    val title get() = path.joinToString(" / ") { it.second }
}

/** Favorites (folders and files) and the history of the last 50 files. */
@Composable
fun LibraryScreen(vm: EditorViewModel, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            var tab by rememberSaveable { mutableIntStateOf(0) }
            var nav by remember { mutableStateOf<FolderNav?>(null) }
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        val n = nav
                        nav = when {
                            n == null -> { onClose(); null }
                            n.path.size > 1 -> n.copy(path = n.path.dropLast(1))
                            else -> null
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        nav?.title ?: "Favorites & history",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val current = nav
                if (current != null) {
                    FolderBrowser(vm, current, onOpenFolder = { item ->
                        nav = current.copy(path = current.path + (item.documentId to item.name))
                    })
                } else {
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Favorites") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History") })
                    }
                    if (tab == 0) {
                        FavoritesTab(vm, onBrowse = { folder ->
                            val rootId = DocumentsContract.getTreeDocumentId(folder.treeUri)
                            nav = FolderNav(folder, listOf(rootId to folder.name))
                        })
                    } else {
                        HistoryTab(vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesTab(vm: EditorViewModel, onBrowse: (FavoriteFolder) -> Unit) {
    val lib = vm.library
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        section("Folders")
        items(lib.favoriteFolders, key = { "folder:" + it.treeUri }) { folder ->
            Entry(
                icon = "📁",
                title = folder.name,
                subtitle = providerLabel(folder.treeUri),
                onClick = { onBrowse(folder) },
                trailing = {
                    IconButton(onClick = { vm.removeFavoriteFolder(folder) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove folder")
                    }
                },
            )
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedButton(onClick = { vm.addFavoriteFolder() }) { Text("+ Add favorite folder") }
                Text(
                    "Phone folders work. Google Drive may not offer whole-folder access; star Drive files instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        section("Files")
        if (lib.favoriteFiles.isEmpty()) {
            item { Hint("Tap ★ next to a file's name to add it here.") }
        }
        items(lib.favoriteFiles, key = { "file:" + it.uri }) { file ->
            Entry(
                icon = "📄",
                title = file.name,
                subtitle = providerLabel(file.uri),
                onClick = { vm.openFromLibrary(file.uri) },
                trailing = { StarButton(true) { vm.toggleFavoriteFile(file.uri, file.name) } },
            )
        }
    }
}

@Composable
private fun HistoryTab(vm: EditorViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val lib = vm.library
    val entries = lib.history.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search history") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            if (lib.history.isNotEmpty()) {
                TextButton(onClick = { vm.clearHistory() }) { Text("Clear") }
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (entries.isEmpty()) item { Hint("Files you open or save show up here (last 50).") }
            items(entries, key = { it.uri.toString() }) { entry ->
                val starred = lib.favoriteFiles.any { it.uri == entry.uri }
                Entry(
                    icon = "📄",
                    title = entry.name,
                    subtitle = historySubtitle(entry),
                    onClick = { vm.openFromLibrary(entry.uri) },
                    trailing = {
                        Row {
                            StarButton(starred) { vm.toggleFavoriteFile(entry.uri, entry.name) }
                            IconButton(onClick = { vm.removeHistory(entry.uri) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove from history")
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FolderBrowser(vm: EditorViewModel, nav: FolderNav, onOpenFolder: (FolderItem) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    var askName by remember { mutableStateOf(false) }
    val listing by produceState<Result<List<FolderItem>>?>(null, nav.documentId, refresh) {
        value = vm.listFolder(nav.folder.treeUri, nav.documentId)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { askName = true }) { Text("Save current file here") }
            TextButton(onClick = { refresh++ }) { Text("Refresh") }
        }
        HorizontalDivider()
        val result = listing
        when {
            result == null -> Hint("Loading…")
            result.isFailure -> Hint("Can't read this folder. It may have been moved, or access was removed.")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                val children = result.getOrThrow()
                if (children.isEmpty()) item { Hint("No text, Markdown or CSV files here.") }
                items(children, key = { it.documentId }) { item ->
                    val starred = vm.library.favoriteFiles.any { it.uri == item.uri }
                    Entry(
                        icon = if (item.isFolder) "📁" else "📄",
                        title = item.name,
                        subtitle = item.lastModified?.let { "Modified " + relative(it) },
                        onClick = { if (item.isFolder) onOpenFolder(item) else vm.openFromLibrary(item.uri) },
                        trailing = if (item.isFolder) null else {
                            { StarButton(starred) { vm.toggleFavoriteFile(item.uri, item.name) } }
                        },
                    )
                }
            }
        }
    }
    if (askName) {
        FileNameDialog(
            initial = vm.doc.name,
            onSave = { name -> askName = false; vm.saveIntoFolder(nav.folder.treeUri, nav.documentId, name) },
            onCancel = { askName = false },
        )
    }
}

@Composable
private fun FileNameDialog(initial: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Save as") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("File name") })
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun Entry(
    icon: String,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun StarButton(starred: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Filled.Star,
            contentDescription = if (starred) "Remove from favorites" else "Add to favorites",
            tint = if (starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

private fun LazyListScope.section(title: String) {
    item(key = "section:$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

private fun historySubtitle(e: HistoryEntry): String = buildString {
    append(providerLabel(e.uri))
    append(" · Opened ").append(relative(e.lastOpened))
    e.lastEdited?.let { append(" · Edited ").append(relative(it)) }
    if (e.temporary) append(" · opened from another app; reopen it there if this fails")
}

private fun relative(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

fun providerLabel(uri: Uri): String {
    val authority = uri.authority.orEmpty()
    return when {
        "google.android.apps.docs" in authority -> "Google Drive"
        authority == "com.android.externalstorage.documents" -> "This device"
        authority == "com.android.providers.downloads.documents" -> "Downloads"
        authority.isEmpty() -> "Unknown location"
        else -> authority.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
