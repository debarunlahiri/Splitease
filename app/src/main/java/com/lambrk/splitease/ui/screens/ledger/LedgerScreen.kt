package com.lambrk.splitease.ui.screens.ledger

import androidx.compose.material.icons.rounded.Sms
import com.lambrk.splitease.ui.util.OwedLine
import com.lambrk.splitease.ui.util.Messages
import com.lambrk.splitease.ui.util.openWhatsApp
import com.lambrk.splitease.ui.i18n.tr

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.lambrk.splitease.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.lambrk.splitease.data.objects
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.components.EmptyState
import com.lambrk.splitease.ui.components.ProfileAvatar
import com.lambrk.splitease.ui.util.friendlyDate
import com.lambrk.splitease.ui.util.money
import com.lambrk.splitease.ui.util.optionalText
import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray?.totals(key: String) =
    this?.objects().orEmpty().map { it.getString("currency") to it.getString(key) }

/** Who owes me what, across every group, with reminders and a CSV export. */
@Composable
internal fun LedgerScreen(model: SplitEaseViewModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { model.refreshLedger() }
    val ledger = model.ledger
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(0) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp, 24.dp, 24.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            var menu by rememberSaveable { mutableStateOf(false) }
            fun share(file: java.io.File, type: String) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.attachments", file)
                val send = Intent(Intent.ACTION_SEND)
                    .setType(type)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "Export ledger"))
            }
            val scope = when (tab) { 1 -> "owed"; 2 -> "owe"; else -> "all" }
            val what = when (tab) { 1 -> "to get back"; 2 -> "you owe"; else -> "everything" }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ledger", Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Box {
                    androidx.compose.material3.IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Menu")
                    }
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Column { Text(tr("Export Excel")); Text(what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                            leadingIcon = { Icon(Icons.Rounded.FileDownload, null) },
                            enabled = !model.busy,
                            onClick = {
                                menu = false
                                model.exportLedger(scope, excel = true) {
                                    share(it, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                }
                            },
                        )
                    }
                }
            }
        }
        if (ledger == null) return@LazyColumn
        val people = ledger.getJSONArray("people").objects()
        val collected = ledger.optJSONArray("collectedThisMonth").totals("amount")
        val pending = ledger.optJSONArray("stillPending").totals("amount")
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Received this month"), style = MaterialTheme.typography.bodySmall)
                            if (collected.isEmpty()) Text(money("0", pending.firstOrNull()?.first ?: "INR"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            collected.forEach { (c, a) -> Text(money(a, c), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(tr("Still pending"), style = MaterialTheme.typography.bodySmall)
                            if (pending.isEmpty()) Text(tr("All paid"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            pending.forEach { (c, a) -> Text(money(a, c), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                        }
                    }
                    people.firstOrNull()?.let { top ->
                        HorizontalDivider()
                        val t = top.getJSONArray("totals").getJSONObject(0)
                        Text(
                            "${money(t.getString("owed"), t.getString("currency"))} waiting on ${top.getString("displayName")}, no rush, but you know.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        // People whose pay-by date has passed: one tap opens WhatsApp with a friendly message ready.
        val late = people.filter { p -> p.getJSONArray("splits").objects().any { it.optBoolean("overdue") } }
        if (late.isNotEmpty() && (tab == 0 || tab == 1)) {
            item(key = "late-card") { LateCard(late, model.user?.optionalText("displayName").orEmpty()) }
        }
        val youOwe = ledger.optJSONArray("youOwe")?.objects().orEmpty()
        val stillToPay = ledger.optJSONArray("stillToPay").totals("amount")
        item {
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("All") })
                androidx.compose.material3.FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text(tr("To get back")) })
                androidx.compose.material3.FilterChip(selected = tab == 3, onClick = { tab = 3 }, label = { Text("Invoices") })
                androidx.compose.material3.FilterChip(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    label = {
                        Text(
                            tr("You owe") + if (stillToPay.isNotEmpty()) " · " + stillToPay.joinToString(" + ") { money(it.second, it.first) } else "",
                        )
                    },
                )
            }
        }
        if (tab == 3) {
            item(key = "invoices-search") { InvoicesPanel(model) }
        }
        if (tab == 0 && people.isNotEmpty()) {
            item(key = "head-owed") { Text(tr("To get back"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        }
        if ((tab == 0 || tab == 1) && people.isEmpty() && (tab == 1 || youOwe.isEmpty())) {
            item { EmptyState(Icons.Rounded.Notifications, "Nobody owes you", "When friends owe you for a split you paid, they show up here.") }
        }
        if (tab == 0 || tab == 1) items(people, key = { it.getString("userId") }) { person ->
            val id = person.getString("userId")
            val open = expanded == id
            Surface(
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable { expanded = if (open) null else id }.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileAvatar(person.optionalText("photoUrl").ifBlank { null }, size = 44.dp)
                        val splits = person.getJSONArray("splits").length()
                        val totals = person.getJSONArray("totals").objects()
                        Column(Modifier.weight(1f)) {
                            Text(person.getString("displayName"), fontWeight = FontWeight.SemiBold)
                            val claimed = person.optJSONArray("claimed").totals("amount")
                            if (claimed.isNotEmpty()) {
                                Text(
                                    tr("Says they paid ") + claimed.joinToString(" + ") { money(it.second, it.first) } + " · check and confirm",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                "owes ${totals.joinToString(" + ") { money(it.getString("owed"), it.getString("currency")) }} across $splits ${if (splits == 1) "split" else "splits"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            totals.first().let { money(it.getString("owed"), it.getString("currency")) },
                            fontWeight = FontWeight.Bold,
                        )
                        Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    if (open) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            person.getJSONArray("splits").objects().forEach { split ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(split.getString("description"))
                                        DueText(split)
                                    }
                                    Text(money(split.getString("owed"), split.getString("currency")), fontWeight = FontWeight.Bold)
                                }
                            }
                            if (person.optBoolean("pending")) {
                                // Not on SplitEase: an in-app notification or email would reach no one, so message them instead.
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val phone = person.optionalText("phone").ifBlank { null }
                                val name = person.getString("displayName").substringBefore(" ")
                                val total = person.getJSONArray("totals").objects().joinToString(" + ") { money(it.getString("owed"), it.getString("currency")) }
                                val text = Messages.reminder(
                                    to = name,
                                    from = model.user?.optionalText("displayName").orEmpty(),
                                    lines = person.getJSONArray("splits").objects().map { owedLine(it) },
                                    total = total,
                                )
                                OutlinedButton(
                                    onClick = {
                                        val intent = if (phone != null)
                                            android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:${android.net.Uri.encode(phone)}")).putExtra("sms_body", text)
                                        else
                                            android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), "Send a message")
                                        runCatching { context.startActivity(intent) }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Rounded.Sms, null)
                                    Text(tr("Send a message"), Modifier.padding(start = 8.dp))
                                }
                                OutlinedButton(
                                    onClick = {
                                        val link = if (phone != null)
                                            "https://wa.me/${phone.removePrefix("+")}?text=${android.net.Uri.encode(text)}"
                                        else null
                                        val intent = if (link != null) android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                                        else android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), "Send a message")
                                        runCatching { context.startActivity(intent) }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(tr("Remind on WhatsApp")) }
                                Text(
                                    tr("{name} isn't on SplitEase yet, so the message goes from your phone.").replace("{name}", name),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                OutlinedButton(
                                    onClick = { model.remindPerson(id, push = true, email = false) },
                                    enabled = !model.busy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Rounded.Notifications, null)
                                    Text(tr("Send one reminder for all"), Modifier.padding(start = 8.dp))
                                }
                                OutlinedButton(
                                    onClick = { model.remindPerson(id, push = false, email = true) },
                                    enabled = !model.busy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Rounded.Email, null)
                                    Text(tr("Email one reminder"), Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (tab == 0 || tab == 2) {
            if (tab == 0 && youOwe.isNotEmpty()) {
                item(key = "head-owe") { Text(tr("You owe"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            }
            if (tab == 2 && youOwe.isEmpty()) {
                item { EmptyState(Icons.Rounded.Notifications, "You're all paid up", "Nothing is waiting for you to pay.") }
            }
            items(youOwe, key = { "owe-" + it.getString("userId") }) { payee -> YouOweCard(model, payee, expanded, { expanded = it }) }
        }
    }
}

@Composable
private fun DueText(split: JSONObject) {
    val due = split.optionalText("dueDate")
    val overdue = split.optBoolean("overdue")
    Text(
        if (due.isBlank()) friendlyDate(split.optString("expenseDate"))
        else (if (overdue) "Late since " else "Due ") + friendlyDate(due),
        style = MaterialTheme.typography.bodySmall,
        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal,
    )
}

/** What you owe one person across groups: pay by UPI, or tell them you've paid so they can confirm. */
@Composable
private fun YouOweCard(model: SplitEaseViewModel, payee: JSONObject, expanded: String?, onExpand: (String?) -> Unit) {
    val context = LocalContext.current
    val id = "owe-" + payee.getString("userId")
    val open = expanded == id
    val name = payee.getString("displayName").substringBefore(" (")
    val upi = payee.optionalText("upiId")
    val totals = payee.getJSONArray("totals").objects()
    val splits = payee.getJSONArray("splits").objects()
    val overdue = splits.any { it.optBoolean("overdue") }
    var paying by rememberSaveable { mutableStateOf<String?>(null) }
    Surface(
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { onExpand(if (open) null else id) }.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(payee.optionalText("photoUrl").ifBlank { null }, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "you owe across ${splits.size} ${if (splits.size == 1) "split" else "splits"}" + if (overdue) " · late" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(money(totals.first().getString("owed"), totals.first().getString("currency")), fontWeight = FontWeight.Bold)
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
            if (open) {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    splits.forEach { split ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(split.getString("description"))
                                DueText(split)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money(split.getString("owed"), split.getString("currency")), fontWeight = FontWeight.Bold)
                                TextButton(onClick = { paying = split.getString("expenseId") }, enabled = !model.busy) { Text(tr("I've paid")) }
                            }
                        }
                    }
                    val inr = totals.firstOrNull { it.getString("currency") == "INR" }
                    if (inr != null && upi.isNotBlank()) {
                        androidx.compose.material3.Button(
                            onClick = {
                                if (!com.lambrk.splitease.ui.util.openUpiPayment(context, upi, name, inr.getString("owed").toBigDecimal().setScale(2).toPlainString(), "SplitEase")) {
                                    model.message = tr("No UPI app found on this phone.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Pay ${money(inr.getString("owed"), "INR")} via UPI") }
                    }
                }
            }
        }
    }
    paying?.let { expenseId ->
        val split = splits.firstOrNull { it.getString("expenseId") == expenseId }
        if (split != null) {
            com.lambrk.splitease.ui.components.IPaidDialog(
                payerName = name,
                left = split.getString("owed").toBigDecimal(),
                currency = split.getString("currency"),
                busy = model.busy,
                onSend = { amount ->
                    model.claimPayment(expenseId, amount)
                    paying = null
                },
                onDismiss = { paying = null },
            )
        }
    }
}

@Composable
private fun InvoicesPanel(model: SplitEaseViewModel) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    // Search as you type, after a short pause.
    LaunchedEffect(query) {
        kotlinx.coroutines.delay(if (query.isBlank()) 0 else 350)
        model.searchInvoices(query)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(tr("Search invoice number, name or Splits")) },
            shape = RoundedCornerShape(18.dp),
        )
        if (model.invoicesLoading && model.invoices.isEmpty()) {
            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (model.invoices.isEmpty()) {
            Text(
                if (query.isBlank()) "No invoices yet. One is made when a friend's payment is confirmed and they open their invoice."
                else "No invoices match “${query.trim()}”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        model.invoices.forEach { inv ->
            val number = inv.getString("number")
            val expanded = open == number
            val payee = inv.getJSONObject("payee").getString("name").substringBefore(" (")
            val payer = inv.getJSONObject("payer").getString("name").substringBefore(" (")
            val received = inv.optString("role") == "PAYEE"
            val currency = inv.getString("currency")
            Surface(
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable { open = if (expanded) null else number }.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(number, fontWeight = FontWeight.Bold)
                            Text(
                                if (received) "Paid to you by $payer" else "You paid $payee",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                inv.getString("groupName") + " · " + friendlyDate(inv.optString("paidAt")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money(inv.optString("paid"), currency), fontWeight = FontWeight.Bold)
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (inv.optString("status") == "PAID") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                            ) {
                                Text(
                                    if (inv.optString("status") == "PAID") "Paid" else "Part paid",
                                    Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    if (expanded) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            inv.optJSONArray("items")?.objects().orEmpty().forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.optString("description"))
                                        Text(friendlyDate(item.optString("date")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(money(item.optString("paid"), currency), fontWeight = FontWeight.SemiBold)
                                }
                            }
                            val url = inv.optionalText("url")
                            if (url.isNotBlank()) {
                                androidx.compose.material3.Button(
                                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(tr("Open invoice · download PDF")) }
                                androidx.compose.material3.OutlinedButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent.createChooser(
                                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Invoice $number: $url"),
                                                "Share invoice",
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(tr("Share invoice link")) }
                            } else {
                                Text(
                                    "The link for this invoice has expired. Ask for a new pay link to download it again, and quote $number.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "These people are late": a short list with a WhatsApp reminder button for each. */
@Composable
private fun LateCard(late: List<org.json.JSONObject>, from: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = MaterialTheme.colorScheme
    var all by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val shown = if (all) late else late.take(3)
    Surface(shape = RoundedCornerShape(22.dp), color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Notifications, contentDescription = null)
                Column {
                    Text(
                        if (late.size == 1) "1 person is late" else "${late.size} people are late",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("A friendly nudge on WhatsApp usually does it.", style = MaterialTheme.typography.bodySmall)
                }
            }
            shown.forEach { person ->
                val splits = person.getJSONArray("splits").objects().filter { it.optBoolean("overdue") }
                val totals = splits.groupBy { it.getString("currency") }
                    .map { (c, list) -> money(list.fold(java.math.BigDecimal.ZERO) { a, x -> a + (x.getString("owed").toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) }.toPlainString(), c) }
                    .joinToString(" + ")
                val name = person.getString("displayName").substringBefore(" ")
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(person.getString("displayName"), fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(totals, style = MaterialTheme.typography.bodySmall)
                    }
                    androidx.compose.material3.FilledTonalButton(
                        onClick = {
                            openWhatsApp(
                                context,
                                person.optionalText("phone").ifBlank { null },
                                Messages.reminder(name, from, splits.map { owedLine(it) }, totals),
                            )
                        },
                    ) { Text("Remind on WhatsApp") }
                }
            }
            if (late.size > 3) {
                androidx.compose.material3.TextButton(onClick = { all = !all }, modifier = Modifier.padding(horizontal = 10.dp)) {
                    Text(if (all) "Show less" else "Show ${late.size - 3} more")
                }
            }
        }
    }
}

/** A split someone still owes for, as a line in a reminder message. */
private fun owedLine(split: org.json.JSONObject) = OwedLine(
    what = split.getString("description"),
    amount = money(split.getString("owed"), split.getString("currency")),
    due = split.optionalText("dueDate").ifBlank { null }?.let { friendlyDate(it.take(10)) },
)
