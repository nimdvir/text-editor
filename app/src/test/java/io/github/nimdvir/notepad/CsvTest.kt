package io.github.nimdvir.notepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {

    @Test
    fun parsesQuotesCommasAndNewlines() {
        val doc = Csv.parse("name,note\n\"Smith, J\",\"said \"\"hi\"\"\nthen left\"\nAmy,ok\n")
        assertEquals(3, doc.rows.size)
        assertEquals("Smith, J", doc.cell(1, 0))
        assertEquals("said \"hi\"\nthen left", doc.cell(1, 1))
        assertTrue(doc.rows[1][0].quoted)
        assertFalse(doc.rows[2][0].quoted)
        assertTrue(doc.trailingNewline)
    }

    @Test
    fun roundTripIsExact() {
        for (src in listOf(
            "a,b,c\n1,2,3\n",
            "a,b\n\"1\",2",
            "x;y\n\"a;b\";c\n",
            "",
            "only\n",
            "a,,c\n,,\n",
        )) {
            assertEquals(src, Csv.write(Csv.parse(src)))
        }
    }

    @Test
    fun detectsDelimiters() {
        assertEquals(',', Csv.detectDelimiter("a,b,c\n1,2,3"))
        assertEquals(';', Csv.detectDelimiter("a;b;c\n1,5;2;3"))
        assertEquals('\t', Csv.detectDelimiter("a\tb\n1\t2"))
        assertEquals('\t', Csv.detectDelimiter("a,b", "data.tsv"))
        assertEquals(',', Csv.detectDelimiter("\"x;y\",z\n\"p;q\",r"))
    }

    @Test
    fun sortsNumbersNumericallyAndKeepsHeader() {
        val doc = Csv.parse("n,v\nb,10\na,9\nc,\nd,100\n")
        assertEquals(listOf(2, 1, 4, 3), Csv.sortedOrder(doc, hasHeader = true, column = 1, ascending = true))
        assertEquals(listOf(4, 1, 2, 3), Csv.sortedOrder(doc, hasHeader = true, column = 1, ascending = false))
        assertEquals(listOf(2, 1, 3, 4), Csv.sortedOrder(doc, hasHeader = true, column = 0, ascending = true))
        val reordered = Csv.write(Csv.reorder(doc, true, listOf(2, 1, 3, 4)))
        assertEquals("n,v\na,9\nb,10\nc,\nd,100\n", reordered)
    }

    @Test
    fun editsKeepOtherCellsUntouched() {
        val doc = Csv.parse("\"a\",b\n1,2\n")
        assertEquals("\"a\",b\n1,x y\n", Csv.write(Csv.setCell(doc, 1, 1, "x y")))
        assertEquals("\"a\",b\n1,\"x,y\"\n", Csv.write(Csv.setCell(doc, 1, 1, "x,y")))
        assertEquals("\"a\",b\n,\n1,2\n", Csv.write(Csv.insertRow(doc, 1)))
        assertEquals("\"a\",b\n", Csv.write(Csv.deleteRow(doc, 1)))
        assertEquals("\"a\",New,b\n1,,2\n", Csv.write(Csv.insertColumn(doc, 1, "New")))
        assertEquals("b\n2\n", Csv.write(Csv.deleteColumn(doc, 0)))
        assertEquals("\"a\",b\n1,2,z\n", Csv.write(Csv.setCell(doc, 1, 2, "z"))) // other rows untouched
    }
}
