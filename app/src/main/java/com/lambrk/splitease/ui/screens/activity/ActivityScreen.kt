package com.lambrk.splitease.ui.screens.activity

import com.lambrk.splitease.ui.i18n.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import com.lambrk.splitease.ui.components.RefreshableList
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.json.JSONObject

@Composable
internal fun ActivityScreen(model: SplitEaseViewModel) {
    val notifications = model.notifications
    val unread = notifications.count { !it.optBoolean("read") }
    val groups = model.groups.associate { it.getString("id") to it.getString("name") }
    val today = LocalDate.now()
    val notificationsByDay = notifications.groupBy { notificationDay(it.optString("createdAt")) }

    RefreshableList(
        isRefreshing = model.refreshing,
        onRefresh = model::refresh,
        enabled = !model.busy,
        contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item(key = "activity-header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Activity",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (unread > 0) {
                        Text(
                            "$unread unread",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (unread > 0) {
                    TextButton(onClick = model::markAllNotificationsRead, enabled = !model.busy) {
                        Text(tr("Mark all as read"))
                    }
                }
            }
        }
        if (notifications.isEmpty() && !model.busy) {
            item(key = "activity-empty") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Rounded.NotificationsNone,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(tr("No activity yet"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("Updates about your Splits and friends will appear here."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        notificationsByDay.forEach { (date, entries) ->
            item(key = "activity-day-$date") {
                val heading = when (date) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    null -> "Earlier"
                    else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                }
                Text(
                    heading,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(entries, key = { it.getString("id") }) { notification ->
                ActivityRow(
                    notification = notification,
                    groupName = groups[notification.optString("groupId")],
                    enabled = !model.busy,
                    onClick = { model.openNotification(notification) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun ActivityRow(
    notification: JSONObject,
    groupName: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val unread = !notification.optBoolean("read")
    val title = notification.getString("title")
    val icon = when (notification.optString("type")) {
        "GROUP_INVITATION", "GROUP_MEMBER_ADDED" -> Icons.Rounded.Groups
        "FRIEND_ADDED" -> Icons.Rounded.PersonAdd
        "SETTLEMENT_RECORDED" -> Icons.Rounded.Payments
        "EXPENSE_CREATED", "EXPENSE_UPDATED", "EXPENSE_DELETED" -> Icons.Rounded.ReceiptLong
        else -> Icons.Rounded.NotificationsNone
    }

    ListItem(
        modifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (unread) colors.surfaceContainerLow else colors.surface,
        ),
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = if (unread) colors.primaryContainer else colors.surfaceContainerHigh,
                contentColor = if (unread) colors.onPrimaryContainer else colors.onSurfaceVariant,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp).padding(10.dp))
            }
        },
        headlineContent = {
            Text(title, fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!groupName.isNullOrBlank() && !title.startsWith("$groupName · ")) {
                    Text(groupName, style = MaterialTheme.typography.labelMedium, color = colors.primary)
                }
                Text(notification.getString("message"), style = MaterialTheme.typography.bodyMedium)
                if (unread) {
                    Text(
                        "Unread",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                    )
                }
            }
        },
    )
}

private fun notificationDay(timestamp: String): LocalDate? =
    runCatching {
        OffsetDateTime.parse(timestamp).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
    }.getOrNull() ?: runCatching { LocalDate.parse(timestamp.take(10)) }.getOrNull()
