package com.lambrk.splitease.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.util.Locale

/** Shows the raw amount with thousands separators; the stored text stays plain digits. */
internal class AmountTransformation(currency: String) : VisualTransformation {
    // Indian rupee groups as 12,34,567; other currencies use the usual 1,234,567.
    private val format = android.icu.text.NumberFormat.getIntegerInstance(
        if (currency == "INR") Locale("en", "IN") else Locale.US
    ).apply { isGroupingUsed = true }

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val dot = raw.indexOf('.')
        val intPart = if (dot >= 0) raw.substring(0, dot) else raw
        val rest = if (dot >= 0) raw.substring(dot) else ""
        val grouped = if (intPart.isEmpty()) "" else format.format(intPart.toBigInteger())
        val shown = grouped + rest
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset > intPart.length) return offset + grouped.length - intPart.length
                var digits = 0
                for (i in grouped.indices) {
                    if (digits == offset) return i
                    if (grouped[i].isDigit()) digits++
                }
                return grouped.length
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset > grouped.length) return offset - (grouped.length - intPart.length)
                return grouped.take(offset).count { it.isDigit() }
            }
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}
