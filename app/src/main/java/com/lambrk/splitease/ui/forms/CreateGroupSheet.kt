package com.lambrk.splitease.ui.forms

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.components.CurrencyDropdown
import com.lambrk.splitease.ui.components.Field
import com.lambrk.splitease.ui.components.FormScreen
import com.lambrk.splitease.ui.components.FormStep

@Composable
internal fun CreateGroupSheet(model: SplitEaseViewModel, dismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("INR") }
    FormScreen(
        title = "New group",
        model = model,
        dismiss = dismiss,
        submitLabel = "Create group",
        submitEnabled = name.isNotBlank() && currency.length == 3,
        onSubmit = { model.createGroup(name, currency, dismiss) },
        steps = listOf(
            FormStep(
                "Name your group",
                "Bring your people and shared expenses together.",
                valid = name.isNotBlank(),
            ) {
                Field(name, { name = it }, "Group name")
            },
            FormStep(
                "Pick a currency",
                "New expenses in this group start in this currency.",
                valid = currency.length == 3,
            ) {
                CurrencyDropdown(
                    value = currency,
                    onValueChange = { currency = it },
                    enabled = !model.busy,
                )
                Text("Group: ${name.trim()}")
            },
        ),
    )
}
