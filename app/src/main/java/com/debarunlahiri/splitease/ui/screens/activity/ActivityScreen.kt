package com.debarunlahiri.splitease.ui.screens.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.debarunlahiri.splitease.ui.SplitEaseViewModel
import com.debarunlahiri.splitease.ui.components.CardShape
import com.debarunlahiri.splitease.ui.components.EmptyState
import com.debarunlahiri.splitease.ui.components.Heading
import com.debarunlahiri.splitease.ui.components.RefreshableList

@Composable
internal fun ActivityScreen(model: SplitEaseViewModel) {
    RefreshableList(
        isRefreshing = model.refreshing,
        onRefresh = model::refresh,
        enabled = !model.busy,
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Heading(
                "Your activity",
                "The little updates, all together.",
            )
        }
        if (model.notifications.isEmpty() && !model.busy)
            item {
                EmptyState(
                    Icons.Rounded.NotificationsNone,
                    "You're all caught up",
                    "Updates from your groups will appear here.",
                )
            }
        items(model.notifications, key = { it.getString("id") }) { item ->
            Card(
                onClick = { if (!model.busy) model.openNotification(item) },
                shape = CardShape,
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (item.getBoolean("read")) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.secondaryContainer
                    ),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val groupName =
                        model.groups
                            .firstOrNull {
                                it.optString("id") == item.optString("groupId")
                            }
                            ?.optString("name")
                            ?.takeIf { it.isNotBlank() }
                    val title = item.getString("title")
                    if (groupName != null && !title.startsWith(groupName + " · ")) {
                        Text(
                            groupName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(item.getString("message"))
                    Text(
                        item.getString("createdAt").take(10) +
                            if (item.getBoolean("read")) " · Read" else " · Tap to view",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
