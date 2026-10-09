package es.sebas1705.owlsplit.core.common.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimplePdfTest {
    @Test
    fun aValidFileWithOneObjectPerPageAndAMatchingXref() {
        val pdf = SimplePdf()
        pdf.text(48f, 60f, "Cena (Ibiza) — 12,50 €", 12f, bold = true)
        pdf.newPage()
        pdf.rect(48f, 100f, 200f, 8f, 0x3B4BF5)
        val text = pdf.build().decodeToString()

        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        assertTrue("/Count 2" in text)
        // Brackets escaped, the dash and the euro sign in WinAnsi octal.
        assertTrue("(Cena \\(Ibiza\\) \\227 12,50 \\200) Tj" in text)
        assertTrue("0.23 0.29 0.96 rg 48 734 200 8 re f" in text)

        // Every xref entry points at the start of its object.
        val xref = text.substringAfter("xref\n").substringBefore("trailer").lines().drop(2).filter { it.isNotBlank() }
        xref.forEachIndexed { index, line ->
            val offset = line.take(10).toInt()
            assertTrue(text.substring(offset).startsWith("${index + 1} 0 obj"), "object ${index + 1}")
        }
        val startXref = text.substringAfter("startxref\n").substringBefore("\n").toInt()
        assertTrue(text.substring(startXref).startsWith("xref"))
    }

    @Test
    fun widthsFollowHelveticaAndLongTextIsCut() {
        val pdf = SimplePdf()
        assertEquals(5.56f, pdf.textWidth("0", 10f), 0.001f)
        assertEquals(25.02f, pdf.textWidth("12,50", 10f), 0.001f)
        val cut = pdf.fit("Una descripción larguísima de un gasto", 10f, 60f)
        assertTrue(cut.endsWith("…"))
        assertTrue(pdf.textWidth(cut, 10f) <= 60f)
    }
}
