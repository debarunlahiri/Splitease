package com.lambrk.splitease.ui.screens.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.components.ProfileAvatar
import com.lambrk.splitease.ui.i18n.Text
import com.lambrk.splitease.ui.i18n.tr
import com.lambrk.splitease.ui.util.friendlyDate
import com.lambrk.splitease.ui.util.money
import com.lambrk.splitease.data.objects
import com.lambrk.splitease.ui.util.optionalText
import org.json.JSONObject
import kotlin.math.abs

/** "Owes you ₹500", "You owe ₹200" or "All settled" for a list of {currency, amount} (positive means they owe you). */
@Composable
private fun balanceText(balance: List<JSONObject>, name: String): Pair<String, Boolean?> {
    if (balance.isEmpty()) return tr("All settled") to null
    val parts = balance.joinToString(", ") { money(abs(it.getDouble("amount")).toString(), it.getString("currency")) }
    val theyOwe = balance.first().getDouble("amount") > 0
    val mixed = balance.map { it.getDouble("amount") > 0 }.distinct().size > 1
    return when {
        mixed -> parts to null
        theyOwe -> "$name owes you $parts" to true
        else -> "You owe $name $parts" to false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FriendDetailSheet(model: SplitEaseViewModel) {
    val colors = MaterialTheme.colorScheme
    val detail = model.friendDetail
    ModalBottomSheet(
        onDismissRequest = model::closeFriend,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        when {
            model.friendDetailLoading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(24.dp))
            detail == null -> Column(Modifier.padding(24.dp).navigationBarsPadding()) {
                Text(tr("Couldn’t load this friend."), color = colors.onSurfaceVariant)
                TextButton(onClick = model::closeFriend) { Text("Close") }
            }
            else -> {
                val friend = detail.getJSONObject("friend")
                val name = friend.getString("displayName")
                val first = name.trim().substringBefore(" ").ifBlank { name }
                val groups = detail.getJSONArray("groups").objects()
                val expenses = detail.getJSONArray("expenses").objects()
                val (summary, theyOwe) = balanceText(detail.getJSONArray("balance").objects(), first)
                LazyColumn(
                    modifier = Modifier.navigationBarsPadding(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ProfileAvatar(friend.optionalText("photoUrl"), size = 56.dp)
                            Column {
                                Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    summary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = when (theyOwe) {
                                        true -> colors.primary
                                        false -> colors.error
                                        null -> colors.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            tr("Splits together"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
                        )
                    }
                    if (groups.isEmpty()) {
                        item { Text(tr("You are not in any Split together yet."), color = colors.onSurfaceVariant) }
                    }
                    items(groups, key = { it.getString("id") }) { group ->
                        val (line, _) = balanceText(group.getJSONArray("balance").objects(), first)
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Text(group.getString("name"), fontWeight = FontWeight.Medium)
                            Text(line, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        HorizontalDivider(color = colors.outlineVariant)
                    }
                    item {
                        Text(
                            tr("Splits between you"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
                        )
                    }
                    if (expenses.isEmpty()) {
                        item { Text(tr("No splits between you yet."), color = colors.onSurfaceVariant) }
                    }
                    items(expenses, key = { it.getString("id") }) { split ->
                        val currency = split.getString("currency")
                        val outstanding = split.getDouble("outstanding")
                        val who = when (split.getString("paidBy")) {
                            "YOU" -> "You paid"
                            "FRIEND" -> "$first paid"
                            else -> "${split.getString("paidByName").substringBefore(" ")} paid"
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(split.getString("description"), fontWeight = FontWeight.Medium)
                                Text(
                                    "${split.getString("groupName")} · ${friendlyDate(split.getString("date"))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                                Text(
                                    "$who ${money(split.getDouble("amount").toString(), currency)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (outstanding == 0.0) {
                                    Text(tr("Settled"), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                } else {
                                    Text(
                                        money(abs(outstanding).toString(), currency),
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (outstanding > 0) colors.primary else colors.error,
                                    )
                                    Text(
                                        if (outstanding > 0) "owes you" else "you owe",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = colors.outlineVariant)
                    }
                    val more = detail.optInt("expenseCount") - expenses.size
                    if (more > 0) item { Text("and $more more", color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)) }
                }
            }
        }
    }
}
