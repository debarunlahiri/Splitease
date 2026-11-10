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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/**
 * A category field that suggests saved categories as you tap or type. Any other text is
 * accepted as a new category (it is remembered when the expense is saved).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val typed = value.trim()
    val matches = suggestions.filter { typed.isEmpty() || it.contains(typed, ignoreCase = true) }
    // Once the text equals a category, list them all so another one can be picked instead.
    val options =
        if (matches.size == 1 && matches[0].equals(typed, ignoreCase = true)) suggestions
        else matches
    val isNew = typed.isNotEmpty() && suggestions.none { it.equals(typed, ignoreCase = true) }
    val showMenu = expanded && enabled && options.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = showMenu,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text("Category") },
            supportingText = if (isNew) ({ Text("New category \"$typed\" will be saved") }) else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMenu) },
            singleLine = true,
            enabled = enabled,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier =
                Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled),
        )
        ExposedDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 240.dp),
        ) {
            options.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category) },
                    onClick = {
                        onValueChange(category)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
