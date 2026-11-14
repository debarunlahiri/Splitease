package com.lambrk.splitease.ui.forms

import com.lambrk.splitease.ui.i18n.tr

import com.lambrk.splitease.ui.i18n.Text
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var currency by remember {
        mutableStateOf(com.lambrk.splitease.ui.util.currencyOf(com.lambrk.splitease.ui.util.defaultCountry(context).iso) ?: "INR")
    }
    FormScreen(
        title = tr("New Splits"),
        model = model,
        dismiss = dismiss,
        submitLabel = "Create Splits",
        submitEnabled = name.isNotBlank() && currency.length == 3,
        onSubmit = { model.createGroup(name, currency, dismiss) },
        steps = listOf(
            FormStep(
                tr("Name your Splits"),
                tr("Add your people and keep all your splits in one place."),
                valid = name.isNotBlank(),
            ) {
                Field(name, { name = it }, "Splits name")
            },
            FormStep(
                tr("Pick a currency"),
                tr("New splits here use this currency."),
                valid = currency.length == 3,
            ) {
                CurrencyDropdown(
                    value = currency,
                    onValueChange = { currency = it },
                    enabled = !model.busy,
                )
                Text("Splits: ${name.trim()}")
            },
        ),
    )
}
