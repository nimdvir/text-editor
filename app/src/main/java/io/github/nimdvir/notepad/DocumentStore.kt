package io.github.nimdvir.notepad

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.IOException

/** A file or subfolder inside a favorite folder. */
data class FolderItem(
    val documentId: String,
    val uri: Uri,
    val name: String,
    val isFolder: Boolean,
    val lastModified: Long?,
)

/**
 * Reads and writes documents through Android's Storage Access Framework. Google Drive (and any other
 * installed storage app) is just another document provider here, so no Drive-specific code is needed.
 */
class DocumentStore(private val resolver: ContentResolver) {

    class FileTooLargeException(val size: Long) : IOException("File is too large ($size bytes)")

    fun displayName(uri: Uri): String {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Untitled.txt"
    }

    fun size(uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()

    fun read(uri: Uri, maxBytes: Long = HARD_LIMIT_BYTES): DecodedText {
        val bytes = (resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())).use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw FileTooLargeException(total)
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
        return TextCodec.decode(bytes)
    }

    fun write(uri: Uri, bytes: ByteArray) {
        // "wt" truncates; not every provider supports it, so fall back to the other write modes.
        var lastError: Exception? = null
        for (mode in arrayOf("wt", "rwt", "w")) {
            try {
                val out = resolver.openOutputStream(uri, mode) ?: continue
                out.use { it.write(bytes) }
                return
            } catch (e: SecurityException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Could not open $uri for writing")
    }

    /** Keeps access to a picked file across app restarts (needed for Save and Recent files). */
    fun persistPermission(uri: Uri) {
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(uri, rw) }
            .recoverCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    /** Keeps access to a picked folder (and everything in it) across app restarts. */
    fun persistTreePermission(treeUri: Uri) {
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        resolver.takePersistableUriPermission(treeUri, rw)
    }

    fun releaseTreePermission(treeUri: Uri) {
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.releasePersistableUriPermission(treeUri, rw) }
    }

    fun folderName(treeUri: Uri, documentId: String = DocumentsContract.getTreeDocumentId(treeUri)): String =
        displayName(DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId))

    /** Subfolders and text-like files directly inside a folder of a picked tree, folders first. */
    fun listFolder(treeUri: Uri, documentId: String): List<FolderItem> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val items = ArrayList<FolderItem>()
        resolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2).orEmpty()
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                if (!isDir && !isEditable(name, mime)) continue
                items += FolderItem(
                    documentId = id,
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                    name = name,
                    isFolder = isDir,
                    lastModified = if (c.isNull(3)) null else c.getLong(3),
                )
            }
        }
        return items.sortedWith(compareBy<FolderItem> { !it.isFolder }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /** Creates an empty file in a folder of a picked tree and returns its URI. */
    fun createInFolder(treeUri: Uri, documentId: String, name: String): Uri {
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        return DocumentsContract.createDocument(resolver, parent, mimeTypeFor(name), name)
            ?: throw IOException("Could not create $name")
    }

    fun hasPersistedPermission(uri: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    companion object {
        /** Above this we ask before opening: Android text fields get sluggish with very large text. */
        const val WARN_SIZE_BYTES = 1L * 1024 * 1024
        const val HARD_LIMIT_BYTES = 20L * 1024 * 1024

        fun mimeTypeFor(name: String): String {
            val n = name.lowercase()
            return when {
                isMarkdown(n) -> "text/markdown"
                n.endsWith(".csv") -> "text/csv"
                n.endsWith(".tsv") -> "text/tab-separated-values"
                else -> "text/plain"
            }
        }

        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "markdown", "mdown", "mkd", "csv", "tsv", "log", "json", "xml", "yaml", "yml", "ini", "cfg",
            "conf", "html", "htm", "css", "js", "ts", "kt", "java", "py", "sh", "sql", "srt",
        )

        fun isEditable(name: String, mime: String): Boolean =
            mime.startsWith("text/") || name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS

        fun isMarkdown(name: String): Boolean {
            val n = name.lowercase()
            return n.endsWith(".md") || n.endsWith(".markdown") || n.endsWith(".mdown") || n.endsWith(".mkd")
        }
    }
}
