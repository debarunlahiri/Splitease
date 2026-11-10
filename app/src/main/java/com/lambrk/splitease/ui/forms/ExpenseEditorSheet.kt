package com.lambrk.splitease.ui.forms

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.data.ExpenseAttachment
import com.lambrk.splitease.data.MAX_EXPENSE_ATTACHMENTS
import com.lambrk.splitease.data.json
import com.lambrk.splitease.data.objects
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.components.CategoryField
import com.lambrk.splitease.ui.components.CurrencyDropdown
import com.lambrk.splitease.ui.components.DateField
import com.lambrk.splitease.ui.components.ExpenseAttachmentViewer
import com.lambrk.splitease.ui.components.Field
import com.lambrk.splitease.ui.components.FormSheet
import com.lambrk.splitease.ui.components.ProfileAvatar
import com.lambrk.splitease.ui.components.openAttachmentInAndroid
import com.lambrk.splitease.ui.util.currencySymbol
import com.lambrk.splitease.ui.util.memberName
import com.lambrk.splitease.ui.util.memberPhotoUrl
import com.lambrk.splitease.ui.util.optionalText
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun ExpenseEditorSheet(
    model: SplitEaseViewModel,
    original: JSONObject?,
    requestedDraftKey: String? = null,
    viewOnly: Boolean = false,
    dismiss: () -> Unit,
) {
    val group = model.selected ?: return
    val groupId = group.getString("id")
    val expenseId = original?.optString("id")
    val draftKey = remember(groupId, expenseId, requestedDraftKey) {
        requestedDraftKey ?: "expense-draft:$groupId:${expenseId ?: "new:${UUID.randomUUID()}"}"
    }
    var draft by remember(draftKey) { mutableStateOf<JSONObject?>(null) }
    var ready by remember(draftKey) { mutableStateOf(false) }
    LaunchedEffect(draftKey) {
        try {
            val saved = model.expenseDraft(draftKey)
            if (requestedDraftKey != null && saved == null) {
                model.error = "This expense draft is no longer available."
                dismiss()
                return@LaunchedEffect
            }
            draft = saved
            ready = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            model.error = "The expense draft couldn’t be opened. Try again."
            dismiss()
        }
    }
    if (ready) ExpenseEditorContent(model, original, draft, draftKey, viewOnly, dismiss)
}

@Composable
private fun ExpenseEditorContent(
    model: SplitEaseViewModel,
    original: JSONObject?,
    draft: JSONObject?,
    draftKey: String,
    viewOnly: Boolean,
    dismiss: () -> Unit,
) {
    val group = model.selected ?: return
    val initial = draft ?: original
    var description by remember { mutableStateOf(initial?.optString("description").orEmpty()) }
    var amount by remember { mutableStateOf(initial?.optString("amount").orEmpty()) }
    // Starts as the group's currency (or the expense's own); the user can pick another.
    var currency by remember {
        mutableStateOf(
            initial?.optString("currency")?.takeIf { it.isNotBlank() }
                ?: group.getString("defaultCurrency")
        )
    }
    var category by remember { mutableStateOf(initial?.optionalText("category").orEmpty()) }
    var notes by remember { mutableStateOf(initial?.optionalText("notes").orEmpty()) }
    var split by remember {
        mutableStateOf(
            if (draft != null) draft.getString("splitType")
            else if (original?.optString("splitType") == "PERCENTAGE") "EXACT"
            else original?.optString("splitType") ?: "EQUAL"
        )
    }
    val members = group.getJSONArray("members").objects()
    var shares by remember {
        mutableStateOf(
            draft?.optJSONObject("shareValues")?.let { values ->
                values.keys().asSequence().associateWith { values.getString(it) }
            } ?: original?.optJSONArray("shares")?.objects().orEmpty()
                .associate { it.getString("userId") to it.optString("value") }
        )
    }
    var date by remember {
        mutableStateOf(initial?.optString("expenseDate") ?: java.time.LocalDate.now().toString())
    }
    var participants by remember {
        mutableStateOf(
            draft?.optJSONArray("participants")?.let { ids ->
                (0 until ids.length()).map { ids.getString(it) }.toSet()
            } ?: original?.optJSONArray("shares")?.objects()?.map { it.getString("userId") }?.toSet()
                ?: members.map { it.getString("userId") }.toSet()
        )
    }
    val categorySuggestions = remember { model.categorySuggestions() }
    var confirmDelete by remember { mutableStateOf(false) }
    var shareTotalError by remember { mutableStateOf<String?>(null) }
    var highlightShares by remember { mutableStateOf(false) }
    val splitSection = remember { BringIntoViewRequester() }
    LaunchedEffect(shareTotalError, highlightShares) {
        if (shareTotalError == null && highlightShares) splitSection.bringIntoView()
    }
    val pending = original?.optString("id")?.startsWith("local:") == true
    val editable = !viewOnly && !pending &&
        (original == null || original.getString("paidBy") == model.userId)
    val originalAttachments = remember(original) { ExpenseAttachment.fromExpense(original) }
    val savedAttachments = remember(draft) { ExpenseAttachment.fromExpense(draft) }
    var attachments by remember { mutableStateOf(if (draft == null) originalAttachments else savedAttachments) }
    var confirmDraft by remember { mutableStateOf(false) }
    var staging by remember { mutableStateOf(false) }
    var openingDocument by remember { mutableStateOf(false) }
    var galleryPage by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        staging = true
        scope.launch {
            val remaining = MAX_EXPENSE_ATTACHMENTS - attachments.size
            var failed = false
            try {
                for (uri in uris.take(remaining)) {
                    try {
                        attachments = attachments + model.stageAttachment(uri)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                    }
                }
                if (failed) model.error = "Some files couldn’t be attached. Choose readable files up to 10 MB each."
                else if (uris.size > remaining) model.error = "You can attach up to five files per expense."
            } finally {
                staging = false
            }
        }
    }

    fun openDocument(attachment: ExpenseAttachment) {
        if (openingDocument) return
        openingDocument = true
        scope.launch {
            try {
                val file = model.openAttachment(attachment)
                if (!openAttachmentInAndroid(context, attachment, file)) {
                    model.error = "No installed app can open this file type."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                model.error = "This attachment couldn’t be opened."
            } finally {
                openingDocument = false
            }
        }
    }

    fun snapshot() = json(
        "description" to description,
        "amount" to amount,
        "currency" to currency,
        "category" to category,
        "notes" to notes,
        "splitType" to split,
        "expenseDate" to date,
        "participants" to JSONArray(participants.sorted()),
        "shareValues" to JSONObject(shares.toSortedMap()),
        "attachments" to JSONArray(attachments.map { it.toJson() }),
        "sourceExpense" to original,
    )
    val initialSnapshot = remember { snapshot().toString() }
    // The signed-in user is marked so they can find themselves in the list.
    fun personLabel(id: String) =
        memberName(id, model) + if (id == model.userId) " (Me)" else ""
    FormSheet(
        title = when {
            viewOnly -> "Draft details"
            original == null -> "What did you share?"
            else -> "Expense details"
        },
        model = model,
        dismiss = dismiss,
        confirmDismiss = {
            val blocked = staging || openingDocument
            val changed = editable && snapshot().toString() != initialSnapshot
            if (!blocked && changed) confirmDraft = true
            !blocked && !changed
        },
    ) {
        if (draft != null) {
            Text("Draft · Not submitted", color = MaterialTheme.colorScheme.primary)
        }
        Text(
            "${group.getString("name")} · ${group.getString("defaultCurrency")}",
            color = MaterialTheme.colorScheme.primary,
        )
        Field(description, { description = it }, "Description", enabled = editable)
        CurrencyDropdown(
            value = currency,
            onValueChange = { currency = it },
            enabled = editable && !model.busy,
        )
        Field(
            amount,
            { amount = it },
            "Amount",
            decimal = true,
            enabled = editable,
            prefixText = currencySymbol(currency),
        )
        CategoryField(category, { category = it }, categorySuggestions, enabled = editable)
        Field(notes, { notes = it }, "Notes (optional)", enabled = editable)
        Text(
            "Attachments · ${attachments.size} of $MAX_EXPENSE_ATTACHMENTS",
            style = MaterialTheme.typography.titleMedium,
        )
        attachments.forEachIndexed { index, attachment ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    enabled = !openingDocument && !staging && !model.busy,
                    onClick = {
                        if (attachment.isImage) galleryPage = index else openDocument(attachment)
                    },
                ) {
                    Icon(
                        if (attachment.isImage) Icons.Rounded.Image else Icons.Rounded.Description,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        attachment.name,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (editable) {
                    IconButton(
                        enabled = !staging && !openingDocument && !model.busy,
                        onClick = {
                            attachments = attachments - attachment
                            if (attachment !in originalAttachments && attachment !in savedAttachments) {
                                scope.launch { model.discardAttachments(listOf(attachment)) }
                            }
                        },
                    ) { Icon(Icons.Rounded.Close, "Remove ${attachment.name}") }
                }
            }
        }
        if (staging || openingDocument) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (editable && attachments.size < MAX_EXPENSE_ATTACHMENTS) {
            TextButton(
                enabled = !staging && !openingDocument && !model.busy,
                onClick = { picker.launch(arrayOf("*/*")) },
            ) {
                Icon(Icons.Rounded.AttachFile, contentDescription = null)
                Text("Add files")
            }
            Text("Up to 10 MB per file.", style = MaterialTheme.typography.bodySmall)
        }
        if (original?.optString("splitType") == "PERCENTAGE")
            Text(
                "Existing percentage splits are shown as their final amounts. Choose Percent to enter new percentages.",
                style = MaterialTheme.typography.bodySmall,
            )
        DateField(date, { date = it }, "Date", enabled = editable)
        Text("Split with", style = MaterialTheme.typography.titleMedium)
        members.forEach { member ->
            val id = member.getString("userId")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = id in participants,
                    enabled = editable && !model.busy,
                    onCheckedChange = { checked ->
                        participants = if (checked) participants + id else participants - id
                    },
                )
                ProfileAvatar(memberPhotoUrl(id, model), size = 32.dp)
                Text(personLabel(id))
            }
        }
        Text(
            "Split the expense",
            modifier = Modifier.bringIntoViewRequester(splitSection),
            color =
                if (highlightShares) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("EQUAL" to "Equal", "EXACT" to "Exact", "PERCENTAGE" to "%").forEach {
                (type, label) ->
                FilterChip(
                    selected = split == type,
                    onClick = {
                        if (split != type) highlightShares = false
                        split = type
                    },
                    enabled = editable && !model.busy,
                    label = { Text(label) },
                )
            }
        }
        val payer = original?.optString("paidBy") ?: model.userId
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileAvatar(memberPhotoUrl(payer, model), size = 24.dp)
            Text("Paid by ${memberName(payer, model)}", style = MaterialTheme.typography.bodySmall)
        }
        if (split == "EQUAL") Text("Shared equally between ${participants.size} members.")
        else
            members.forEach { member ->
                val id = member.getString("userId")
                if (id !in participants) return@forEach
                Field(
                    shares[id].orEmpty(),
                    { value -> shares = shares + (id to value) },
                    "${personLabel(id)} · ${if (split == "PERCENTAGE") "percent" else group.getString("defaultCurrency")}",
                    decimal = true,
                    enabled = editable,
                    isError = highlightShares,
                    leadingIcon = { ProfileAvatar(memberPhotoUrl(id, model), size = 24.dp) },
                )
            }
        if (original != null && !pending && !viewOnly) {
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
                enabled = !model.busy && !staging && !openingDocument &&
                    description.isNotBlank() && amount.isNotBlank(),
                onClick = {
                    highlightShares = false
                    model.saveExpense(
                        description,
                        amount,
                        currency,
                        category,
                        notes,
                        split,
                        date,
                        participants,
                        shares,
                        attachments,
                        original,
                        draftKey,
                        onShareTotalMismatch = {
                            highlightShares = true
                            shareTotalError = it
                        },
                        done = dismiss,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save expense")
            }
            if (original != null)
                TextButton(
                    enabled = !model.busy && !staging && !openingDocument,
                    onClick = { confirmDelete = true },
                ) {
                    Text("Delete expense", color = MaterialTheme.colorScheme.error)
                }
        } else if (!viewOnly)
            Text(
                if (pending) "Sync this expense before editing."
                else "Only the person who paid can change this expense.",
                style = MaterialTheme.typography.bodySmall,
            )
    }
    if (confirmDraft) {
        AlertDialog(
            onDismissRequest = { if (!model.busy) confirmDraft = false },
            title = { Text("Save this draft?") },
            text = { Text("Keep your changes on this device and submit the expense later.") },
            confirmButton = {
                TextButton(
                    enabled = !model.busy,
                    onClick = {
                        model.saveExpenseDraft(draftKey, snapshot()) {
                            confirmDraft = false
                            dismiss()
                        }
                    },
                ) { Text("Save draft") }
            },
            dismissButton = {
                TextButton(
                    enabled = !model.busy,
                    onClick = { confirmDraft = false },
                ) { Text("Keep editing") }
            },
        )
    }
    galleryPage?.let { page ->
        ExpenseAttachmentViewer(
            attachments = attachments,
            initialPage = page,
            load = model::openAttachment,
            openDocument = ::openDocument,
            onDismiss = { galleryPage = null },
        )
    }
    shareTotalError?.let { message ->
        AlertDialog(
            onDismissRequest = { shareTotalError = null },
            title = { Text("Check the split") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { shareTotalError = null }) { Text("Edit shares") }
            },
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
                        model.deleteExpense(original, draftKey, attachments, dismiss)
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
}
