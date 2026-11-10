package com.lambrk.splitease.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.lambrk.splitease.update.AppUpdateViewModel
import com.lambrk.splitease.update.AppUpdateViewModel.Phase

/** Offers a new version of the app. A required update cannot be dismissed. */
@Composable
internal fun UpdateDialog(updates: AppUpdateViewModel) {
    val update = updates.available ?: return
    val phase = updates.phase
    val name = update.versionName.ifBlank { update.versionCode.toString() }
    AlertDialog(
        onDismissRequest = updates::dismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = !update.required,
                dismissOnClickOutside = !update.required,
            ),
        title = { Text(if (update.required) "Update required" else "Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "SplitEase $name is ready." +
                        if (update.required) " Please update to keep using SplitEase." else ""
                )
                if (update.notes.isNotBlank()) {
                    Text(
                        "What's new\n${update.notes}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                when (phase) {
                    Phase.Downloading -> {
                        if (updates.progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { updates.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        Text("Downloading…", style = MaterialTheme.typography.bodySmall)
                    }
                    Phase.NeedsPermission ->
                        Text(
                            "Android needs your permission to install updates from SplitEase. " +
                                "Tap Allow installs, switch on Allow from this source, then come back.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    Phase.Ready ->
                        Text(
                            "Finish installing in the window Android opened. If it closed, tap Install.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    Phase.Failed ->
                        Text(
                            updates.failure ?: "Something went wrong. Please try again.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    Phase.Idle -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = phase != Phase.Downloading,
                onClick = {
                    if (phase == Phase.NeedsPermission) updates.openInstallSettings()
                    else updates.startUpdate()
                },
            ) {
                Text(
                    when (phase) {
                        Phase.Downloading -> "Downloading…"
                        Phase.NeedsPermission -> "Allow installs"
                        Phase.Ready -> "Install"
                        Phase.Failed -> "Try again"
                        Phase.Idle -> "Update now"
                    }
                )
            }
        },
        dismissButton =
            if (!update.required && phase != Phase.Downloading) {
                { TextButton(onClick = updates::dismiss) { Text("Later") } }
            } else null,
    )
}
