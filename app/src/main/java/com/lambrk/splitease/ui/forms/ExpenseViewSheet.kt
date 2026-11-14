package com.lambrk.splitease.ui.forms

import com.lambrk.splitease.ui.i18n.tr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.lambrk.splitease.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.data.ExpenseAttachment
import com.lambrk.splitease.data.objects
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.components.AttachmentCard
import com.lambrk.splitease.ui.components.ExpenseAttachmentViewer
import com.lambrk.splitease.ui.components.ExpenseHistory
import com.lambrk.splitease.ui.components.ProfileAvatar
import com.lambrk.splitease.ui.components.ShareExpenseDialog
import com.lambrk.splitease.ui.components.openAttachmentInAndroid
import com.lambrk.splitease.ui.util.friendlyDate
import com.lambrk.splitease.ui.util.memberName
import com.lambrk.splitease.ui.util.memberPhotoUrl
import com.lambrk.splitease.ui.util.money
import com.lambrk.splitease.ui.util.optionalText
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Read-only, all-in-one look at an expense in a bottom sheet: the amount, who paid, how it was split
 * and who has paid back, plus notes, files and history. The payer gets an Edit button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpenseViewSheet(
    model: SplitEaseViewModel,
    expense: JSONObject,
    onEdit: () -> Unit,
    dismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf(expense) }
    var payTarget by remember { mutableStateOf<String?>(null) }
    var sharing by remember { mutableStateOf(false) }
    var galleryPage by remember { mutableStateOf<Int?>(null) }
    var openingDocument by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var historyFile by remember { mutableStateOf<ExpenseAttachment?>(null) }

    val currency = current.optString("currency")
    val total = current.optString("amount")
    val splitType = current.optString("splitType")
    val payerId = current.optString("paidBy")
    val pending = current.optString("id").startsWith("local:")
    val isPayer = payerId == model.userId
    val shares = current.optJSONArray("shares")?.objects().orEmpty()
    val attachments = remember(current) { ExpenseAttachment.fromExpense(current) }

    fun amountOf(share: JSONObject) = share.optString("amount").toBigDecimalOrNull() ?: BigDecimal.ZERO
    fun paidOf(share: JSONObject) =
        if (share.optString("userId") == payerId) amountOf(share) else share.optString("paidAmount").toBigDecimalOrNull() ?: BigDecimal.ZERO
    val others = shares.filter { it.optString("userId") != payerId }
    val owedToPayer = others.fold(BigDecimal.ZERO) { acc, s -> acc + amountOf(s) }
    val collected = others.fold(BigDecimal.ZERO) { acc, s -> acc + paidOf(s).min(amountOf(s)) }
    val me = shares.firstOrNull { it.optString("userId") == model.userId }

    // What was typed for % and share splits, rebuilt from the amounts the server keeps.
    val entered = remember(current) {
        if (splitType == "PERCENTAGE" || splitType == "SHARES") {
            convertSplitValues(
                splitType,
                shares.map { it.optString("userId") },
                shares.associate { it.optString("userId") to amountOf(it) },
                total,
            )
        } else emptyMap()
    }

    fun openDocument(attachment: ExpenseAttachment) {
        if (openingDocument) return
        openingDocument = true
        scope.launch {
            try {
                val file = model.openAttachment(attachment)
                if (!openAttachmentInAndroid(context, attachment, file)) model.error = tr("No installed app can open this file type.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                model.error = tr("This attachment couldn’t be opened.")
            } finally {
                openingDocument = false
            }
        }
    }

    fun label(id: String) = memberName(id, model).substringBefore(" (") + if (id == model.userId) " (Me)" else ""

    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Header: what, how much, who paid
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        current.optString("description"),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!pending) {
                        OutlinedButton(
                            onClick = { sharing = true },
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier.height(40.dp),
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(tr("Share"), Modifier.padding(start = 6.dp))
                        }
                        if (isPayer) {
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = onEdit,
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                modifier = Modifier.height(40.dp),
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(tr("Edit"), Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
                Text(
                    money(total, currency),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                val meta = listOfNotNull(
                    current.optionalText("category").ifBlank { null },
                    current.optString("expenseDate").takeIf { it.isNotBlank() }?.let { friendlyDate(it) },
                ).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(memberPhotoUrl(payerId, model), size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(tr("Paid by"), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Text(label(payerId), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            // ---- Where things stand
            if (others.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val headline = when {
                            isPayer && collected >= owedToPayer -> "Everyone has paid you back"
                            isPayer -> "${money((owedToPayer - collected).toPlainString(), currency)} still to receive"
                            me != null && paidOf(me) >= amountOf(me) -> "You've paid your share"
                            me != null -> "You owe ${money((amountOf(me) - paidOf(me)).max(BigDecimal.ZERO).toPlainString(), currency)}"
                            else -> "You're not part of this split"
                        }
                        Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val progress = if (owedToPayer.signum() > 0) (collected.toDouble() / owedToPayer.toDouble()).toFloat().coerceIn(0f, 1f) else 1f
                        LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(8.dp), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                        Text(
                            "${money(collected.toPlainString(), currency)} of ${money(owedToPayer.toPlainString(), currency)} paid back",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- The split
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Split", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Surface(shape = RoundedCornerShape(50), color = colors.secondaryContainer) {
                        Text(
                            when (splitType) {
                                "EXACT" -> "Exact amounts"
                                "PERCENTAGE" -> "By percentage"
                                "SHARES" -> "By shares"
                                else -> "Equally"
                            },
                            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, colors.outlineVariant), color = colors.surface) {
                    Column {
                        shares.forEachIndexed { index, share ->
                            val id = share.optString("userId")
                            val amount = amountOf(share)
                            val paid = paidOf(share)
                            val isShareHolderPayer = id == payerId
                            val status = when {
                                isShareHolderPayer -> "Paid" to colors.primary
                                paid >= amount -> "Paid" to colors.primary
                                paid.signum() > 0 -> "Partly paid" to colors.tertiary
                                else -> "Unpaid" to colors.error
                            }
                            val detail = listOfNotNull(
                                entered[id]?.let { if (splitType == "PERCENTAGE") "$it%" else "$it ${if (it == "1") "share" else "shares"}" },
                                if (!isShareHolderPayer && paid < amount && paid.signum() > 0)
                                    "${money(paid.toPlainString(), currency)} paid"
                                else null,
                                if (!isShareHolderPayer && paid < amount)
                                    share.optionalText("dueDate").ifBlank { null }?.let { "Due ${friendlyDate(it)}" }
                                else null,
                            ).joinToString(" · ")
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = isPayer && !pending && !isShareHolderPayer) { payTarget = id }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ProfileAvatar(memberPhotoUrl(id, model), size = 36.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(label(id), maxLines = 1, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    if (detail.isNotBlank()) {
                                        Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(money(amount.toPlainString(), currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        if (isShareHolderPayer) "Paid the bill" else status.first,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = status.second,
                                    )
                                }
                            }
                            if (index < shares.lastIndex) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
                if (isPayer && !pending && others.isNotEmpty()) {
                    Text(tr("Tap someone to mark that they paid, or to remind them."), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }

            // ---- Notes and dates
            val notes = current.optionalText("notes")
            val payBy = current.optionalText("payByDate").take(10)
            val remind = if (current.isNull("remindAfterDays")) 0 else current.optInt("remindAfterDays")
            if (notes.isNotBlank() || payBy.isNotBlank() || remind > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (notes.isNotBlank()) DetailLine("Notes", notes)
                            if (payBy.isNotBlank()) DetailLine("Pay by", friendlyDate(payBy))
                            if (remind > 0) DetailLine("Reminder", "After $remind ${if (remind == 1) "day" else "days"} if unpaid")
                        }
                    }
                }
            }

            // ---- Files
            if (attachments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Attachments · ${attachments.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    attachments.withIndex().chunked(2).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowItems.forEach { (index, attachment) ->
                                AttachmentCard(
                                    attachment = attachment,
                                    load = model::openAttachment,
                                    modifier = Modifier.weight(1f),
                                    enabled = !openingDocument,
                                    onClick = { if (attachment.isImage) galleryPage = index else openDocument(attachment) },
                                    onRemove = null,
                                )
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (openingDocument) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

            // ---- History
            if (!pending) {
                var requested by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Change history"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        TextButton(
                            enabled = !model.busy,
                            onClick = {
                                historyOpen = !historyOpen
                                if (historyOpen && !requested) {
                                    requested = true
                                    model.history(current)
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
                            else -> Text(tr("No history yet."), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }

            // ---- Notes about editing
            if (pending) {
                Text(tr("This split is still being saved. You can edit or share it in a moment."), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            } else if (!isPayer) {
                Text(
                    "Only ${label(payerId).substringBefore(" (")} can change this split.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }

    if (sharing) ShareExpenseDialog(model, current) { sharing = false }
    galleryPage?.let { page ->
        ExpenseAttachmentViewer(
            attachments = attachments,
            initialPage = page,
            load = model::openAttachment,
            openDocument = ::openDocument,
            onDismiss = { galleryPage = null },
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
    payTarget?.let { id ->
        val share = shares.firstOrNull { it.optString("userId") == id }
        if (share != null) {
            SharePaymentDialog(
                name = label(id).substringBefore(" ("),
                amount = amountOf(share),
                paid = share.optString("paidAmount").toBigDecimalOrNull() ?: BigDecimal.ZERO,
                currency = currency,
                description = current.optString("description"),
                busy = model.busy,
                onSettle = { model.recordSharePayment(current, id, null) { current = it; payTarget = null } },
                onPartial = { value -> model.recordSharePayment(current, id, value) { current = it; payTarget = null } },
                onNudge = { push, email -> model.nudgeUnpaid(current, id, push, email) },
                onDismiss = { payTarget = null },
                from = model.user?.optionalText("displayName").orEmpty(),
                onSplitEase = model.selected?.optJSONArray("members")?.objects().orEmpty().firstOrNull { it.optString("userId") == id }?.optBoolean("pending") != true,
                phone = model.selected?.optJSONArray("members")?.objects().orEmpty().firstOrNull { it.optString("userId") == id }?.optionalText("contactPhone")?.ifBlank { null },
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
