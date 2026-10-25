package com.debarunlahiri.splitease.ui.screens.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.debarunlahiri.splitease.ui.SplitEaseViewModel
import com.debarunlahiri.splitease.ui.components.CardShape
import com.debarunlahiri.splitease.ui.components.EmptyState
import com.debarunlahiri.splitease.ui.components.Heading
import com.debarunlahiri.splitease.ui.components.Panel
import com.debarunlahiri.splitease.ui.components.RefreshableList
import com.debarunlahiri.splitease.ui.components.SectionTitle
import com.debarunlahiri.splitease.ui.components.Symbol
import com.debarunlahiri.splitease.ui.util.shortId

@Composable
internal fun GroupsScreen(model: SplitEaseViewModel) {
    RefreshableList(
        isRefreshing = model.refreshing,
        onRefresh = model::refresh,
        enabled = !model.busy,
        contentPadding = PaddingValues(24.dp, 20.dp, 24.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Heading(
                "Hey, ${model.user?.optString("displayName")?.substringBefore(' ')}",
                "Make room for good times.",
            )
        }
        item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CardShape) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Rounded.Spa, null, Modifier.size(32.dp))
                    Text(
                        "Less maths.\nMore memories.",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Your shared expenses, all in one happy place.")
                    Text(
                        "${model.groups.size} groups together",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        val pending = model.invitations.filter { it.optString("status") == "PENDING" }
        if (pending.isNotEmpty()) {
            item { SectionTitle("You're invited") }
            items(pending, key = { "invite-${it.getString("id")}" }) { invitation ->
                Panel {
                    Text("Group invitation", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Group ${shortId(invitation.getString("groupId"))}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row {
                        TextButton(
                            enabled = !model.busy,
                            onClick = { model.invitationAction(invitation, true) },
                        ) {
                            Text("Accept")
                        }
                        TextButton(
                            enabled = !model.busy,
                            onClick = { model.invitationAction(invitation, false) },
                        ) {
                            Text("Decline")
                        }
                    }
                }
            }
        }
        item { SectionTitle("Your circles") }
        if (model.groups.isEmpty() && !model.busy)
            item {
                EmptyState(
                    Icons.Rounded.Groups,
                    "Everything starts with a group",
                    "Create a circle for your flatmates, travel crew or everyday plans.",
                )
            }
        items(model.groups, key = { it.getString("id") }) { group ->
            Card(
                onClick = { if (!model.busy) model.openGroup(group) },
                shape = CardShape,
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Symbol(Icons.Rounded.Groups, Modifier.size(56.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            group.getString("name"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${group.getJSONArray("members").length()} members · ${group.getString("defaultCurrency")}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, "Open group")
                }
            }
        }
    }
}
