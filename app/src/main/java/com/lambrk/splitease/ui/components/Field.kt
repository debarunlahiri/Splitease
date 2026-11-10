package com.lambrk.splitease.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
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
