package com.lambrk.splitease.ui.screens.groups

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
import androidx.compose.material3.Text
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
import com.lambrk.splitease.ui.util.optionalText

@Composable
internal fun GroupsScreen(model: SplitEaseViewModel) {
    val colors = MaterialTheme.colorScheme
    val pending = model.invitations.filter { it.optString("status") == "PENDING" }
    val groupCount = model.groups.size

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
                        "Groups",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (groupCount > 0) {
                        Text(
                            if (groupCount == 1) "1 group" else "$groupCount groups",
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
                                invitation.optionalText("groupName").ifBlank { "Group invitation" },
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
        item(key = "groups-list-heading") {
            SectionTitle("Your groups", modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
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
                    Text("No groups yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Create a group to start sharing expenses.",
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
                supportingContent = { Text("$members · $currency") },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                },
            )
            HorizontalDivider(color = colors.outlineVariant)
        }
    }
}
