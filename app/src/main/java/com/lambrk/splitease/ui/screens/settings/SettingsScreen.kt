@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lambrk.splitease.ui.screens.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lambrk.splitease.BuildConfig
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.components.Field
import com.lambrk.splitease.ui.components.Panel
import com.lambrk.splitease.ui.components.SectionTitle
import com.lambrk.splitease.ui.theme.ThemeMode

private const val SUPPORT_EMAIL = "support@lambrk.com"

@Composable
internal fun SettingsScreen(
    model: SplitEaseViewModel,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) model.enablePush(true)
        }
    val preferences = model.preferences
    val canEditPreferences = preferences != null && !model.busy && !model.needsReconnect
    val currentName = model.user?.optString("displayName").orEmpty()
    var displayName by rememberSaveable(currentName) { mutableStateOf(currentName) }
    val canEditName = model.user != null && !model.busy && !model.needsReconnect
    var confirmingSignOut by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to profile")
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SectionTitle("Profile") }
            item {
                Panel {
                    Field(
                        value = displayName,
                        change = { if (it.length <= 100) displayName = it },
                        label = "Display name",
                        enabled = canEditName,
                    )
                    Button(
                        onClick = { model.updateDisplayName(displayName) },
                        enabled = canEditName && displayName.trim().length >= 2 &&
                            displayName.trim() != currentName,
                    ) {
                        Text("Save name")
                    }
                    TextButton(
                        onClick = { confirmingSignOut = true },
                        enabled = model.user != null && !model.busy,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                            Text("Sign out")
                        }
                    }
                }
            }
            item { SectionTitle("Appearance") }
            item {
                Panel {
                    Column(Modifier.selectableGroup()) {
                        ThemeMode.entries.forEach { mode ->
                            val selected = themeMode == mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { onThemeChange(mode) },
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            item { SectionTitle("Notifications") }
            item {
                Panel {
                    PreferenceSwitch(
                        title = "Activity inbox",
                        description = "Show group and friend updates in Activity.",
                        checked = preferences?.optBoolean("inboxEnabled") ?: true,
                        enabled = canEditPreferences,
                        onCheckedChange = model::toggleInbox,
                    )
                    PreferenceSwitch(
                        title = "Push notifications",
                        description = "Get invitations and expense updates on this device.",
                        checked = preferences?.optBoolean("pushEnabled") ?: false,
                        enabled = canEditPreferences,
                        onCheckedChange = { enabled ->
                            if (
                                enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                model.enablePush(enabled)
                            }
                        },
                    )
                }
            }
            item { SectionTitle("Support") }
            item {
                Panel {
                    Text(
                        "Need help with SplitEase, or want to report a problem? Email us for support.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectionContainer {
                        Text(SUPPORT_EMAIL, style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(
                        onClick = {
                            val email = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", SUPPORT_EMAIL, null))
                            try {
                                context.startActivity(email)
                            } catch (_: ActivityNotFoundException) {
                                model.error = "No email app is installed. You can copy $SUPPORT_EMAIL from Settings."
                            }
                        },
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Email, contentDescription = null)
                            Text("Contact support")
                        }
                    }
                }
            }
            item { SectionTitle("About") }
            item {
                Panel {
                    Text("SplitEase", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
    if (confirmingSignOut) {
        AlertDialog(
            onDismissRequest = { confirmingSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Are you sure you want to sign out of SplitEase?") },
            confirmButton = {
                TextButton(
                    enabled = !model.busy,
                    onClick = {
                        confirmingSignOut = false
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSignOut = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PreferenceSwitch(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
        )
    }
}
