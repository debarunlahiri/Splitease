package com.lambrk.splitease.ui.forms

import java.math.BigDecimal
import java.time.LocalDate

/** What was read from a receipt photo. Every field can be missing; the person checks them before using them. */
internal class ReceiptResult(
    val merchant: String?,
    val total: BigDecimal?,
    val date: LocalDate?,
    val items: List<Pair<String, BigDecimal>>,
)

/** Pulls the shop, the total, the date and the line items out of the text a receipt scan produced. */
internal object ReceiptParser {
    private val AMOUNT = Regex("""(?<![\d.,])(\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)(?![\d])""")
    private val TOTAL_WORDS = Regex("""(?i)\b(grand\s*total|total\s*(amount|payable|due)?|net\s*(amount|payable)|amount\s*(payable|due)|balance\s*due|bill\s*amount|to\s*pay|you\s*pay|payable)\b""")
    private val NOT_TOTAL = Regex("""(?i)(sub\s*-?\s*total|total\s*(qty|items?|quantity|discount|tax|gst|savings)|tax|gst|cgst|sgst|igst|vat|discount|round|change|tendered|cash|paid\s*by|saved)""")
    private val SKIP_LINE = Regex("""(?i)(gstin|gst\s*no|fssai|invoice|bill\s*no|order|table|phone|ph[:.]|tel|mob|www\.|http|@|thank|visit|cashier|date|time|token|qty|rate|price|amount|description|item)""")
    private val DATE_PATTERNS = listOf(
        Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})\b""") to "dmy",
        Regex("""\b(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})\b""") to "ymd",
        Regex("""(?i)\b(\d{1,2})[\s\-]+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[\s\-,.]+(\d{2,4})\b""") to "dMy",
    )
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    fun parse(text: String, today: LocalDate = LocalDate.now()): ReceiptResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return ReceiptResult(merchant(lines), total(lines), date(lines, today), items(lines))
    }

    private fun amountsIn(line: String): List<BigDecimal> =
        AMOUNT.findAll(line).mapNotNull { it.value.replace(",", "").toBigDecimalOrNull() }
            .filter { it.signum() > 0 && it < BigDecimal("10000000") }.toList()

    private fun total(lines: List<String>): BigDecimal? {
        // The last line that says "total" wins: receipts end with the grand total after any subtotals.
        var found: BigDecimal? = null
        lines.forEachIndexed { index, line ->
            if (TOTAL_WORDS.containsMatchIn(line) && !NOT_TOTAL.containsMatchIn(line)) {
                val onLine = amountsIn(line).maxOrNull()
                val value = onLine ?: lines.getOrNull(index + 1)?.takeIf { !TOTAL_WORDS.containsMatchIn(it) }?.let { amountsIn(it).maxOrNull() }
                if (value != null) found = value
            }
        }
        if (found != null) return found
        // No "total" line: the biggest amount with paise, which is nearly always the total.
        return lines.flatMap { line -> if (NOT_TOTAL.containsMatchIn(line) && !TOTAL_WORDS.containsMatchIn(line)) emptyList() else amountsIn(line).filter { it.scale() == 2 } }
            .maxOrNull()
    }

    private fun merchant(lines: List<String>): String? =
        lines.take(6).firstOrNull { line ->
            val letters = line.count { it.isLetter() }
            letters >= 3 && letters * 2 >= line.length && !SKIP_LINE.containsMatchIn(line) && !TOTAL_WORDS.containsMatchIn(line)
        }?.lowercase()?.split(' ')?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }?.take(60)

    private fun date(lines: List<String>, today: LocalDate): LocalDate? {
        for (line in lines) for ((regex, kind) in DATE_PATTERNS) {
            val m = regex.find(line) ?: continue
            val date = runCatching {
                when (kind) {
                    "dmy" -> LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
                    "ymd" -> LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                    else -> {
                        val year = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
                        LocalDate.of(year, MONTHS.indexOf(m.groupValues[2].lowercase().take(3)) + 1, m.groupValues[1].toInt())
                    }
                }
            }.getOrNull() ?: continue
            if (date.isAfter(today.plusDays(1)) || date.isBefore(today.minusYears(2))) continue
            return date
        }
        return null
    }

    private fun items(lines: List<String>): List<Pair<String, BigDecimal>> {
        val out = mutableListOf<Pair<String, BigDecimal>>()
        for (line in lines) {
            if (TOTAL_WORDS.containsMatchIn(line) || NOT_TOTAL.containsMatchIn(line) || SKIP_LINE.containsMatchIn(line)) continue
            val amounts = amountsIn(line)
            if (amounts.isEmpty()) continue
            val name = line.replace(AMOUNT, " ").replace(Regex("""[₹]|(?i)\brs\.?|\binr\b"""), " ").replace(Regex("""\s+"""), " ").trim(' ', '-', ':', '.', 'x', 'X', '*')
            if (name.count { it.isLetter() } < 3) continue
            out += name.take(40) to amounts.last()
        }
        return out.take(15)
    }
}
