package com.lambrk.splitease.ui.components

import com.lambrk.splitease.ui.i18n.tr

import androidx.compose.material3.AlertDialog
import com.lambrk.splitease.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.lambrk.splitease.ui.util.money
import org.json.JSONObject

@Composable
internal fun RecordPaymentDialog(
    suggestion: JSONObject,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(tr("Mark this as paid?")) },
        text = {
            Text(
                "Confirm that you paid ${money(suggestion.getString("amount"), suggestion.getString("currency"))}. This only marks it as paid; no money is sent."
            )
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text(tr("Yes, I paid"))
            }
        },
        dismissButton = { TextButton(onClick = { onDismiss() }) { Text("Cancel") } },
    )
}
