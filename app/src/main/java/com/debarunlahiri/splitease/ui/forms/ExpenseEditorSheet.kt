package com.debarunlahiri.splitease.ui.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.debarunlahiri.splitease.data.objects
import com.debarunlahiri.splitease.ui.SplitEaseViewModel
import com.debarunlahiri.splitease.ui.components.Field
import com.debarunlahiri.splitease.ui.components.FormSheet
import com.debarunlahiri.splitease.ui.util.memberName
import com.debarunlahiri.splitease.ui.util.optionalText
import org.json.JSONObject

@Composable
internal fun ExpenseEditorSheet(
    model: SplitEaseViewModel,
    original: JSONObject?,
    dismiss: () -> Unit,
) {
    val group = model.selected ?: return
    var description by remember { mutableStateOf(original?.optString("description").orEmpty()) }
    var amount by remember { mutableStateOf(original?.optString("amount").orEmpty()) }
    var category by remember { mutableStateOf(original?.optionalText("category").orEmpty()) }
    var notes by remember { mutableStateOf(original?.optionalText("notes").orEmpty()) }
    var split by remember {
        mutableStateOf(
            if (original?.optString("splitType") == "PERCENTAGE") "EXACT"
            else original?.optString("splitType") ?: "EQUAL"
        )
    }
    val members = group.getJSONArray("members").objects()
    var shares by remember {
        mutableStateOf(
            members.map { member ->
                original
                    ?.optJSONArray("shares")
                    ?.objects()
                    ?.find { it.getString("userId") == member.getString("userId") }
                    ?.optString("value")
                    .orEmpty()
            }
        )
    }
    var date by remember {
        mutableStateOf(original?.optString("expenseDate") ?: java.time.LocalDate.now().toString())
    }
    var participants by remember {
        mutableStateOf(
            original?.optJSONArray("shares")?.objects()?.map { it.getString("userId") }?.toSet()
                ?: members.map { it.getString("userId") }.toSet()
        )
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val editable = original == null || original.getString("paidBy") == model.userId
    FormSheet(if (original == null) "What did you share?" else "Expense details", model, dismiss) {
        Text(
            "${group.getString("name")} · ${group.getString("defaultCurrency")}",
            color = MaterialTheme.colorScheme.primary,
        )
        Field(description, { description = it }, "Description", enabled = editable)
        Field(amount, { amount = it }, "Amount", decimal = true, enabled = editable)
        Field(category, { category = it }, "Category", enabled = editable)
        Field(notes, { notes = it }, "Notes (optional)", enabled = editable)
        if (original?.optString("splitType") == "PERCENTAGE")
            Text(
                "Existing percentage splits are shown as their final amounts. Choose Percent to enter new percentages.",
                style = MaterialTheme.typography.bodySmall,
            )
        Field(date, { date = it }, "Date (YYYY-MM-DD)", enabled = editable)
        Text("Split with", style = MaterialTheme.typography.titleMedium)
        members.forEach { member ->
            val id = member.getString("userId")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = id in participants,
                    enabled = editable && !model.busy,
                    onCheckedChange = { checked ->
                        participants = if (checked) participants + id else participants - id
                    },
                )
                Text(memberName(id, model))
            }
        }
        Text("Split the expense", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("EQUAL" to "Equal", "EXACT" to "Exact", "PERCENTAGE" to "%").forEach {
                (type, label) ->
                FilterChip(
                    selected = split == type,
                    onClick = { split = type },
                    enabled = editable && !model.busy,
                    label = { Text(label) },
                )
            }
        }
        Text(
            "Paid by ${memberName(original?.optString("paidBy") ?: model.userId, model)}",
            style = MaterialTheme.typography.bodySmall,
        )
        if (split == "EQUAL") Text("Shared equally between ${participants.size} members.")
        else
            members.forEachIndexed { index, member ->
                if (member.getString("userId") !in participants) return@forEachIndexed
                Field(
                    shares[index],
                    { value -> shares = shares.toMutableList().also { it[index] = value } },
                    "${memberName(member.getString("userId"), model)} · ${if (split == "PERCENTAGE") "percent" else group.getString("defaultCurrency")}",
                    decimal = true,
                    enabled = editable,
                )
            }
        if (original != null) {
            TextButton(enabled = !model.busy, onClick = { model.history(original) }) {
                Text("View change history")
            }
            model.audit.forEach { entry ->
                Text(
                    "${entry.getString("action")} · ${entry.getString("occurredAt").take(10)} · revision ${entry.getLong("revision")}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (editable) {
            Button(
                enabled = !model.busy && description.isNotBlank() && amount.isNotBlank(),
                onClick = {
                    model.saveExpense(
                        description,
                        amount,
                        category,
                        notes,
                        split,
                        date,
                        participants,
                        shares,
                        original,
                        dismiss,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save expense")
            }
            if (original != null)
                TextButton(enabled = !model.busy, onClick = { confirmDelete = true }) {
                    Text("Delete expense", color = MaterialTheme.colorScheme.error)
                }
        } else
            Text(
                "Only the person who paid can change this expense.",
                style = MaterialTheme.typography.bodySmall,
            )
    }
    if (confirmDelete && original != null)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete expense?") },
            text = { Text("This removes the expense and updates the group's balances.") },
            confirmButton = {
                TextButton(
                    enabled = !model.busy,
                    onClick = {
                        confirmDelete = false
                        model.deleteExpense(original, dismiss)
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
}
