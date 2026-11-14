package com.lambrk.splitease.ui.screens.groups

import androidx.compose.runtime.Composable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import com.lambrk.splitease.ui.i18n.tr

import com.lambrk.splitease.ui.util.friendlyDate
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
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import com.lambrk.splitease.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import com.lambrk.splitease.data.objects
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.components.EmptyState
import com.lambrk.splitease.ui.components.ProfileAvatar
import com.lambrk.splitease.ui.components.Symbol
import com.lambrk.splitease.ui.util.memberName
import com.lambrk.splitease.ui.util.memberPhotoUrl
import com.lambrk.splitease.ui.util.money
import com.lambrk.splitease.ui.util.optionalText
import org.json.JSONObject

/** How much of an expense is paid back: people who have paid, people who owe, and the money still due. */
private class Collection(val settled: Int, val total: Int, val left: java.math.BigDecimal, val unpaid: Int)

private fun collection(expense: JSONObject): Collection? {
    val payer = expense.optString("paidBy")
    val shares = expense.optJSONArray("shares")?.objects().orEmpty().filter { it.optString("userId") != payer }
    // Expenses still waiting to sync have no payment information yet.
    if (shares.isEmpty() || shares.any { !it.has("status") }) return null
    var left = java.math.BigDecimal.ZERO
    shares.forEach {
        left += (it.optString("amount").toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) -
            (it.optString("paidAmount").toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO)
    }
    return Collection(
        shares.count { it.optString("status") == "PAID" },
        shares.size,
        left.max(java.math.BigDecimal.ZERO),
        shares.count { it.optString("status") != "PAID" },
    )
}

private enum class Tone { Good, Pending, Neutral }

private class MyStatus(val text: String, val tone: Tone, val owedToMe: java.math.BigDecimal, val iOwe: java.math.BigDecimal, val payer: String = "")

private fun zero() = java.math.BigDecimal.ZERO

/** What this expense means for the signed-in user, in plain words. Null while it is still syncing. */
private fun myStatus(expense: JSONObject, me: String, model: SplitEaseViewModel): MyStatus? {
    val currency = expense.optString("currency")
    val payer = expense.optString("paidBy")
    val shares = expense.optJSONArray("shares")?.objects().orEmpty()
    if (shares.isEmpty() || shares.any { !it.has("status") }) return null
    fun left(share: JSONObject) =
        ((share.optString("amount").toBigDecimalOrNull() ?: zero()) -
            (share.optString("paidAmount").toBigDecimalOrNull() ?: zero())).max(zero())
    if (payer == me) {
        val others = shares.filter { it.optString("userId") != me }
        if (others.isEmpty()) return MyStatus("Only you in this split", Tone.Neutral, zero(), zero())
        val owing = others.filter { left(it).signum() > 0 }
        val total = owing.fold(zero()) { a, b -> a + left(b) }
        return if (owing.isEmpty()) MyStatus("Everyone paid you back", Tone.Good, zero(), zero())
        else MyStatus(
            "${money(total.toPlainString(), currency)} still to receive · ${others.size - owing.size} of ${others.size} paid",
            Tone.Pending,
            total,
            zero(),
        )
    }
    val mine = shares.firstOrNull { it.optString("userId") == me }
        ?: return MyStatus("You're not part of this split", Tone.Neutral, zero(), zero())
    val owe = left(mine)
    return if (owe.signum() == 0) MyStatus("You've paid your share", Tone.Good, zero(), zero())
    else MyStatus(
        "You owe ${money(owe.toPlainString(), currency)} to ${memberName(payer, model).substringBefore(" (")}",
        Tone.Pending,
        zero(),
        owe,
        payer,
    )
}

internal fun LazyListScope.GroupExpensesContent(
    model: SplitEaseViewModel,
    onEdit: (JSONObject) -> Unit,
    onDraft: (String) -> Unit,
) {
    item(key = "repeating-splits") { RepeatingSplits(model) }
    val drafts = model.groupDrafts
    items(drafts, key = { it.snapshotKey }) { draft ->
        val fields = JSONObject(draft.json)
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
                    Text(fields.optString("description").ifBlank { "Untitled split" })
                },
                supportingContent = { Text(tr("Draft · Not submitted")) },
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
    // Group summary from my point of view: what I'm owed and what I owe, per currency.
    val statuses = model.expenses.associate { it.getString("id") to myStatus(it, model.userId, model) }
    val owedToMe = model.expenses.mapNotNull { e -> statuses[e.getString("id")]?.let { e.optString("currency") to it.owedToMe } }
        .groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.fold(zero()) { a, b -> a + b } }.filterValues { it.signum() > 0 }
    val iOwe = model.expenses.mapNotNull { e -> statuses[e.getString("id")]?.let { e.optString("currency") to it.iOwe } }
        .groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.fold(zero()) { a, b -> a + b } }.filterValues { it.signum() > 0 }
    if (statuses.values.any { it != null }) {
        item(key = "group-claims-refresh") {
            androidx.compose.runtime.LaunchedEffect(model.selected?.optString("id"), model.expenses.size) { model.refreshClaims() }
        }
        item(key = "pending-confirmations") {
            // People who say they paid you: check your bank or UPI app, then confirm them all at once.
            val mine = model.claims.filter { it.optString("paidBy") == model.userId }
            val byPerson = mine.groupBy { it.optString("userId") }
            if (byPerson.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    byPerson.forEach { (_, list) ->
                        val total = list.fold(zero()) { a, c -> a + (c.optString("amount").toBigDecimalOrNull() ?: zero()) }
                        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "${list.first().optString("userName").substringBefore(" (")} says they paid ${money(total.toPlainString(), list.first().optString("currency"))}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    (if (list.size == 1) "For ${list.first().optString("description")}. " else "Across ${list.size} splits. ") +
                                        "Check your bank or UPI app to see if it arrived.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = { model.resolveClaims(list.map { it.getString("id") }, false) },
                                        enabled = !model.busy,
                                        modifier = Modifier.weight(1f),
                                    ) { Text(tr("Not received")) }
                                    androidx.compose.material3.Button(
                                        onClick = { model.resolveClaims(list.map { it.getString("id") }, true) },
                                        enabled = !model.busy,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Received") }
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "group-summary") {
            var collecting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            val group = model.selected
            val currency = group?.optString("defaultCurrency").orEmpty()
            // Combined total per person across the group's expenses I paid, in the group's currency.
            val perPerson = model.expenses
                .filter { it.optString("paidBy") == model.userId && it.optString("currency") == currency }
                .flatMap { e -> e.optJSONArray("shares")?.objects().orEmpty().filter { it.optString("userId") != model.userId } }
                .groupBy { it.optString("userId") }
                .mapValues { (_, list) ->
                    list.fold(zero()) { a, sh ->
                        a + ((sh.optString("amount").toBigDecimalOrNull() ?: zero()) - (sh.optString("paidAmount").toBigDecimalOrNull() ?: zero())).max(zero())
                    }
                }
                .filterValues { it.signum() > 0 }
                .map { (id, total) -> id to total.setScale(2).toPlainString() }
            val names = perPerson.associate { (id, _) -> memberName(id, model).substringBefore(" (") to id }
            val perPersonNamed = perPerson.map { (id, total) -> memberName(id, model).substringBefore(" (") to total }
            if (collecting && group != null) {
                com.lambrk.splitease.ui.components.CollectAllSheet(model, group.optString("name"), currency, perPersonNamed, names) { collecting = false }
            }
            val scheme = MaterialTheme.colorScheme
            if (owedToMe.isEmpty() && iOwe.isEmpty()) {
                Surface(shape = RoundedCornerShape(22.dp), color = scheme.surfaceContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = scheme.primary)
                        Text(tr("Everything here is paid up"), style = MaterialTheme.typography.titleMedium, color = scheme.primary)
                    }
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    @Composable
                    fun Tile(label: String, map: Map<String, java.math.BigDecimal>, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color, modifier: Modifier) {
                        Surface(modifier, shape = RoundedCornerShape(20.dp), color = container, contentColor = content) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(tr(label), style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.8f))
                                if (map.isEmpty()) Text("—", style = MaterialTheme.typography.titleLarge)
                                map.forEach { (c, a) ->
                                    Text(money(a.toPlainString(), c), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Tile("You get back", owedToMe, scheme.primaryContainer.copy(alpha = 0.55f), scheme.onPrimaryContainer, Modifier.weight(1f))
                    Tile("You owe", iOwe, if (iOwe.isEmpty()) scheme.surfaceContainer else scheme.errorContainer.copy(alpha = 0.55f), if (iOwe.isEmpty()) scheme.onSurface else scheme.onErrorContainer, Modifier.weight(1f))
                }
                if (perPerson.isNotEmpty()) {
                    androidx.compose.material3.Button(
                        onClick = { collecting = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                        Text(tr("Ask everyone to pay · QR, link"), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
    if (model.expenses.isEmpty() && drafts.isEmpty())
        item {
            EmptyState(
                Icons.Rounded.ReceiptLong,
                "A fresh start",
                "Add your first split. We'll work out who owes what.",
            )
        }
    items(model.expenses, key = { it.getString("id") }) { expense ->
        val status = statuses[expense.getString("id")]
        var sharing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        if (sharing) com.lambrk.splitease.ui.components.ShareExpenseDialog(model, expense) { sharing = false }
        val category = expense.optionalText("category").ifBlank { "" }
        Card(
            onClick = { onEdit(expense) },
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(46.dp),
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.ReceiptLong, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            expense.getString("description"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Text(
                            listOf(friendlyDate(expense.getString("expenseDate")), category.takeIf { it.isNotBlank() && it != "Other" && it != "General" })
                                .filterNotNull().joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        money(expense.getString("amount"), expense.getString("currency")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                val syncState = expense.optString("_syncState")
                if (syncState in listOf("PENDING", "FAILED", "AUTH_REQUIRED")) {
                    Text(
                        when (syncState) {
                            "FAILED" -> "Couldn't save online. Your split is still on this phone."
                            "AUTH_REQUIRED" -> "Sign in again to save online"
                            else -> "Waiting to save online"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (syncState == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    if (syncState == "FAILED")
                        TextButton(
                            onClick = { model.retryExpense(expense.getString("_operationId")) },
                            enabled = !model.busy,
                        ) { Text(tr("Try again")) }
                }
                if (status != null) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val payerUpi = model.selected?.optJSONArray("members")?.objects().orEmpty()
                        .firstOrNull { it.optString("userId") == status.payer }?.optionalText("upiId").orEmpty()
                    val good = status.tone == Tone.Good
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when (status.tone) {
                            Tone.Good -> MaterialTheme.colorScheme.primaryContainer
                            Tone.Pending -> MaterialTheme.colorScheme.tertiaryContainer
                            Tone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                if (good) Icons.Rounded.Check else Icons.Rounded.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(status.text, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    val progress = collection(expense)
                    if (progress != null && status.owedToMe.signum() > 0) {
                        LinearProgressIndicator(
                            progress = { progress.settled.toFloat() / progress.total },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }
                    if (!expense.optString("id").startsWith("local:")) {
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { sharing = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                            Text(tr("Share · QR, link, reminder"), Modifier.padding(start = 8.dp))
                        }
                    }
                    val expenseId = expense.getString("id")
                    val expenseClaims = model.claims.filter { it.optString("expenseId") == expenseId }
                    var iPaid by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    // The payer: someone says they paid. Check your bank or UPI app, then confirm.
                    if (expense.optString("paidBy") == model.userId) {
                        expenseClaims.forEach { claim ->
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "${claim.optString("userName").substringBefore(" (")} says they paid ${money(claim.optString("amount"), claim.optString("currency"))}",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(tr("Check your bank or UPI app to see if it arrived."), style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = { model.resolveClaim(claim.getString("id"), false) },
                                            enabled = !model.busy,
                                            modifier = Modifier.weight(1f),
                                        ) { Text(tr("Not received")) }
                                        androidx.compose.material3.Button(
                                            onClick = { model.resolveClaim(claim.getString("id"), true) },
                                            enabled = !model.busy,
                                            modifier = Modifier.weight(1f),
                                        ) { Text("Received") }
                                    }
                                }
                            }
                        }
                    }
                    val myClaim = expenseClaims.firstOrNull { it.optString("userId") == model.userId }
                    if (myClaim != null) {
                        Text(
                            "You said you paid ${money(myClaim.optString("amount"), myClaim.optString("currency"))} · waiting for ${memberName(status.payer, model).substringBefore(" (")} to confirm",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (status.iOwe.signum() > 0) {
                        androidx.compose.material3.OutlinedButton(onClick = { iPaid = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(tr("I've paid"))
                        }
                    }
                    if (iPaid) {
                        com.lambrk.splitease.ui.components.IPaidDialog(
                            payerName = memberName(status.payer, model).substringBefore(" ("),
                            left = status.iOwe,
                            currency = expense.optString("currency"),
                            busy = model.busy,
                            onSend = { amount ->
                                model.claimPayment(expenseId, amount)
                                iPaid = false
                            },
                            onDismiss = { iPaid = false },
                        )
                    }
                    if (status.iOwe.signum() > 0 && expense.optString("currency") == "INR" && payerUpi.isNotBlank()) {
                        androidx.compose.material3.Button(
                            onClick = {
                                val opened = com.lambrk.splitease.ui.util.openUpiPayment(
                                    context,
                                    payerUpi,
                                    memberName(status.payer, model).substringBefore(" ("),
                                    status.iOwe.setScale(2).toPlainString(),
                                    expense.getString("description"),
                                )
                                if (!opened) model.message = tr("No UPI app found on this phone.")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Pay ${money(status.iOwe.toPlainString(), "INR")} via UPI") }
                    }
                }
            }
        }
    }
}

/** The splits that add themselves (rent, maid, bills), with a way to pause or stop each one. */
@Composable
private fun RepeatingSplits(model: SplitEaseViewModel) {
    val groupId = model.selected?.optString("id")
    androidx.compose.runtime.LaunchedEffect(groupId) { model.loadRecurring() }
    val items = model.recurring.filter { it.optString("groupId") == groupId }
    if (items.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
    var stopping by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<JSONObject?>(null) }
    Surface(shape = RoundedCornerShape(22.dp), color = colors.surfaceContainer) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Repeat, contentDescription = null, tint = colors.primary)
                Column(Modifier.weight(1f)) {
                    Text("Repeating splits", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${items.count { it.optBoolean("active") }} active",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            }
            if (open) {
                items.forEach { r ->
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant)
                    val active = r.optBoolean("active")
                    val mine = r.optString("createdBy") == model.userId
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.optString("description"), fontWeight = FontWeight.Medium, maxLines = 1)
                            val every = when (r.optString("frequency")) { "WEEKLY" -> "Every week"; "YEARLY" -> "Every year"; else -> "Every month" }
                            Text(
                                if (active) "$every · next ${friendlyDate(r.optString("nextRunOn"))}" else "$every · paused",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        Text(money(r.optString("amount"), r.optString("currency")), fontWeight = FontWeight.Bold)
                        if (mine) {
                            androidx.compose.material3.Switch(
                                checked = active,
                                onCheckedChange = { model.setRecurringActive(r.optString("id"), it) },
                                enabled = !model.busy,
                            )
                            androidx.compose.material3.IconButton(onClick = { stopping = r }, enabled = !model.busy) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Stop repeating")
                            }
                        }
                    }
                }
            }
        }
    }
    stopping?.let { r ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { stopping = null },
            title = { Text("Stop repeating?") },
            text = { Text("Splits already added stay. No new ones will be added.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { model.deleteRecurring(r.optString("id")); stopping = null }) {
                    Text("Stop repeating", color = colors.error)
                }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { stopping = null }) { Text("Keep") } },
        )
    }
}
