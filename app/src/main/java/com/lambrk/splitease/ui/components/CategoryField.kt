package com.lambrk.splitease.ui.components

import com.lambrk.splitease.ui.i18n.tr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.lambrk.splitease.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

private const val MAX_CHIPS = 14

/**
 * Categories as tappable chips. Tapping the selected chip clears it. "Others" opens a box for a
 * category that isn't listed; whatever is typed there is remembered as a new chip once the split is saved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    enabled: Boolean = true,
) {
    // "Other" is covered by the Others chip.
    val chips = suggestions.filterNot { it.equals("other", true) || it.equals("others", true) }
        .distinctBy { it.lowercase() }.take(MAX_CHIPS)
    val typed = value.trim()
    val listed = chips.firstOrNull { it.equals(typed, ignoreCase = true) }
    // "Others" is on while a custom value is in use, or right after it was tapped with nothing typed yet.
    var othersTapped by remember { mutableStateOf(false) }
    val othersSelected = (typed.isNotEmpty() && listed == null) || othersTapped

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            chips.forEach { category ->
                FilterChip(
                    selected = listed == category,
                    enabled = enabled,
                    onClick = {
                        othersTapped = false
                        onValueChange(if (listed == category) "" else category)
                    },
                    label = { Text(category) },
                )
            }
            FilterChip(
                selected = othersSelected,
                enabled = enabled,
                onClick = {
                    if (othersSelected) {
                        othersTapped = false
                        if (listed == null) onValueChange("")
                    } else {
                        othersTapped = true
                        onValueChange("")
                    }
                },
                label = { Text("Others") },
            )
        }
        if (othersSelected) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(tr("Your category")) },
                placeholder = { Text(tr("e.g. Gifts")) },
                supportingText = { Text(tr("We'll save it so you can pick it next time.")) },
                singleLine = true,
                enabled = enabled,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
