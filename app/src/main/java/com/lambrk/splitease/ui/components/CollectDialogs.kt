@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lambrk.splitease.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.util.money
import com.lambrk.splitease.ui.util.payPayload
import java.math.BigDecimal

/** Collect everything a group owes you: one QR / link per person with their combined total. */
@Composable
internal fun CollectAllSheet(
    model: SplitEaseViewModel,
    groupName: String,
    currency: String,
    owing: List<Pair<String, String>>,
    ids: Map<String, String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var link by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<Int?>(null) }
    var showQr by remember { mutableStateOf(false) }
    val myName = (model.user?.optString("displayName") ?: "").substringBefore(" (")
    val upi = model.user?.optString("upiId").orEmpty().let { if (it == "null") "" else it }
    val total = owing.fold(BigDecimal.ZERO) { a, b -> a + (b.second.toBigDecimalOrNull() ?: BigDecimal.ZERO) }
    LaunchedEffect(owing) {
        if (owing.isNotEmpty()) link = model.shortPayLink(
            payPayload(
                groupName, currency, myName, upi.ifBlank { null }, owing, null,
                groupId = model.selected?.optString("id"), userIds = ids,
            )
        )
    }
    val groupMessage = buildString {
        append("$groupName · ${money(total.toPlainString(), currency)} to collect\n")
        owing.forEach { (name, amount) -> append("$name owes ${money(amount, currency)}\n") }
        append("Paid by $myName")
        if (upi.isNotBlank() && currency == "INR") append(" · UPI $upi")
        link?.let { append("\nPay / details: $it") }
    }
    // A link for one person opens straight to their share.
    val targetLink = link?.let { if (target != null) "$it?p=$target" else it }
    val message = target?.let { i ->
        owing.getOrNull(i)?.let { (name, amount) ->
            buildString {
                append("Hi ${name.substringBefore(" ")}, your share for $groupName is ${money(amount, currency)}.")
                if (upi.isNotBlank() && currency == "INR") append(" Pay $myName on UPI: $upi.")
                targetLink?.let { append("\nPay here: $it") }
            }
        }
    } ?: groupMessage
    fun send(text: String, whatsapp: Boolean = false) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        try {
            if (whatsapp) context.startActivity(Intent(intent).setPackage("com.whatsapp"))
            else context.startActivity(Intent.createChooser(intent, "Share"))
        } catch (_: Exception) {
            context.startActivity(Intent.createChooser(intent, "Share"))
        }
    }
    if (showQr) {
        UpiQrDialog(upiId = upi, payeeName = myName, note = groupName, entries = target?.let { i -> owing.getOrNull(i)?.let { listOf(it) } } ?: owing, onDismiss = { showQr = false })
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Collect from everyone", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(money(total.toPlainString(), currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            owing.forEach { (name, amount) ->
                Text("$name · ${money(amount, currency)}", style = MaterialTheme.typography.bodyLarge)
            }
            SendToChips(owing.map { it.first }, target) { target = it }
            if (currency == "INR" && upi.isNotBlank()) {
                Button(onClick = { showQr = true }, Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Icon(Icons.Rounded.QrCode2, null)
                    Text("Show QR (pick a person)", Modifier.padding(start = 8.dp))
                }
            } else if (currency == "INR") {
                Text("Add your UPI ID in the You tab to collect with a QR code.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { send(message, whatsapp = true) }, Modifier.fillMaxWidth()) {
                androidx.compose.material3.Icon(Icons.Rounded.Share, null)
                Text(if (target == null) "Share on WhatsApp" else "WhatsApp ${owing.getOrNull(target!!)?.first?.substringBefore(" ")}", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { send(targetLink ?: message) }, Modifier.fillMaxWidth(), enabled = link != null) {
                androidx.compose.material3.Icon(Icons.Rounded.Link, null)
                Text(if (target == null) "Share link" else "Share ${owing.getOrNull(target!!)?.first?.substringBefore(" ")}'s link", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Collect", message))
                    model.message = "Copied"
                },
                Modifier.fillMaxWidth(),
            ) {
                androidx.compose.material3.Icon(Icons.Rounded.ContentCopy, null)
                Text("Copy message", Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** "I've paid": the whole share, or just part of it. The payer then checks and confirms. */
@Composable
internal fun IPaidDialog(
    payerName: String,
    left: BigDecimal,
    currency: String,
    busy: Boolean,
    onSend: (amount: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var partial by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    val parsed = value.toBigDecimalOrNull()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Did you pay $payerName?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$payerName will check that the money arrived and confirm it.")
                if (partial) {
                    Field(value, { value = com.lambrk.splitease.ui.components.sanitizeAmount(it) }, "Amount you paid", decimal = true)
                }
            }
        },
        confirmButton = {
            if (!partial) {
                Button(onClick = { onSend(null) }, enabled = !busy) { Text("I paid ${money(left.toPlainString(), currency)}") }
            } else {
                Button(
                    onClick = { onSend(value) },
                    enabled = !busy && parsed != null && parsed.signum() > 0 && parsed <= left,
                ) { Text("Send") }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (partial) partial = false else partial = true }, enabled = !busy) {
                Text(if (partial) "Pay full amount" else "I paid a different amount")
            }
        },
    )
}

/** Who a message or link is for: everyone (a group chat) or one person. */
@Composable
internal fun SendToChips(names: List<String>, target: Int?, onTarget: (Int?) -> Unit) {
    if (names.isEmpty()) return
    Text("Send to", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    androidx.compose.foundation.layout.Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.FilterChip(selected = target == null, onClick = { onTarget(null) }, label = { Text("Everyone / group chat") })
        names.forEachIndexed { i, name ->
            androidx.compose.material3.FilterChip(selected = target == i, onClick = { onTarget(i) }, label = { Text(name.substringBefore(" ")) })
        }
    }
}
