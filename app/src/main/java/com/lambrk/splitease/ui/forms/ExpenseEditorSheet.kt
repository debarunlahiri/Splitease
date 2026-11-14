package com.lambrk.splitease.ui.forms

import com.lambrk.splitease.ui.util.Messages
import androidx.compose.runtime.saveable.rememberSaveable
import com.lambrk.splitease.ui.i18n.tr

import com.lambrk.splitease.ui.util.friendlyDate
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.lambrk.splitease.ui.i18n.Text
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
import com.lambrk.splitease.ui.components.AttachmentCard
import com.lambrk.splitease.ui.components.CategoryField
import com.lambrk.splitease.ui.components.CurrencyDropdown
import com.lambrk.splitease.ui.components.DateField
import com.lambrk.splitease.ui.components.ExpenseAttachmentViewer
import com.lambrk.splitease.ui.components.ExpenseHistory
import com.lambrk.splitease.ui.components.Field
import com.lambrk.splitease.ui.components.FormScreen
import com.lambrk.splitease.ui.components.FormStep
import com.lambrk.splitease.ui.components.ProfileAvatar
import com.lambrk.splitease.ui.components.UpiQrDialog
import com.lambrk.splitease.ui.components.openAttachmentInAndroid
import com.lambrk.splitease.ui.util.currencySymbol
import com.lambrk.splitease.ui.util.memberName
import com.lambrk.splitease.ui.util.money
import com.lambrk.splitease.ui.util.splitAmounts
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
                model.error = tr("This draft is no longer available.")
                dismiss()
                return@LaunchedEffect
            }
            draft = saved
            ready = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            model.error = tr("This draft couldn’t be opened. Please try again.")
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
            else original?.optString("splitType") ?: "EQUAL"
        )
    }
    val members = group.getJSONArray("members").objects()
    var shares by remember {
        mutableStateOf(
            draft?.optJSONObject("shareValues")?.let { values ->
                values.keys().asSequence().associateWith { values.getString(it) }
            } ?: original?.optJSONArray("shares")?.objects().orEmpty().let { rows ->
                val type = original?.optString("splitType")
                if (type == "PERCENTAGE" || type == "SHARES") {
                    // The server only returns final amounts, so rebuild the percentages / share counts from them.
                    convertSplitValues(
                        type,
                        rows.map { it.getString("userId") },
                        rows.associate { it.getString("userId") to (it.optString("amount").toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) },
                        original?.optString("amount").orEmpty(),
                    )
                } else rows.associate { it.getString("userId") to it.optString("value") }
            }
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
    var remindAfterDays by remember {
        mutableStateOf(initial?.takeIf { it.has("remindAfterDays") && !it.isNull("remindAfterDays") }?.optInt("remindAfterDays"))
    }
    var payByDate by remember {
        mutableStateOf(initial?.optionalText("payByDate")?.takeIf { it.isNotBlank() }?.take(10))
    }
    var personDue by remember {
        mutableStateOf(
            (draft?.optJSONObject("personDue")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }
                ?: original?.optJSONArray("shares")?.objects().orEmpty()
                    .filter { it.has("payByDate") && !it.isNull("payByDate") }
                    .associate { it.getString("userId") to it.getString("payByDate").take(10) })
        )
    }
    var repeat by rememberSaveable { mutableStateOf<String?>(null) }
    var saveTemplate by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }
    var templates by remember { mutableStateOf(emptyList<JSONObject>()) }
    LaunchedEffect(Unit) { if (original == null) templates = model.expenseTemplates() }
    var statuses by remember {
        mutableStateOf(original?.optJSONArray("shares")?.objects().orEmpty().associateBy { it.getString("userId") })
    }
    var payTarget by remember { mutableStateOf<String?>(null) }
    var live by remember { mutableStateOf(false) }
    var historyFile by remember { mutableStateOf<ExpenseAttachment?>(null) }
    var showQr by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    var shareLink by remember { mutableStateOf<String?>(null) }
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
                if (failed) model.error = tr("Some files couldn’t be attached. Choose readable files up to 10 MB each.")
                else if (uris.size > remaining) model.error = tr("You can attach up to 5 files to a split.")
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
                    model.error = tr("No installed app can open this file type.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                model.error = tr("This attachment couldn’t be opened.")
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
        "remindAfterDays" to remindAfterDays,
        "payByDate" to payByDate,
        "personDue" to JSONObject(personDue.toSortedMap()),
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
    val ordered = members.map { it.getString("userId") }.filter { it in participants }
    val shareAmounts = splitAmounts(amount, split, ordered, shares)
    val payerId = original?.optString("paidBy") ?: model.userId
    fun applyTemplate(t: JSONObject) {
        description = t.optString("description")
        t.opt("amount")?.takeIf { it != JSONObject.NULL }?.let { amount = it.toString() }
        currency = t.optString("currency").ifBlank { currency }
        category = t.optionalText("category")
        notes = t.optionalText("notes")
        split = t.optString("splitType", "EQUAL")
        remindAfterDays = if (t.isNull("remindAfterDays")) null else t.optInt("remindAfterDays")
        val saved = t.optJSONArray("shares")?.objects().orEmpty()
        val valid = saved.map { it.getString("userId") }.filter { id -> members.any { it.getString("userId") == id } }
        if (valid.isNotEmpty()) participants = valid.toSet()
        shares = saved.filter { it.getString("userId") in valid }
            .associate { it.getString("userId") to (it.opt("value")?.takeIf { v -> v != JSONObject.NULL }?.toString().orEmpty()) }
    }
    LaunchedEffect(live) {
        if (!live) return@LaunchedEffect
        val names = ordered.associate { id -> memberName(id, model).substringBefore(" (") to id }
        // The expense syncs in the background; wait briefly for its server id so friends can tell you they paid.
        var synced: JSONObject? = null
        repeat(20) {
            synced = model.expenses.firstOrNull {
                !it.optString("id").startsWith("local:") && it.optString("paidBy") == payerId &&
                    it.optString("description") == description.trim() &&
                    it.optString("amount").toBigDecimalOrNull()?.compareTo(amount.toBigDecimalOrNull() ?: java.math.BigDecimal(-1)) == 0 &&
                    java.time.OffsetDateTime.parse(it.optString("createdAt", "1970-01-01T00:00:00Z")).isAfter(java.time.OffsetDateTime.now().minusMinutes(10))
            }
            if (synced != null) return@repeat
            kotlinx.coroutines.delay(750)
        }
        val expenseId = synced?.optString("id")
        shareLink = model.shortPayLink(
            com.lambrk.splitease.ui.util.payPayload(
                description.trim(),
                currency,
                memberName(payerId, model).substringBefore(" ("),
                model.user?.optionalText("upiId"),
                ordered.filter { it != payerId }.map { id ->
                    memberName(id, model).substringBefore(" (") to (shareAmounts[id]?.setScale(2)?.toPlainString() ?: "0")
                },
                payByDate,
                ordered.associate { id -> memberName(id, model).substringBefore(" (") to (personDue[id] ?: payByDate) },
                groupId = if (expenseId != null) model.selected?.optString("id") else null,
                expenseId = expenseId,
                userIds = if (expenseId != null) names else emptyMap(),
            )
        )
    }
    if (live) {
        SplitLiveScreen(
            summary = Messages.groupSplit(
                title = description.trim(),
                total = money(amount, currency),
                paidBy = memberName(payerId, model),
                owes = ordered.filter { it != payerId }.map { id -> memberName(id, model) to (shareAmounts[id]?.let { money(it.toPlainString(), currency) } ?: "-") },
                payBy = payByDate?.let { friendlyDate(it) },
                link = shareLink,
                upiId = null,
            ),
            people = ordered.filter { it != model.userId }.map { id ->
                memberName(id, model) to (shareAmounts[id]?.let { money(it.toPlainString(), currency) } ?: "—")
            },
            personalMessage = { name, owed ->
                Messages.personalShare(
                    to = name,
                    title = description.trim(),
                    amount = owed,
                    paidBy = memberName(payerId, model),
                    payBy = payByDate?.let { friendlyDate(it) },
                    link = shareLink,
                    upiId = null,
                )
            },
            onShowQr = if (currency == "INR" && !model.user?.optionalText("upiId").isNullOrBlank() && ordered.any { it != payerId }) { { showQr = true } } else null,
            onDone = dismiss,
        )
    } else FormScreen(
        title = when {
            viewOnly -> "Draft details"
            original == null -> "New split"
            else -> "Split details"
        },
        model = model,
        dismiss = dismiss,
        submitLabel = if (original == null) "Create split" else "Save split",
        submitEnabled = !staging && !openingDocument && description.isNotBlank() && amount.isNotBlank(),
        onSubmit = if (!editable) null else {
            {
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
                    done = { if (original == null) live = true else dismiss() },
                    remindAfterDays = remindAfterDays,
                    payByDate = payByDate,
                    dueDates = personDue.filterKeys { it in participants },
                    templateName = if (saveTemplate) templateName.ifBlank { description }.trim() else null,
                    repeat = if (original == null) repeat else null,
                )
            }
        },
        confirmDismiss = {
            val blocked = staging || openingDocument
            val changed = editable && snapshot().toString() != initialSnapshot
            if (!blocked && changed) confirmDraft = true
            !blocked && !changed
        },
        steps = listOf(
            FormStep(tr("What did you share?"), "${group.getString("name")} · ${group.getString("defaultCurrency")}", valid = description.isNotBlank() && amount.isNotBlank()) {
                if (editable && original == null) ScanReceiptCard { showScan = true }
                if (editable && original == null) TemplateChips(templates, ::applyTemplate)
        if (draft != null) {
            Text(tr("Draft · Not submitted"), color = MaterialTheme.colorScheme.primary)
        }
        Field(description, { description = it }, "Description", enabled = editable)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1.2f)) {
                CurrencyDropdown(
                    value = currency,
                    onValueChange = { currency = it },
                    enabled = editable && !model.busy,
                )
            }
            Box(Modifier.weight(1f)) {
                Field(
                    amount,
                    { amount = it },
                    "Amount",
                    decimal = true,
                    enabled = editable,
                    prefixText = currencySymbol(currency),
                    groupingCurrency = currency,
                )
            }
        }
        DateField(date, { date = it }, "Date", enabled = editable)
        CategoryField(category, { category = it }, categorySuggestions, enabled = editable)
            },
            FormStep(tr("Who's in?"), tr("Search the people in this Splits, or pick from your contacts."), valid = participants.isNotEmpty()) {
                WhoIsInStep(model, members, participants, { participants = it }, editable, ::personLabel)
            },
            FormStep(
                tr("Split it"),
                valid = splitIsComplete(split, amount, ordered, shares),
            ) {
                SplitItStep(
                    model = model,
                    total = amount,
                    currency = currency,
                    split = split,
                    onSplit = { type ->
                        if (split != type) highlightShares = false
                        if (type != "EQUAL" && type != split) {
                            shares = convertSplitValues(type, ordered, shareAmounts, amount)
                        }
                        split = type
                    },
                    people = ordered,
                    shares = shares,
                    onShare = { id, value -> shares = shares + (id to value) },
                    amounts = shareAmounts,
                    enabled = editable,
                    highlight = highlightShares,
                    personLabel = ::personLabel,
                    payerLabel = memberName(payerId, model),
                    paymentStatus = { id ->
                        val st = statuses[id]
                        if (original == null || !editable || pending || id == payerId || st == null || !st.has("status")) null
                        else when (st.optString("status")) {
                            "PAID" -> "Paid" to ""
                            "PARTIAL" -> "Partly paid" to ""
                            else -> "Unpaid" to ""
                        }
                    },
                    onPaymentClick = { payTarget = it },
                )
            },
            FormStep(tr("Looks right?"), valid = true) {
                if (editable && original == null && currency == "INR") UpiPrompt(model)
                if (payerId == model.userId && currency == "INR" && !model.user?.optionalText("upiId").isNullOrBlank() && ordered.any { it != payerId } && splitIsComplete(split, amount, ordered, shares)) {
                    OutlinedButton(onClick = { showQr = true }, modifier = Modifier.fillMaxWidth()) { Text(tr("Show QR to get paid")) }
                }
                ReviewStep(
                    description = description,
                    total = amount,
                    currency = currency,
                    people = ordered,
                    amounts = shareAmounts,
                    personLabel = ::personLabel,
                    photo = { memberPhotoUrl(it, model) },
                    notes = notes,
                    onNotes = { notes = it },
                    remindAfterDays = remindAfterDays,
                    onRemind = { remindAfterDays = it },
                    payByDate = payByDate,
                    onPayBy = { payByDate = it },
                    saveTemplate = saveTemplate,
                    onSaveTemplate = { saveTemplate = it },
                    templateName = templateName,
                    onTemplateName = { templateName = it },
                    enabled = editable,
                    showTemplate = editable && original == null,
                    repeat = repeat,
                    onRepeat = { repeat = it },
                    showRepeat = editable && original == null,
                    splitDate = date,
                    dueFor = ordered.filter { it != payerId },
                    personDue = personDue,
                    onPersonDue = { id, date -> personDue = if (date == null) personDue - id else personDue + (id to date) },
                )
        Text(
            "Attachments · ${attachments.size} of $MAX_EXPENSE_ATTACHMENTS",
            style = MaterialTheme.typography.titleMedium,
        )
        attachments.withIndex().chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { (index, attachment) ->
                    AttachmentCard(
                        attachment = attachment,
                        load = model::openAttachment,
                        modifier = Modifier.weight(1f),
                        enabled = !openingDocument && !staging && !model.busy,
                        onClick = {
                            if (attachment.isImage) galleryPage = index else openDocument(attachment)
                        },
                        onRemove = if (!editable) null else {
                            {
                                attachments = attachments - attachment
                                if (attachment !in originalAttachments && attachment !in savedAttachments) {
                                    scope.launch { model.discardAttachments(listOf(attachment)) }
                                }
                            }
                        },
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (staging || openingDocument) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (editable && attachments.size < MAX_EXPENSE_ATTACHMENTS) {
            TextButton(
                enabled = !staging && !openingDocument && !model.busy,
                onClick = { picker.launch(arrayOf("*/*")) },
            ) {
                Icon(Icons.Rounded.AttachFile, contentDescription = null)
                Text(tr("Add files"))
            }
            Text(tr("Up to 10 MB per file. Photos are converted to JPEG, PNG or WebP."), style = MaterialTheme.typography.bodySmall)
        }
        if (original != null && !pending && !viewOnly) {
            var historyOpen by remember { mutableStateOf(false) }
            var historyRequested by remember { mutableStateOf(false) }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Change history"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(
                            enabled = !model.busy,
                            onClick = {
                                historyOpen = !historyOpen
                                if (historyOpen && !historyRequested) {
                                    historyRequested = true
                                    model.history(original)
                                }
                            },
                        ) { Text(if (historyOpen) "Hide" else "Show") }
                    }
                    if (historyOpen) {
                        when {
                            model.audit.isNotEmpty() -> ExpenseHistory(
                                model.audit,
                                load = model::openAttachment,
                                onOpen = { file -> if (file.isImage) historyFile = file else openDocument(file) },
                            )
                            model.busy -> LinearProgressIndicator(Modifier.fillMaxWidth())
                            else -> Text(
                                tr("No history yet."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
            if (editable && original != null)
                TextButton(
                    enabled = !model.busy && !staging && !openingDocument,
                    onClick = { confirmDelete = true },
                ) {
                    Text(tr("Delete split"), color = MaterialTheme.colorScheme.error)
                }
        if (!editable && !viewOnly)
            Text(
                if (pending) "This split is still being saved. Try again in a moment."
                else "Only the person who paid can change this split.",
                style = MaterialTheme.typography.bodySmall,
            )
            },
        ),
    )
    if (showScan) {
        ReceiptScanSheet(
            currency = currency,
            onDismiss = { showScan = false },
            onApply = { scan ->
                showScan = false
                if (description.isBlank() && scan.merchant.isNotBlank()) description = scan.merchant
                amount = scan.total
                scan.date?.let { date = it.toString() }
                scan.itemsNote?.let { notes = if (notes.isBlank()) it else notes + "\n" + it }
                // Keep the photo with the split so everyone can see the bill.
                if (attachments.size < MAX_EXPENSE_ATTACHMENTS) {
                    scope.launch { runCatching { attachments = attachments + model.stageAttachment(scan.photo) } }
                }
            },
        )
    }
    if (showQr) {
        UpiQrDialog(
            upiId = model.user?.optionalText("upiId").orEmpty(),
            payeeName = memberName(payerId, model).substringBefore(" ("),
            note = description.trim(),
            entries = ordered.filter { it != payerId }.map { id ->
                memberName(id, model).substringBefore(" (") to (shareAmounts[id]?.setScale(2)?.toPlainString() ?: "0")
            },
            onDismiss = { showQr = false },
        )
    }
    payTarget?.let { id ->
        val st = statuses[id]
        if (original != null && st != null) {
            SharePaymentDialog(
                name = memberName(id, model),
                amount = st.optString("amount").toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO,
                paid = st.optString("paidAmount").toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO,
                currency = currency,
                description = description,
                busy = model.busy,
                onSettle = {
                    model.recordSharePayment(original, id, null) { updated ->
                        statuses = updated.getJSONArray("shares").objects().associateBy { it.getString("userId") }
                        payTarget = null
                    }
                },
                onPartial = { value ->
                    model.recordSharePayment(original, id, value) { updated ->
                        statuses = updated.getJSONArray("shares").objects().associateBy { it.getString("userId") }
                        payTarget = null
                    }
                },
                onNudge = { push, email -> model.nudgeUnpaid(original, id, push, email) },
                onDismiss = { payTarget = null },
                from = model.user?.optionalText("displayName").orEmpty(),
                onSplitEase = members.firstOrNull { it.getString("userId") == id }?.optBoolean("pending") != true,
                phone = members.firstOrNull { it.getString("userId") == id }?.optionalText("contactPhone")?.ifBlank { null },
            )
        }
    }
    if (confirmDraft) {
        AlertDialog(
            onDismissRequest = { if (!model.busy) confirmDraft = false },
            title = { Text(tr("Save this draft?")) },
            text = { Text(tr("Keep your changes on this device and finish this split later.")) },
            confirmButton = {
                TextButton(
                    enabled = !model.busy,
                    onClick = {
                        model.saveExpenseDraft(draftKey, snapshot()) {
                            confirmDraft = false
                            dismiss()
                        }
                    },
                ) { Text(tr("Save draft")) }
            },
            dismissButton = {
                TextButton(
                    enabled = !model.busy,
                    onClick = { confirmDraft = false },
                ) { Text(tr("Keep editing")) }
            },
        )
    }
    historyFile?.let { file ->
        ExpenseAttachmentViewer(
            attachments = listOf(file),
            initialPage = 0,
            load = model::openAttachment,
            openDocument = ::openDocument,
            onDismiss = { historyFile = null },
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
            title = { Text(tr("Check the split")) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { shareTotalError = null }) { Text(tr("Edit shares")) }
            },
        )
    }
    if (confirmDelete && original != null)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Delete this split?")) },
            text = { Text(tr("This removes the split and updates what everyone owes.")) },
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
