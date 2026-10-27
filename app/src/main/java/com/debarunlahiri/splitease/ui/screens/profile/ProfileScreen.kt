package com.debarunlahiri.splitease.ui.screens.profile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.debarunlahiri.splitease.ui.SplitEaseViewModel
import com.debarunlahiri.splitease.ui.components.Heading
import com.debarunlahiri.splitease.ui.components.Panel
import com.debarunlahiri.splitease.ui.components.ProfileAvatar
import com.debarunlahiri.splitease.ui.util.optionalText

@Composable
internal fun ProfileScreen(model: SplitEaseViewModel, logout: () -> Unit) {
    val user = model.user ?: return
    val context = LocalContext.current
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) model.enablePush(true)
        }
    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Heading(
                title = "Your space",
                subtitle = "A little about you.",
                icon = Icons.Rounded.AccountCircle,
                label = "MAKE IT YOURS",
            )
        }
        if (model.needsReconnect)
            item {
                Panel {
                    Text("Your saved data is still available.")
                    Text("Reconnect your Google account to get updates and sync expenses.")
                    androidx.compose.material3.Button(
                        enabled = !model.busy,
                        onClick = {
                            (context as? android.app.Activity)?.let { model.login(it) }
                        },
                    ) {
                        Text("Reconnect Google account")
                    }
                }
            }
        item {
            Panel {
                ProfileAvatar(user.optionalText("photoUrl"))
                Text(
                    user.getString("displayName"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(user.getString("email"))
            }
        }
        item {
            Panel {
                Text("Get invited by email", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Friends can invite you using the email address you sign in with.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(user.getString("email"), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Group updates", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Show updates in your activity inbox",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = model.preferences?.optBoolean("inboxEnabled") ?: true,
                        enabled = !model.busy && model.preferences != null,
                        onCheckedChange = model::toggleInbox,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Push notifications", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Invitations and shared expense updates",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = model.preferences?.optBoolean("pushEnabled") ?: false,
                        enabled = !model.busy,
                        onCheckedChange = { enabled ->
                            if (
                                enabled &&
                                    Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else model.enablePush(enabled)
                        },
                    )
                }
                model.entitlement?.let { entitlement ->
                    Text(
                        "Your plan: ${entitlement.getString("planCode")}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (!entitlement.isNull("expiresAt"))
                        Text(
                            "Valid until ${entitlement.getString("expiresAt").take(10)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                }
            }
        }
        item {
            OutlinedButton(
                onClick = logout,
                enabled = !model.busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, null)
                Spacer(Modifier.width(10.dp))
                Text("Sign out")
            }
        }
        item {
            Text(
                "SplitEase · Made for sharing",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
