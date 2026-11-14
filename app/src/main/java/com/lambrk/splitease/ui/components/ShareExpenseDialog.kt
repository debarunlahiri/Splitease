package com.lambrk.splitease.ui.components

import com.lambrk.splitease.ui.util.Messages
import com.lambrk.splitease.ui.i18n.tr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.lambrk.splitease.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.data.objects
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.util.memberName
import com.lambrk.splitease.ui.util.money
import com.lambrk.splitease.ui.util.friendlyDate
import com.lambrk.splitease.ui.util.optionalText
import com.lambrk.splitease.ui.util.payPayload
import java.math.BigDecimal
import org.json.JSONObject

@Composable
private fun ShareOption(icon: ImageVector, title: String, detail: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Everything you can do to get an expense paid: QR code, link, message, or a nudge. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareExpenseDialog(model: SplitEaseViewModel, expense: JSONObject, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var link by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<Int?>(null) }
    var showQr by remember { mutableStateOf(false) }

    val currency = expense.optString("currency")
    val description = expense.optString("description")
    val payerId = expense.optString("paidBy")
    val payerName = memberName(payerId, model).substringBefore(" (")
    val payerUpi = (if (payerId == model.userId) model.user?.optionalText("upiId") else
        model.selected?.optJSONArray("members")?.objects().orEmpty().firstOrNull { it.optString("userId") == payerId }?.optionalText("upiId"))
        .orEmpty()
    fun left(share: JSONObject) = ((share.optString("amount").toBigDecimalOrNull() ?: BigDecimal.ZERO) -
        (share.optString("paidAmount").toBigDecimalOrNull() ?: BigDecimal.ZERO)).max(BigDecimal.ZERO)
    // People who still owe: everyone but the payer with something left to pay.
    val owing = expense.optJSONArray("shares")?.objects().orEmpty()
        .filter { it.optString("userId") != payerId && left(it).signum() > 0 }
        .map { memberName(it.getString("userId"), model).substringBefore(" (") to left(it).setScale(2).toPlainString() }
    val iAmPayer = payerId == model.userId
    val nameToId = expense.optJSONArray("shares")?.objects().orEmpty()
        .associate { memberName(it.getString("userId"), model).substringBefore(" (") to it.getString("userId") }
    // Contacts added by phone have no account: they can't get in-app or email reminders.
    val pendingIds = model.selected?.optJSONArray("members")?.objects().orEmpty()
        .filter { it.optBoolean("pending") }.map { it.optString("userId") }.toSet()
    val canRemind = (target?.let { i -> owing.getOrNull(i)?.first?.let { nameToId[it] }?.let { it !in pendingIds } }
        ?: owing.any { (name, _) -> nameToId[name] !in pendingIds })
    val dues = expense.optJSONArray("shares")?.objects().orEmpty()
        .filter { it.optionalText("dueDate").isNotBlank() }
        .associate { memberName(it.getString("userId"), model).substringBefore(" (") to it.optionalText("dueDate").take(10) }
    val payBy = expense.optionalText("payByDate").take(10).ifBlank { null }
    // A short link is made on the server once, when the dialog opens.
    LaunchedEffect(owing, payerUpi) {
        if (owing.isNotEmpty()) link = model.shortPayLink(payPayload(description, currency, payerName, payerUpi.ifBlank { null }, owing, payBy, dues,
            groupId = expense.optString("groupId").ifBlank { null },
            expenseId = expense.optString("id").takeIf { it.isNotBlank() && !it.startsWith("local:") },
            userIds = nameToId,
        ))
    }
    // WhatsApp shows *text* in bold, so the title and the amounts stand out in a busy group chat.
    val upiForMessage = payerUpi.takeIf { it.isNotBlank() && currency == "INR" }
    val groupMessage = Messages.groupSplit(
        title = description,
        total = money(expense.optString("amount"), currency),
        paidBy = payerName,
        owes = owing.map { (name, amount) -> name to money(amount, currency) },
        payBy = payBy?.let { friendlyDate(it) },
        link = link,
        upiId = upiForMessage,
    )
    // A link for one person opens straight to their share.
    val targetLink = link?.let { if (target != null) "$it?p=$target" else it }
    val message = target?.let { i ->
        owing.getOrNull(i)?.let { (name, amount) ->
            Messages.personalShare(
                to = name,
                title = description,
                amount = money(amount, currency),
                paidBy = payerName,
                payBy = payBy?.let { friendlyDate(it) },
                link = targetLink,
                upiId = upiForMessage,
            )
        }
    } ?: groupMessage
    fun send(text: String, title: String, whatsapp: Boolean = false) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        try {
            if (whatsapp) context.startActivity(Intent(intent).setPackage("com.whatsapp"))
            else context.startActivity(Intent.createChooser(intent, title))
        } catch (_: Exception) {
            context.startActivity(Intent.createChooser(intent, title))
        }
    }

    if (showQr) {
        UpiQrDialog(
            upiId = payerUpi,
            payeeName = payerName,
            note = description,
            entries = target?.let { i -> owing.getOrNull(i)?.let { listOf(it) } } ?: owing,
            onDismiss = { showQr = false },
        )
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Share \"$description\"", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (owing.isEmpty()) {
                Text(tr("Everyone has paid for this split."), color = MaterialTheme.colorScheme.primary)
            }
            SendToChips(owing.map { it.first }, target) { target = it }
            if (currency == "INR" && payerUpi.isNotBlank() && owing.isNotEmpty()) {
                ShareOption(Icons.Rounded.QrCode2, "Show QR code", "Scan with any UPI app to pay ${payerName}") { showQr = true }
            }
            ShareOption(
                Icons.Rounded.Share,
                if (target == null) "Share on WhatsApp" else "WhatsApp ${owing.getOrNull(target!!)?.first?.substringBefore(" ")}",
                if (target == null) "Message with the amounts and a pay link" else "A personal message with their share and link",
            ) {
                send(message, "Share split", whatsapp = true)
            }
            ShareOption(Icons.Rounded.Link, if (target == null) "Share link" else "Share ${owing.getOrNull(target!!)?.first?.substringBefore(" ")}'s link", "Works even if they don't have SplitEase", enabled = link != null) {
                send(targetLink ?: message, "Share link")
            }
            ShareOption(Icons.Rounded.Share, "Share anywhere", "Send the details with any app") { send(message, "Share split") }
            ShareOption(Icons.Rounded.ContentCopy, "Copy message", "Copy the details and link") {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Split", message))
                model.message = "Copied"
            }
            if (iAmPayer && owing.isNotEmpty() && canRemind) {
                ShareOption(Icons.Rounded.Notifications, "Remind in the app", "Notify everyone who hasn't paid", enabled = !model.busy) {
                    model.nudgeUnpaid(expense, null, push = true, email = false)
                    onDismiss()
                }
                ShareOption(Icons.Rounded.Email, "Remind by email", "Email everyone who hasn't paid", enabled = !model.busy) {
                    model.nudgeUnpaid(expense, null, push = false, email = true)
                    onDismiss()
                }
            }
        }
    }
}
