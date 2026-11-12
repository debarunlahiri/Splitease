package com.lambrk.splitease.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.ui.util.currencySymbol
import java.util.Currency
import java.util.Locale

private class CurrencyOption(val code: String, val name: String, val symbol: String) {
    val label = "$code · $name"
}

/** Currencies a person can pick, most common first. */
private fun currencyOptions(locale: Locale): List<CurrencyOption> {
    val preferred = listOf("INR", "USD", "EUR", "GBP", "AED", "AUD", "CAD", "SGD")
    // Java also lists retired currencies (Estonian Kroon, Spanish Peseta, ...). Only those some
    // country uses today are offered, plus the common ones above.
    val inUse =
        Locale.getAvailableLocales()
            .mapNotNull { runCatching { Currency.getInstance(it) }.getOrNull() }
            .map { it.currencyCode }
            .toSet() + preferred
    return Currency.getAvailableCurrencies()
        .filter { it.currencyCode in inUse && it.currencyCode !in setOf("XXX", "XTS") }
        .sortedWith(
            compareBy<Currency> {
                preferred.indexOf(it.currencyCode).takeIf { index -> index >= 0 } ?: preferred.size
            }.thenBy { it.currencyCode }
        )
        .map {
            CurrencyOption(it.currencyCode, it.getDisplayName(locale), currencySymbol(it.currencyCode))
        }
}

/** A currency picker. Tap it to see the list, and type a code or name to narrow it down. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CurrencyDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    // The text being searched for; only shown while the field is focused.
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    // Read from the configuration so the list updates if the language changes.
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val options = remember(locale) { currencyOptions(locale) }
    val selected = options.firstOrNull { it.code == value }
    val selectedLabel = selected?.label ?: value

    val needle = query.trim()
    val matches =
        if (!searching || needle.isEmpty()) options
        else
            options.filter {
                it.code.contains(needle, ignoreCase = true) ||
                    it.name.contains(needle, ignoreCase = true) ||
                    it.symbol.equals(needle, ignoreCase = true)
            }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = if (searching) query else selectedLabel,
            onValueChange = {
                query = it
                expanded = true
            },
            readOnly = !enabled,
            enabled = enabled,
            singleLine = true,
            label = { Text("Currency") },
            // While searching the box is empty, so keep showing what is currently chosen.
            placeholder = if (searching) ({ Text(selectedLabel) }) else null,
            leadingIcon = { CurrencyBadge(value) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled)
            },
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier =
                Modifier.fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled)
                    .onFocusChanged {
                        searching = it.isFocused
                        if (it.isFocused) query = ""
                    },
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            if (matches.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No matching currency") },
                    enabled = false,
                    onClick = {},
                )
            }
            matches.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(text = option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingIcon = { CurrencyBadge(option.code) },
                    onClick = {
                        onValueChange(option.code)
                        expanded = false
                        focus.clearFocus()
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
