package com.debarunlahiri.splitease.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.debarunlahiri.splitease.ui.util.money
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
        title = { Text("Record this payment?") },
        text = {
            Text(
                "Confirm you have paid ${money(suggestion.getString("amount"), suggestion.getString("currency"))}. This records the payment for your group; no money is transferred."
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
                Text("Record payment")
            }
        },
        dismissButton = { TextButton(onClick = { onDismiss() }) { Text("Cancel") } },
    )
}
