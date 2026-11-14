@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lambrk.splitease.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PrivacyTip
import com.lambrk.splitease.ui.i18n.tr

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import com.lambrk.splitease.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lambrk.splitease.BuildConfig
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.components.Field
import com.lambrk.splitease.ui.components.Panel
import com.lambrk.splitease.ui.components.SectionTitle
import com.lambrk.splitease.ui.theme.ThemeMode

private const val SUPPORT_EMAIL = "support@lambrk.com"

/** True when SplitEase may show notifications (permission granted and not switched off in system settings). */
private fun notificationsAllowed(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** Opens this app's notification page in the system settings. */
private fun openNotificationSettings(context: Context): Boolean {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

@Composable
internal fun SettingsScreen(
    model: SplitEaseViewModel,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    // What the phone really allows. The saved "push" preference alone is not enough: the user can
    // refuse the permission, or switch notifications off later in the system settings.
    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
    var waitingForSettings by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = notificationsAllowed(context)
                // Came back from the system settings after being sent there to allow notifications.
                if (allowed && waitingForSettings) {
                    waitingForSettings = false
                    model.enablePush(true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            allowed = notificationsAllowed(context)
            if (granted && allowed) {
                model.enablePush(true)
            } else {
                model.message = tr("Allow notifications to get push alerts.")
                // After two refusals Android no longer shows the dialog, so point to the settings.
                val activity = context.findActivity()
                val blocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        (activity == null || !ActivityCompat.shouldShowRequestPermissionRationale(
                            activity,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ))
                if (blocked && openNotificationSettings(context)) waitingForSettings = true
            }
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
                        label = tr("Display name"),
                        enabled = canEditName,
                    )
                    Button(
                        onClick = { model.updateDisplayName(displayName) },
                        enabled = canEditName && displayName.trim().length >= 2 &&
                                displayName.trim() != currentName,
                    ) {
                        Text(tr("Save name"))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val chosenIso = model.user?.optString("country").orEmpty().ifBlank { null }
                    // Another phone may have set it already: keep this one in step.
                    androidx.compose.runtime.LaunchedEffect(chosenIso) {
                        if (chosenIso != null && com.lambrk.splitease.ui.util.chosenCountry(context)?.iso != chosenIso) {
                            com.lambrk.splitease.ui.util.saveChosenCountry(context, chosenIso)
                        }
                    }
                    var pickingCountry by rememberSaveable { mutableStateOf(false) }
                    val country = com.lambrk.splitease.ui.util.defaultCountry(context)
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { pickingCountry = true }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        com.lambrk.splitease.ui.screens.profile.Flag(country.iso, width = 32.dp)
                        Column(Modifier.weight(1f)) {
                            Text("Country", fontWeight = FontWeight.Medium)
                            Text(
                                "${country.name} · used for phone numbers and currency",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                    }
                    if (pickingCountry) {
                        com.lambrk.splitease.ui.screens.profile.CountryPicker(
                            onPick = { pickingCountry = false; model.saveCountry(it.iso) },
                            onDismiss = { pickingCountry = false },
                        )
                    }
                }
            }
            item { SectionTitle("Language") }
            item {
                Panel {
                    Column(Modifier.selectableGroup()) {
                        com.lambrk.splitease.ui.i18n.I18n.languages.forEach { language ->
                            val selected = com.lambrk.splitease.ui.i18n.I18n.lang == language.code
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { model.selectLanguage(language.code) },
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Text(
                                    language.nativeName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
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
                                Text(tr(mode.label), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            item { SectionTitle("Notifications") }
            item {
                Panel {
                    PreferenceSwitch(
                        title = tr("Activity inbox"),
                        description = tr("Show updates about your Splits and friends in Activity."),
                        checked = preferences?.optBoolean("inboxEnabled") ?: true,
                        enabled = canEditPreferences,
                        onCheckedChange = model::toggleInbox,
                    )
                    PreferenceSwitch(
                        title = tr("Push notifications"),
                        description = tr("Get invites and split updates on this device."),
                        // On only when it is saved AND the phone allows notifications.
                        checked = (preferences?.optBoolean("pushEnabled") ?: false) && allowed,
                        enabled = canEditPreferences,
                        onCheckedChange = { enabled ->
                            allowed = notificationsAllowed(context)
                            when {
                                !enabled -> model.enablePush(false)
                                allowed -> model.enablePush(true)
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        ) != PackageManager.PERMISSION_GRANTED ->
                                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)

                                else -> {
                                    // Permission is there but notifications are switched off in the system.
                                    model.message =
                                        tr("Turn on notifications for SplitEase, then come back.")
                                    if (openNotificationSettings(context)) waitingForSettings = true
                                }
                            }
                        },
                    )
                }
            }
            item { SectionTitle("Support") }
            item {
                Panel {
                    Text(
                        tr("Need help with SplitEase, or want to report a problem? Email us for support."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectionContainer {
                        Text(SUPPORT_EMAIL, style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(
                        onClick = {
                            val email = Intent(
                                Intent.ACTION_SENDTO,
                                Uri.fromParts("mailto", SUPPORT_EMAIL, null)
                            )
                            try {
                                context.startActivity(email)
                            } catch (_: ActivityNotFoundException) {
                                model.error =
                                    "No email app is installed. You can copy $SUPPORT_EMAIL from Settings."
                            }
                        },
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Email, contentDescription = null)
                            Text(tr("Contact support"))
                        }
                    }
                }
            }
            // Last action on the screen, directly above the About / version section.
            item {
                OutlinedButton(
                    onClick = { confirmingSignOut = true },
                    enabled = model.user != null && !model.busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                        Text(tr("Sign out"))
                    }
                }
            }
            item { SectionTitle("About") }
            item {
                var showPrivacy by rememberSaveable { mutableStateOf(false) }
                Panel {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { showPrivacy = true }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Rounded.PrivacyTip, contentDescription = null)
                        Text("Privacy policy", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                    }
                }
                if (showPrivacy) com.lambrk.splitease.ui.components.PrivacyPolicyDialog { showPrivacy = false }
            }
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
            title = { Text(tr("Sign out?")) },
            text = { Text(tr("Are you sure you want to sign out of SplitEase?")) },
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
                ) { Text(tr("Sign out")) }
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
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
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
