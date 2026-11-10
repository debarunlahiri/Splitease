package com.lambrk.splitease.ui.screens.profile

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.components.Panel
import com.lambrk.splitease.ui.components.ProfileAvatar
import com.lambrk.splitease.ui.util.optionalText

@Composable
internal fun ProfileScreen(
    model: SplitEaseViewModel,
    onSettings: () -> Unit,
    onPlan: () -> Unit,
) {
    val user = model.user ?: return
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text(
                "You",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = colors.surfaceContainerLow,
                border = BorderStroke(1.dp, colors.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileAvatar(user.optionalText("photoUrl"), size = 56.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            user.getString("displayName"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        SelectionContainer {
                            Text(
                                user.getString("email"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (model.needsReconnect) {
            item {
                Panel {
                    Text("Reconnect your account", style = MaterialTheme.typography.titleMedium)
                    Text("Reconnect Google to get updates and sync expenses.")
                    Button(
                        enabled = !model.busy,
                        onClick = { (context as? Activity)?.let(model::login) },
                    ) { Text("Reconnect Google account") }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, colors.outlineVariant),
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Settings") },
                        supportingContent = { Text("Profile, notifications, and support") },
                        leadingContent = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable(role = Role.Button, onClick = onSettings),
                    )
                    HorizontalDivider(color = colors.outlineVariant)
                    val plan = model.entitlement?.optionalText("planCode")?.let(::planName)
                    ListItem(
                        headlineContent = { Text("Plan") },
                        supportingContent = { Text(plan ?: "View plan details") },
                        leadingContent = { Icon(Icons.Rounded.CreditCard, contentDescription = null) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable(role = Role.Button, onClick = onPlan),
                    )
                }
            }
        }
    }
}
