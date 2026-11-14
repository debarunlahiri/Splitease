package com.lambrk.splitease

import com.lambrk.splitease.ui.forms.ReceiptParser
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptParserTest {
    private val today = LocalDate.of(2026, 11, 14)

    @Test
    fun restaurantBill() {
        val text = """
            DOMINOS PIZZA
            GSTIN: 29ABCDE1234F1Z5
            Bill No: 4521   Date: 12/11/2026
            Margherita Pizza   1   299.00
            Garlic Bread       2   178.00
            Subtotal                477.00
            CGST 2.5%                11.93
            SGST 2.5%                11.93
            Grand Total            Rs. 500.86
            Thank you, visit again
        """.trimIndent()
        val r = ReceiptParser.parse(text, today)
        assertEquals("Dominos Pizza", r.merchant)
        assertEquals(BigDecimal("500.86"), r.total)
        assertEquals(LocalDate.of(2026, 11, 12), r.date)
        assertEquals(listOf("Margherita Pizza", "Garlic Bread"), r.items.map { it.first })
        assertEquals(BigDecimal("178.00"), r.items[1].second)
    }

    @Test
    fun totalOnNextLineAndIndianGrouping() {
        val text = "Big Bazaar\n13 Nov 2026\nTotal Amount\n₹ 1,24,560.00\n"
        val r = ReceiptParser.parse(text, today)
        assertEquals(BigDecimal("124560.00"), r.total)
        assertEquals(LocalDate.of(2026, 11, 13), r.date)
    }

    @Test
    fun noTotalWordUsesBiggestPaiseAmount() {
        val r = ReceiptParser.parse("Cafe Aroma\nCoffee 120.00\nCake 245.50\n", today)
        assertEquals(BigDecimal("245.50"), r.total)
    }

    @Test
    fun ignoresFutureOrMissingDates() {
        assertNull(ReceiptParser.parse("Shop\nTotal 100.00\n01/01/2031", today).date)
        assertNotNull(ReceiptParser.parse("Shop\nTotal 100.00", today).total)
    }
}
