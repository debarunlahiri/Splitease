package com.debarunlahiri.splitease.ui.screens.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.debarunlahiri.splitease.ui.SplitEaseViewModel
import com.debarunlahiri.splitease.ui.components.EmptyState
import com.debarunlahiri.splitease.ui.components.Symbol
import com.debarunlahiri.splitease.ui.util.memberName
import com.debarunlahiri.splitease.ui.util.money
import com.debarunlahiri.splitease.ui.util.optionalText
import org.json.JSONObject

internal fun LazyListScope.GroupExpensesContent(
    model: SplitEaseViewModel,
    onEdit: (JSONObject) -> Unit,
) {
    if (model.expenses.isEmpty())
        item {
            EmptyState(
                Icons.Rounded.ReceiptLong,
                "A fresh start",
                "Add your first shared expense. We'll take care of the split.",
            )
        }
    items(model.expenses, key = { it.getString("id") }) { expense ->
        Card(
            onClick = {
                if (
                    expense.optString("_syncState") in listOf("PENDING", "FAILED", "AUTH_REQUIRED")
                ) {
                    model.message = "This expense is saved on your device. Sync it before editing."
                } else onEdit(expense)
            },
            shape = RoundedCornerShape(22.dp),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol(Icons.Rounded.ReceiptLong, Modifier.size(44.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        expense.getString("description"),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${expense.getString("expenseDate")} · ${expense.optionalText("category").ifBlank { "General" }}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val syncState = expense.optString("_syncState")
                    if (syncState in listOf("PENDING", "FAILED", "AUTH_REQUIRED")) {
                        Text(
                            when (syncState) {
                                "FAILED" -> "Couldn't sync. Your expense is still saved."
                                "AUTH_REQUIRED" -> "Sign in again to sync"
                                else -> "Waiting to sync"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (syncState == "FAILED") MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                        )
                        if (syncState == "FAILED")
                            TextButton(
                                onClick = { model.retryExpense(expense.getString("_operationId")) },
                                enabled = !model.busy,
                            ) {
                                Text("Retry sync")
                            }
                    }
                    Text(
                        "Paid by ${memberName(expense.getString("paidBy"), model)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    money(expense.getString("amount"), expense.getString("currency")),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
