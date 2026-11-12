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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
        item { UpiCard(model, user) }
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

@Composable
private fun UpiCard(model: SplitEaseViewModel, user: org.json.JSONObject) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val saved = user.optionalText("upiId")
    var upi by androidx.compose.runtime.saveable.rememberSaveable(saved) { androidx.compose.runtime.mutableStateOf(saved) }
    var touched by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var confirming by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val check = com.lambrk.splitease.ui.util.checkUpiId(upi)
    val verified = saved.isNotBlank() && user.optBoolean("upiVerified")
    val changed = upi.trim().lowercase() != saved
    val showError = touched && upi.isNotBlank() && !check.valid
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("UPI ID", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (saved.isNotBlank() && !changed) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (verified) colors.primaryContainer else colors.tertiaryContainer,
                ) {
                    Text(
                        if (verified) "✓ Verified" else "Not verified",
                        Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Text(
            "Friends can pay you back in INR straight from SplitEase.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        androidx.compose.material3.OutlinedTextField(
            value = upi,
            onValueChange = { upi = it.trim(); touched = true },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("name@bank") },
            isError = showError,
            supportingText = {
                when {
                    showError -> Text(check.reason.orEmpty())
                    upi.isNotBlank() && check.valid && check.unknownHandle ->
                        Text("We don't recognise that bank handle. Double-check it, or verify it below.")
                    upi.isNotBlank() && check.valid -> Text("Looks good")
                    else -> Text("For example name@oksbi, 9876543210@ybl or name@paytm")
                }
            },
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
            ),
        )
        Button(
            enabled = !model.busy && changed && (upi.isBlank() || check.valid),
            onClick = { model.saveUpiId(upi) },
        ) { Text(if (upi.isBlank()) "Remove UPI ID" else "Save UPI ID") }
        if (saved.isNotBlank() && !changed && !verified) {
            Text(
                "Verify it: your UPI app shows the name registered to this ID. If it's your name, the ID is right.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    val opened = com.lambrk.splitease.ui.util.openUpiVerification(context, saved, user.optionalText("displayName"))
                    if (opened) confirming = true else model.message = "No UPI app found on this phone."
                },
                enabled = !model.busy,
            ) { Text("Check name in my UPI app") }
        }
    }
    if (confirming) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Did your UPI app show your name?") },
            text = {
                Text("Don't send any money. If the name shown for $saved is yours, tap Yes. If it showed someone else or an error, fix the UPI ID.")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirming = false; model.confirmUpiId() }, enabled = !model.busy) {
                    Text("Yes, that's me")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirming = false }) { Text("No, not yet") }
            },
        )
    }
}
