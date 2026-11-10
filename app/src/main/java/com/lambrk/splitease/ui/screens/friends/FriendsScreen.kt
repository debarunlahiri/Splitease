package com.lambrk.splitease.ui.screens.friends

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.components.ProfileAvatar
import com.lambrk.splitease.ui.components.SectionTitle
import com.lambrk.splitease.ui.util.optionalText

@Composable
internal fun FriendsScreen(model: SplitEaseViewModel) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var emails by rememberSaveable { mutableStateOf("") }
    var showingEmailInvite by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { model.refreshFriends() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 32.dp),
    ) {
        item {
            Text(
                "Friends",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            SectionTitle("Invite friends", modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, colors.outlineVariant),
            ) {
                Column {
                    ListItem(
                        modifier = Modifier.clickable(
                            enabled = !model.busy,
                            role = Role.Button,
                            onClick = { showingEmailInvite = true },
                        ),
                        leadingContent = { Icon(Icons.Rounded.Email, contentDescription = null) },
                        headlineContent = { Text("Invite by email") },
                        supportingContent = { Text("Send invitations to up to 20 people.") },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        },
                    )
                    HorizontalDivider(color = colors.outlineVariant)
                    ListItem(
                        modifier = Modifier.clickable(
                            enabled = !model.busy,
                            role = Role.Button,
                            onClick = {
                                model.shareFriendInvite { message ->
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, message)
                                    }
                                    context.startActivity(Intent.createChooser(share, "Invite friends"))
                                }
                            },
                        ),
                        leadingContent = { Icon(Icons.Rounded.Share, contentDescription = null) },
                        headlineContent = { Text("Share invite link") },
                        supportingContent = { Text("The link is valid for seven days.") },
                    )
                }
            }
        }
        item {
            SectionTitle("Your friends", modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))
        }
        if (model.friendsLoading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } else if (model.friendsLoadFailed) {
            item {
                Column {
                    Text("Couldn’t refresh your friends.", color = colors.onSurfaceVariant)
                    TextButton(onClick = model::refreshFriends) { Text("Retry") }
                }
            }
        } else if (model.friends.isEmpty() && !model.busy) {
            item {
                Text(
                    "Friends appear here after they accept an invitation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        items(model.friends, key = { it.getString("id") }) { friend ->
            ListItem(
                headlineContent = {
                    Text(friend.getString("displayName"), fontWeight = FontWeight.Medium)
                },
                leadingContent = { ProfileAvatar(friend.optionalText("photoUrl"), size = 40.dp) },
            )
            HorizontalDivider(color = colors.outlineVariant)
        }
    }

    if (showingEmailInvite) {
        AlertDialog(
            onDismissRequest = { if (!model.busy) showingEmailInvite = false },
            title = { Text("Invite by email") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter up to 20 email addresses separated by commas or new lines.")
                    OutlinedTextField(
                        value = emails,
                        onValueChange = { emails = it },
                        label = { Text("Email addresses") },
                        enabled = !model.busy,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        maxLines = 4,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !model.busy && emails.isNotBlank(),
                    onClick = {
                        model.inviteFriends(emails) {
                            emails = ""
                            showingEmailInvite = false
                        }
                    },
                ) { Text("Send invitations") }
            },
            dismissButton = {
                TextButton(onClick = { showingEmailInvite = false }, enabled = !model.busy) {
                    Text("Cancel")
                }
            },
        )
    }
}
