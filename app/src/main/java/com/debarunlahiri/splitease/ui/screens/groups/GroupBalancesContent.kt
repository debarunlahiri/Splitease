package com.debarunlahiri.splitease.ui.screens.groups

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import com.debarunlahiri.splitease.ui.SplitEaseViewModel
import com.debarunlahiri.splitease.ui.components.EmptyState
import com.debarunlahiri.splitease.ui.components.Panel
import com.debarunlahiri.splitease.ui.components.SectionTitle
import com.debarunlahiri.splitease.ui.util.memberName
import com.debarunlahiri.splitease.ui.util.money
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
    item { SectionTitle("Who's up, who's down") }
    items(model.balances, key = { it.getString("userId") }) { balance ->
        Panel {
            Text(
                memberName(balance.getString("userId"), model),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                money(balance.getString("amount"), balance.getString("currency")),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "Positive gets back · negative owes",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (model.suggestions.isEmpty())
        item {
            EmptyState(
                Icons.Rounded.DoneAll,
                "All settled",
                "There are no suggested payments in this currency.",
            )
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
    if (model.settlements.isNotEmpty()) item { SectionTitle("Payment history") }
    items(model.settlements, key = { it.getString("id") }) { settlement ->
        Panel {
            Text(
                "${memberName(settlement.getString("payerId"), model)} paid ${memberName(settlement.getString("payeeId"), model)}"
            )
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
