package com.debarunlahiri.splitease.ui.screens.groups

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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.debarunlahiri.splitease.ui.SplitEaseViewModel
import com.debarunlahiri.splitease.ui.components.EmptyState
import com.debarunlahiri.splitease.ui.components.Panel
import com.debarunlahiri.splitease.ui.components.SectionTitle
import com.debarunlahiri.splitease.ui.util.memberName
import com.debarunlahiri.splitease.ui.util.money
import java.math.BigDecimal
import org.json.JSONObject

internal fun LazyListScope.GroupBalancesContent(
    model: SplitEaseViewModel,
    onPay: (JSONObject) -> Unit,
) {
    if (!model.groupDataAvailable) {
        item {
            EmptyState(
                Icons.Rounded.DoneAll,
                "Balance unavailable",
                "Connect to load your group's balances.",
            )
        }
        return
    }
    item { SectionTitle("Member balances") }
    items(model.balances, key = { it.getString("userId") }) { balance ->
        val amount = balance.getString("amount").toBigDecimalOrNull() ?: BigDecimal.ZERO
        val isCurrentUser = balance.getString("userId") == model.userId
        Panel {
            Text(
                if (isCurrentUser) "You" else memberName(balance.getString("userId"), model),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                money(amount.abs().toPlainString(), balance.getString("currency")),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                when {
                    amount.signum() > 0 -> if (isCurrentUser) "You get back" else "Gets back"
                    amount.signum() < 0 -> if (isCurrentUser) "You owe" else "Owes"
                    else -> "Settled up"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    item { BalanceSectionHeader("Suggested payments") }
    if (model.suggestions.isEmpty()) {
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
                    Icon(
                        Icons.Rounded.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "All settled",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "No payments needed in this currency.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
    items(model.suggestions) { suggestion ->
        Panel {
            Text(
                "${memberName(suggestion.getString("fromUserId"), model)} pays ${memberName(suggestion.getString("toUserId"), model)}"
            )
            Text(
                money(suggestion.getString("amount"), suggestion.getString("currency")),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (suggestion.getString("fromUserId") == model.userId)
                Button(enabled = !model.busy, onClick = { onPay(suggestion) }) {
                    Text("Record payment")
                }
        }
    }
    if (model.settlements.isNotEmpty()) {
        item { BalanceSectionHeader("Payment history") }
    }
    items(model.settlements, key = { it.getString("id") }) { settlement ->
        val payerId = settlement.getString("payerId")
        val payeeId = settlement.getString("payeeId")
        val payerName = if (payerId == model.userId) "You" else memberName(payerId, model)
        val payeeName = if (payeeId == model.userId) "you" else memberName(payeeId, model)
        Panel {
            Text("$payerName paid $payeeName")
            Text(
                money(settlement.getString("amount"), settlement.getString("currency")),
                fontWeight = FontWeight.Bold,
            )
            Text(
                settlement.getString("createdAt").take(10),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BalanceSectionHeader(title: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SectionTitle(title)
    }
}
