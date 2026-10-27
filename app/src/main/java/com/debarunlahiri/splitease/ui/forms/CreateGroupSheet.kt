package com.debarunlahiri.splitease.ui.forms

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.debarunlahiri.splitease.ui.SplitEaseViewModel
import com.debarunlahiri.splitease.ui.components.CurrencyDropdown
import com.debarunlahiri.splitease.ui.components.Field
import com.debarunlahiri.splitease.ui.components.FormSheet

@Composable
internal fun CreateGroupSheet(model: SplitEaseViewModel, dismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("INR") }
    FormSheet("A new circle", model, dismiss) {
        Text("Bring your people and shared expenses together.")
        Field(name, { name = it }, "Group name")
        CurrencyDropdown(
            value = currency,
            onValueChange = { currency = it },
            enabled = !model.busy,
        )
        Button(
            enabled = !model.busy && name.isNotBlank() && currency.length == 3,
            onClick = { model.createGroup(name, currency, dismiss) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create group")
        }
    }
}
