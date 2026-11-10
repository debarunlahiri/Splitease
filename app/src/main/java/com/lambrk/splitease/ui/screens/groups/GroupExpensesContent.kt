package com.lambrk.splitease.ui.screens.groups

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
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.components.EmptyState
import com.lambrk.splitease.ui.components.Symbol
import com.lambrk.splitease.ui.components.ProfileAvatar
import com.lambrk.splitease.ui.util.memberName
import com.lambrk.splitease.ui.util.memberPhotoUrl
import com.lambrk.splitease.ui.util.money
import com.lambrk.splitease.ui.util.optionalText
import org.json.JSONObject

internal fun LazyListScope.GroupExpensesContent(
    model: SplitEaseViewModel,
    onEdit: (JSONObject) -> Unit,
    onDraft: (String) -> Unit,
) {
    val drafts = model.groupDrafts
    items(drafts, key = { it.snapshotKey }) { draft ->
        val fields = JSONObject(draf
        Card(
            onClick = { onDraft(draft.snapshotKey) },
            enabled = !model.busy,
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            ListItem(
                leadingContent = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
                headlineContent = {
                    Text(fields.optString("description").ifBlank { "Untitled expense" })
                },
                supportingContent = { Text("Draft · Not submitted") },
                trailingContent = {
                    val amount = fields.optString("amount")
                    if (amount.isNotBlank()) Text(money(amount, fields.optString("currency")))
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
    }
    if (model.expenses.isEmpty() && drafts.isEmpty())
        item {
            EmptyState(
                Icons.Rounded.ReceiptLong,
                "A fresh start",
                "Add your first shared expense. We'll take care of the split.",
            )
        }
    items(model.expenses, key = { it.getString("id") }) { expense ->
        Card(
            onClick = { onEdit(expense) },
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
                    val payer = expense.getString("paidBy")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileAvatar(memberPhotoUrl(payer, model), size = 20.dp)
                        Text(
                            "Paid by ${memberName(payer, model)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text(
                    money(expense.getString("amount"), expense.getString("currency")),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
