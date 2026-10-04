package io.github.nimdvir.notepad

/** One cell. [quoted] remembers whether it was quoted in the file, so rewriting doesn't change other cells. */
data class CsvField(val value: String, val quoted: Boolean = false)

data class CsvDoc(
    val rows: List<List<CsvField>>,
    val delimiter: Char,
    val trailingNewline: Boolean,
) {
    val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0

    fun cell(row: Int, col: Int): String = rows.getOrNull(row)?.getOrNull(col)?.value.orEmpty()
}

/** RFC 4180 CSV reading and writing, plus the table view's sort and edit operations. */
object Csv {
    private val CANDIDATES = charArrayOf(',', ';', '\t', '|')

    fun isCsvName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".csv") || n.endsWith(".tsv")
    }

    /** Picks the delimiter that splits the first lines most consistently. */
    fun detectDelimiter(text: CharSequence, fileName: String? = null): Char {
        if (fileName?.lowercase()?.endsWith(".tsv") == true) return '\t'
        val sample = text.take(16_384).toString()
        var best = ','
        var bestScore = 0.0
        for (d in CANDIDATES) {
            val counts = sampleLines(sample).map { countOutsideQuotes(it, d) }
            if (counts.isEmpty() || counts.first() == 0) continue
            val consistent = counts.count { it == counts.first() }.toDouble() / counts.size
            val score = counts.first() * consistent
            if (score > bestScore) {
                bestScore = score
                best = d
            }
        }
        return best
    }

    fun parse(text: CharSequence, delimiter: Char = detectDelimiter(text)): CsvDoc {
        val rows = ArrayList<List<CsvField>>()
        var row = ArrayList<CsvField>()
        val field = StringBuilder()
        var quoted = false
        var inQuotes = false
        var i = 0
        val n = text.length
        var rowHasContent = false

        fun endField() {
            row += CsvField(field.toString(), quoted)
            field.setLength(0)
            quoted = false
        }
        fun endRow() {
            endField()
            rows += row
            row = ArrayList()
            rowHasContent = false
        }

        while (i < n) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> if (field.isEmpty() && !quoted) {
                        inQuotes = true
                        quoted = true
                    } else {
                        field.append(c)
                    }
                    delimiter -> endField()
                    '\n' -> endRow()
                    '\r' -> Unit
                    else -> field.append(c)
                }
                rowHasContent = true
            }
            i++
        }
        val trailingNewline = n > 0 && text[n - 1] == '\n'
        if (!trailingNewline && (rowHasContent || field.isNotEmpty() || row.isNotEmpty() || quoted)) endRow()
        return CsvDoc(rows, delimiter, trailingNewline)
    }

    fun write(doc: CsvDoc): String {
        val sb = StringBuilder()
        doc.rows.forEachIndexed { r, row ->
            if (r > 0) sb.append('\n')
            row.forEachIndexed { c, f ->
                if (c > 0) sb.append(doc.delimiter)
                if (f.quoted || needsQuotes(f.value, doc.delimiter)) {
                    sb.append('"').append(f.value.replace("\"", "\"\"")).append('"')
                } else {
                    sb.append(f.value)
                }
            }
        }
        if (doc.trailingNewline && doc.rows.isNotEmpty()) sb.append('\n')
        return sb.toString()
    }

    /**
     * Display order of the data rows (indices into doc.rows, skipping the header row if [hasHeader]),
     * sorted by [column]. Numbers compare as numbers; empty cells go last.
     */
    fun sortedOrder(doc: CsvDoc, hasHeader: Boolean, column: Int, ascending: Boolean): List<Int> {
        val first = if (hasHeader) 1 else 0
        val indices = (first until doc.rows.size).toList()
        val cmp = Comparator<Int> { a, b -> compareCells(doc.cell(a, column), doc.cell(b, column)) }
        val sorted = indices.sortedWith(if (ascending) cmp else cmp.reversed())
        // Empty cells last in both directions.
        val (filled, empty) = sorted.partition { doc.cell(it, column).isNotBlank() }
        return filled + empty
    }

    fun compareCells(a: String, b: String): Int {
        val na = a.trim().toNumberOrNull()
        val nb = b.trim().toNumberOrNull()
        return when {
            na != null && nb != null -> na.compareTo(nb)
            na != null -> -1
            nb != null -> 1
            else -> String.CASE_INSENSITIVE_ORDER.compare(a, b)
        }
    }

    fun reorder(doc: CsvDoc, hasHeader: Boolean, order: List<Int>): CsvDoc {
        val header = if (hasHeader && doc.rows.isNotEmpty()) listOf(doc.rows[0]) else emptyList()
        return doc.copy(rows = header + order.map { doc.rows[it] })
    }

    fun setCell(doc: CsvDoc, row: Int, col: Int, value: String): CsvDoc {
        val rows = doc.rows.toMutableList()
        while (rows.size <= row) rows += emptyList<CsvField>()
        val cells = rows[row].toMutableList()
        while (cells.size <= col) cells += CsvField("")
        cells[col] = cells[col].copy(value = value)
        rows[row] = cells
        return doc.copy(rows = rows)
    }

    fun insertRow(doc: CsvDoc, at: Int): CsvDoc {
        val rows = doc.rows.toMutableList()
        rows.add(at.coerceIn(0, rows.size), List(maxOf(doc.columnCount, 1)) { CsvField("") })
        return doc.copy(rows = rows)
    }

    fun deleteRow(doc: CsvDoc, row: Int): CsvDoc =
        doc.copy(rows = doc.rows.filterIndexed { i, _ -> i != row })

    fun insertColumn(doc: CsvDoc, at: Int, headerName: String? = null): CsvDoc {
        val width = doc.columnCount
        return doc.copy(
            rows = doc.rows.mapIndexed { r, row ->
                val cells = row.toMutableList()
                while (cells.size < minOf(at, width)) cells += CsvField("")
                val name = if (r == 0 && headerName != null) headerName else ""
                cells.add(at.coerceIn(0, cells.size), CsvField(name))
                cells
            },
        )
    }

    fun deleteColumn(doc: CsvDoc, col: Int): CsvDoc =
        doc.copy(rows = doc.rows.map { row -> row.filterIndexed { i, _ -> i != col } })

    private fun needsQuotes(value: String, delimiter: Char) =
        value.any { it == delimiter || it == '"' || it == '\n' || it == '\r' }

    private fun String.toNumberOrNull(): Double? =
        if (isEmpty() || length > 40) null else toDoubleOrNull()?.takeIf { !it.isNaN() }

    private fun sampleLines(sample: String): List<String> =
        sample.split('\n').filter { it.isNotBlank() }.take(10)

    private fun countOutsideQuotes(line: String, d: Char): Int {
        var inQ = false
        var count = 0
        for (c in line) {
            if (c == '"') inQ = !inQ else if (c == d && !inQ) count++
        }
        return count
    }
}
