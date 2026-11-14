package com.lambrk.splitease.ui.screens.groups

import com.lambrk.splitease.ui.i18n.tr

import com.lambrk.splitease.ui.util.friendlyDate
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.lambrk.splitease.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.data.objects
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.components.EmptyState
import com.lambrk.splitease.ui.components.Panel
import com.lambrk.splitease.ui.components.SectionTitle
import com.lambrk.splitease.ui.components.ProfileAvatar
import com.lambrk.splitease.ui.util.memberName
import com.lambrk.splitease.ui.util.memberPhotoUrl
import com.lambrk.splitease.ui.util.money
import java.math.BigDecimal
import org.json.JSONObject

/**
 * The Balances tab, top to bottom:
 * 1. "Balances": one card listing every member, who is owed money and who owes it.
 * 2. "Pay back": the payments that would clear everything, with a button for your own.
 * 3. "Payment history": payments that were already recorded.
 */
internal fun LazyListScope.GroupBalancesContent(
    model: SplitEaseViewModel,
    onPay: (JSONObject) -> Unit,
) {
    val group = model.selected
    if (!model.groupDataAvailable || group == null) {
        item {
            EmptyState(
                Icons.Rounded.DoneAll,
                "Not available right now",
                "Connect to see who owes what in this Splits.",
            )
        }
        return
    }
    val currency = group.getString("defaultCurrency")

    // Every member appears, even with no balance yet, so the list is never empty.
    val amounts = LinkedHashMap<String, BigDecimal>()
    group.getJSONArray("members").objects().forEach { amounts[it.getString("userId")] = BigDecimal.ZERO }
    model.balances.forEach {
        amounts[it.getString("userId")] = it.getString("amount").toBigDecimalOrNull() ?: BigDecimal.ZERO
    }
    val allSettled = amounts.values.all { it.signum() == 0 }
    // Money owed to people first, then money people owe, then those who are settled.
    val ordered =
        amounts.entries.sortedWith(
            compareBy<Map.Entry<String, BigDecimal>> { balanceRank(it.value) }
                .thenByDescending { it.value.abs() }
        )

    if (allSettled) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.DoneAll, null, Modifier.size(28.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            tr("Everyone is paid up"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Nobody owes anything in $currency right now.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }

    item {
        Section(
            tr("Who owes what"),
            tr("Green means they get money back. Red means they need to pay."),
        )
    }
    item {
        Panel {
            ordered.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                BalanceRow(
                    name = personLabel(model, entry.key),
                    photoUrl = memberPhotoUrl(entry.key, model),
                    amount = entry.value,
                    currency = currency,
                )
            }
        }
    }

    if (model.suggestions.isNotEmpty()) {
        item {
            Section(
                tr("Pay back"),
                tr("These payments will square everyone up with the fewest payments."),
            )
        }
        items(model.suggestions) { suggestion ->
            val from = suggestion.getString("fromUserId")
            val to = suggestion.getString("toUserId")
            Panel {
                MoneyRow(
                    title = "${subject(model, from)} pays ${objectOf(model, to)}",
                    detail = null,
                    amount = money(suggestion.getString("amount"), suggestion.getString("currency")),
                    amountColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    PaymentAvatars(model, from, to)
                }
                // Only the person who owes can record that they paid.
                if (from == model.userId) {
                    FilledTonalButton(
                        enabled = !model.busy,
                        onClick = { onPay(suggestion) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(tr("I've paid this"))
                    }
                }
            }
        }
    }

    if (model.settlements.isNotEmpty()) {
        item { Section(tr("Payment history"), tr("Who has paid whom so far.")) }
        items(model.settlements, key = { it.getString("id") }) { settlement ->
            val payer = settlement.getString("payerId")
            val payee = settlement.getString("payeeId")
            Panel {
                MoneyRow(
                    title = "${subject(model, payer)} paid ${objectOf(model, payee)}",
                    detail = friendlyDate(settlement.getString("createdAt")),
                    amount = money(settlement.getString("amount"), settlement.getString("currency")),
                    amountColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    PaymentAvatars(model, payer, payee)
                }
            }
        }
    }
}

/** A section heading with a one-line explanation under it. */
@Composable
private fun Section(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionTitle(title)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BalanceRow(name: String, photoUrl: String?, amount: BigDecimal, currency: String) {
    val (label, color) =
        when {
            amount.signum() > 0 -> "gets back" to MaterialTheme.colorScheme.primary
            amount.signum() < 0 -> "owes" to MaterialTheme.colorScheme.error
            else -> "paid up" to MaterialTheme.colorScheme.onSurfaceVariant
        }
    MoneyRow(
        title = name,
        detail = label,
        amount = money(amount.abs().toPlainString(), currency),
        amountColor = color,
    ) {
        ProfileAvatar(photoUrl, size = 42.dp)
    }
}

@Composable
private fun PaymentAvatars(model: SplitEaseViewModel, from: String, to: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        ProfileAvatar(memberPhotoUrl(from, model), size = 24.dp)
        ProfileAvatar(memberPhotoUrl(to, model), size = 24.dp)
    }
}

@Composable
private fun MoneyRow(
    title: String,
    detail: String?,
    amount: String,
    amountColor: Color,
    avatars: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        avatars()
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            amount,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = amountColor,
        )
    }
}

private fun personLabel(model: SplitEaseViewModel, id: String) =
    memberName(id, model) + if (id == model.userId) " (Me)" else ""

/** "You" for the signed-in user at the start of a sentence, otherwise the member's name. */
private fun subject(model: SplitEaseViewModel, id: String) =
    if (id == model.userId) "You" else memberName(id, model)

/** "you" for the signed-in user inside a sentence, otherwise the member's name. */
private fun objectOf(model: SplitEaseViewModel, id: String) =
    if (id == model.userId) "you" else memberName(id, model)

/** Money owed to people first, then money people owe, then those who are settled. */
private fun balanceRank(amount: BigDecimal) = if (amount.signum() > 0) 0 else if (amount.signum() < 0) 1 else 2
