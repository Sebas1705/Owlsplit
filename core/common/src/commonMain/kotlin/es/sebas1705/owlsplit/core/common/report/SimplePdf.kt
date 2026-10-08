// Character codes, font metrics and PDF syntax are numbers by nature.
@file:Suppress("MagicNumber")

package es.sebas1705.owlsplit.core.common.report

/**
 * A minimal PDF writer: A4 pages with text in Helvetica (regular and bold) and filled rectangles.
 * It has no dependencies and gives the same file on every platform. The text goes in WinAnsi, which
 * covers Spanish and the rest of Western Europe plus "€"; anything else is written as "?". The
 * coordinates are in points from the top-left corner of the page.
 */
class SimplePdf(val width: Float = A4_WIDTH, val height: Float = A4_HEIGHT) {
    private val pages = mutableListOf(StringBuilder())
    private val page: StringBuilder get() = pages.last()

    val pageCount: Int get() = pages.size

    fun newPage() {
        pages += StringBuilder()
    }

    /** [y] is the text baseline; [rgb] is 0xRRGGBB. */
    fun text(x: Float, y: Float, text: String, size: Float, bold: Boolean = false, rgb: Int = BLACK) {
        page.append(color(rgb)).append(" rg BT /").append(if (bold) "F2" else "F1").append(' ')
            .append(num(size)).append(" Tf ").append(num(x)).append(' ').append(num(height - y))
            .append(" Td (").append(escape(text)).append(") Tj ET\n")
    }

    /** Text that ends at [right] (numbers in a column). */
    fun textRight(right: Float, y: Float, text: String, size: Float, bold: Boolean = false, rgb: Int = BLACK) =
        text(right - textWidth(text, size), y, text, size, bold, rgb)

    fun rect(x: Float, y: Float, w: Float, h: Float, rgb: Int) {
        page.append(color(rgb)).append(" rg ").append(num(x)).append(' ').append(num(height - y - h)).append(' ')
            .append(num(w)).append(' ').append(num(h)).append(" re f\n")
    }

    /** Width of [text] in Helvetica at [size] (bold is a few percent wider; close enough for layout). */
    fun textWidth(text: String, size: Float): Float = text.sumOf { widthOf(it) } * size / EM

    /** [text] cut with "…" so it fits in [maxWidth]. */
    fun fit(text: String, size: Float, maxWidth: Float): String {
        if (textWidth(text, size) <= maxWidth) return text
        var cut = text
        while (cut.isNotEmpty() && textWidth("$cut…", size) > maxWidth) cut = cut.dropLast(1)
        return "${cut.trimEnd()}…"
    }

    fun build(): ByteArray {
        val out = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        fun obj(body: String) {
            offsets += out.length
            out.append(offsets.size).append(" 0 obj\n").append(body).append("\nendobj\n")
        }
        // 1 catalog, 2 page tree, 3 and 4 fonts, then a page and its content for each page.
        val pageIds = pages.indices.map { FIRST_PAGE_ID + it * 2 }
        obj("<< /Type /Catalog /Pages 2 0 R >>")
        obj("<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count ${pages.size} >>")
        obj(font("Helvetica"))
        obj(font("Helvetica-Bold"))
        pages.forEachIndexed { index, content ->
            obj(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${num(width)} ${num(height)}] " +
                    "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${pageIds[index] + 1} 0 R >>",
            )
            obj("<< /Length ${content.length} >>\nstream\n$content\nendstream")
        }
        val xref = out.length
        out.append("xref\n0 ").append(offsets.size + 1).append("\n0000000000 65535 f \n")
        offsets.forEach { out.append(it.toString().padStart(XREF_DIGITS, '0')).append(" 00000 n \n") }
        out.append("trailer\n<< /Size ").append(offsets.size + 1).append(" /Root 1 0 R >>\nstartxref\n")
            .append(xref).append("\n%%EOF\n")
        // Everything above is ASCII (text is escaped), so characters and bytes line up for the xref.
        return out.toString().encodeToByteArray()
    }

    private fun font(name: String) = "<< /Type /Font /Subtype /Type1 /BaseFont /$name /Encoding /WinAnsiEncoding >>"

    private fun color(rgb: Int): String =
        listOf(rgb shr 16, rgb shr 8, rgb).joinToString(" ") { num((it and 0xFF) / 255f) }

    companion object {
        const val A4_WIDTH = 595f
        const val A4_HEIGHT = 842f
        const val BLACK = 0x000000
        private const val FIRST_PAGE_ID = 5
        private const val XREF_DIGITS = 10
        private const val EM = 1000f
        private const val DEFAULT_WIDTH = 556
        private const val FIRST_ASCII = 32

        // Helvetica advance widths for ' '..'~' (Adobe's AFM).
        private val ASCII_WIDTHS = intArrayOf(
            278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
            556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
            1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
            667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
            333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
            556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584,
        )

        // WinAnsi characters outside Latin-1 that a report may use.
        private val WIN_ANSI_EXTRA = mapOf(
            '€' to 0x80, '…' to 0x85, '‘' to 0x91, '’' to 0x92, '“' to 0x93, '”' to 0x94,
            '•' to 0x95, '–' to 0x96, '—' to 0x97, '−' to 0x2D,
        )

        private fun widthOf(c: Char): Int = when {
            c.code in FIRST_ASCII until FIRST_ASCII + ASCII_WIDTHS.size -> ASCII_WIDTHS[c.code - FIRST_ASCII]
            c == '…' -> 1000
            c == '—' -> 1000
            c == '•' -> 350
            else -> DEFAULT_WIDTH
        }

        private fun winAnsi(c: Char): Int = when {
            c.code in FIRST_ASCII..0x7E -> c.code
            c.code in 0xA0..0xFF -> c.code
            else -> WIN_ANSI_EXTRA[c] ?: '?'.code
        }

        /** A PDF literal string body: ASCII as is, the rest as octal escapes. */
        private fun escape(text: String): String = buildString {
            text.forEach { c ->
                val code = winAnsi(c)
                when {
                    code == '('.code || code == ')'.code || code == '\\'.code -> append('\\').append(code.toChar())
                    code < 0x80 -> append(code.toChar())
                    else -> append('\\').append(code.toString(8).padStart(3, '0'))
                }
            }
        }

        private fun num(v: Float): String {
            val hundredths = kotlin.math.round(v * 100).toLong()
            val sign = if (hundredths < 0) "-" else ""
            val abs = kotlin.math.abs(hundredths)
            val fraction = abs % 100
            return if (fraction == 0L) {
                "$sign${abs / 100}"
            } else {
                "$sign${abs / 100}.${fraction.toString().padStart(
                    2,
                    '0'
                )}"
            }
        }
    }
}
