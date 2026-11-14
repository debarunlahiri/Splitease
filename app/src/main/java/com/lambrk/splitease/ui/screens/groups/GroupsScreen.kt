package com.lambrk.splitease.ui.screens.groups

import com.lambrk.splitease.ui.i18n.tr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.lambrk.splitease.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.components.CurrencyBadge
import com.lambrk.splitease.ui.components.ProfileAvatar
import com.lambrk.splitease.ui.components.RefreshableList
import com.lambrk.splitease.ui.components.SectionTitle
import com.lambrk.splitease.ui.util.money
import com.lambrk.splitease.ui.util.optionalText
import com.lambrk.splitease.data.objects
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun GroupsScreen(model: SplitEaseViewModel) {
    val colors = MaterialTheme.colorScheme
    val pending = model.invitations.filter { it.optString("status") == "PENDING" }
    val groupCount = model.groups.size
    val progress = model.progress
    LaunchedEffect(model.groups.size, model.expenses.size) { model.refreshProgress() }
    fun totals(array: org.json.JSONArray?) = array?.objects().orEmpty().map { it.getString("currency") to it.getString("amount") }
    val perGroup = progress?.optJSONArray("groups")?.objects().orEmpty().associateBy { it.getString("groupId") }
    val overallSettled = perGroup.values.sumOf { it.optInt("settled") }
    val overallTotal = perGroup.values.sumOf { it.optInt("total") }

    RefreshableList(
        isRefreshing = model.refreshing,
        onRefresh = model::refresh,
        enabled = !model.busy,
        contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item(key = "groups-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Splits",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (groupCount > 0) {
                        Text(
                            "$groupCount in total",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                ProfileAvatar(model.user?.optionalText("photoUrl"), size = 40.dp)
            }
        }
        if (pending.isNotEmpty()) {
            item(key = "groups-invitations-heading") {
                SectionTitle("Invitations", modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
            }
            items(pending, key = { "invite-${it.getString("id")}" }) { invitation ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surfaceContainerLow,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.MailOutline, contentDescription = null, tint = colors.primary)
                            Text(
                                invitation.optionalText("groupName").ifBlank { "Invitation to Splits" },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            TextButton(
                                enabled = !model.busy,
                                onClick = { model.invitationAction(invitation, false) },
                            ) { Text("Decline") }
                            FilledTonalButton(
                                enabled = !model.busy,
                                onClick = { model.invitationAction(invitation, true) },
                            ) { Text("Accept") }
                        }
                    }
                }
            }
        }
        if (progress != null && overallTotal > 0) {
            item(key = "groups-progress") {
                val toCollect = totals(progress.optJSONArray("toCollect"))
                val toPay = totals(progress.optJSONArray("toPay"))
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = colors.surfaceContainer,
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(tr("Still to receive"), style = MaterialTheme.typography.bodySmall)
                                if (toCollect.isEmpty()) Text("Nothing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                toCollect.forEach { (c, a) -> Text(money(a, c), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(tr("You still owe"), style = MaterialTheme.typography.bodySmall)
                                if (toPay.isEmpty()) Text("Nothing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                toPay.forEach { (c, a) -> Text(money(a, c), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                            }
                        }
                        LinearProgressIndicator(
                            progress = { overallSettled.toFloat() / overallTotal },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "$overallSettled of $overallTotal payments paid",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item(key = "groups-list-heading") {
            SectionTitle(tr("Your Splits"), modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        }
        if (model.groups.isEmpty() && !model.busy) {
            item(key = "groups-empty") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Rounded.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = colors.onSurfaceVariant,
                    )
                    Text(tr("No Splits yet"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("Create Splits to start sharing costs with friends."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
        items(model.groups, key = { it.getString("id") }) { group ->
            val currency = group.getString("defaultCurrency")
            val memberCount = group.getJSONArray("members").length()
            val members = if (memberCount == 1) "1 member" else "$memberCount members"
            ListItem(
                modifier = Modifier.clickable(
                    enabled = !model.busy,
                    role = Role.Button,
                    onClick = { model.openGroup(group) },
                ),
                leadingContent = { CurrencyBadge(currency) },
                headlineContent = {
                    Text(group.getString("name"), fontWeight = FontWeight.SemiBold)
                },
                supportingContent = {
                    val p = perGroup[group.getString("id")]
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("$members · $currency")
                        if (p != null && p.optInt("total") > 0) {
                            LinearProgressIndicator(
                                progress = { p.optInt("settled").toFloat() / p.optInt("total") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            val left = totals(p.optJSONArray("pending"))
                            Text(
                                "${p.optInt("settled")}/${p.optInt("total")} paid" +
                                    if (left.isEmpty()) " · All paid" else " · ${left.joinToString(" + ") { money(it.second, it.first) }} pending",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                },
            )
            HorizontalDivider(color = colors.outlineVariant)
        }
    }
}
