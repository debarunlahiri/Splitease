@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.debarunlahiri.splitease.ui.screens.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.debarunlahiri.splitease.ui.SplitEaseViewModel
import com.debarunlahiri.splitease.ui.components.CardShape
import com.debarunlahiri.splitease.ui.components.RefreshableList
import com.debarunlahiri.splitease.ui.util.money
import java.math.BigDecimal
import org.json.JSONObject

@Composable
internal fun GroupDetailScreen(
    model: SplitEaseViewModel,
    onInvite: () -> Unit,
    onEdit: (JSONObject) -> Unit,
    onPay: (JSONObject) -> Unit,
) {
    val group = model.selected ?: return
    var section by remember(group.getString("id")) { mutableStateOf(0) }
    RefreshableList(
        isRefreshing = model.refreshing,
        onRefresh = model::refresh,
        enabled = !model.busy,
        contentPadding = PaddingValues(24.dp, 16.dp, 24.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = model::closeGroup) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to groups")
                }
                Text(
                    group.getString("name"),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        item {
            val amount =
                model.balances.find { it.optString("userId") == model.userId }?.optString("amount")
                    ?: "0"
            val value = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CardShape) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        if (value.signum() > 0) "YOU GET BACK"
                        else if (value.signum() < 0) "YOU OWE" else "YOUR BALANCE",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        if (model.groupDataAvailable)
                            money(value.abs().toPlainString(), group.getString("defaultCurrency"))
                        else "Not available",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (!model.groupDataAvailable)
                            "Connect to see your balance. Pending expenses aren't included yet."
                        else if (value.signum() == 0) "All square. That's a good feeling."
                        else "A little clarity for your shared spending."
                    )
                    if (model.showingSavedData || model.pendingExpenseCount > 0) {
                        Text(
                            "Last synced balance. Pending expenses aren't included.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "${group.getJSONArray("members").length()} members · ${group.getString("defaultCurrency")}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        item {
            PrimaryTabRow(selectedTabIndex = section) {
                listOf("Expenses", "Balances", "People").forEachIndexed { index, label ->
                    Tab(
                        selected = section == index,
                        onClick = { section = index },
                        text = { Text(label) },
                    )
                }
            }
        }
        when (section) {
            0 -> GroupExpensesContent(model, onEdit)
            1 -> GroupBalancesContent(model, onPay)
            else -> GroupPeopleContent(model, group, onInvite)
        }
    }
}
