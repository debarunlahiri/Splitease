package com.lambrk.splitease.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun Field(
    value: String,
    change: (String) -> Unit,
    label: String,
    decimal: Boolean = false,
    enabled: Boolean = true,
    keyboardType: KeyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text,
    // Plain text starts each sentence with a capital; email, password and numbers do not.
    capitalization: KeyboardCapitalization =
        if (keyboardType == KeyboardType.Text) KeyboardCapitalization.Sentences
        else KeyboardCapitalization.None,
    // Fixed text shown before the typed value, such as a currency symbol.
    prefixText: String? = null,
    isError: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    // When set, the number is shown with thousands separators for this currency (e.g. 12,34,567 for INR).
    groupingCurrency: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {
            change(if (groupingCurrency != null) sanitizeAmount(it) else it)
        },
        visualTransformation =
            if (groupingCurrency != null) remember(groupingCurrency) { AmountTransformation(groupingCurrency) }
            else VisualTransformation.None,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        prefix = if (prefixText != null) ({ Text(prefixText) }) else null,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        isError = isError,
        shape = RoundedCornerShape(18.dp),
        keyboardOptions =
            KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType),
    )
}

/** Keeps digits and a single decimal point (accepting a comma as the point). */
internal fun sanitizeAmount(input: String): String {
    var dot = false
    val cleaned = buildString {
        for (c in input) when {
            c.isDigit() -> append(c)
            (c == '.' || c == ',') && !dot -> { dot = true; append('.') }
        }
    }
    // No leading zeros ("007" becomes "7", but "0.5" stays), so what is shown matches what is stored.
    return cleaned.replace(Regex("^0+(?=\\d)"), "")
}
