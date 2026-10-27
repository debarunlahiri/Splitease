package com.debarunlahiri.splitease.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
internal fun SplitEaseBottomBar(
    selected: AppDestination,
    unreadCount: Int = 0,
    onSelect: (AppDestination) -> Unit,
) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            listOf(AppDestination.Groups, AppDestination.Activity, AppDestination.Profile).forEach {
                destination ->
                val label =
                    when (destination) {
                        AppDestination.Groups -> "Groups"
                        AppDestination.Activity -> "Activity"
                        else -> "You"
                    }
                val icon =
                    when (destination) {
                        AppDestination.Groups -> Icons.Rounded.Groups
                        AppDestination.Activity -> Icons.Rounded.Notifications
                        else -> Icons.Rounded.Person
                    }
                NavigationBarItem(
                    selected = selected == destination,
                    onClick = { onSelect(destination) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (destination == AppDestination.Activity && unreadCount > 0)
                                    Badge { Text(unreadCount.coerceAtMost(99).toString()) }
                            }
                        ) {
                            Icon(icon, label)
                        }
                    },
                    label = { Text(label) },
                )
            }
        }
    }
}
