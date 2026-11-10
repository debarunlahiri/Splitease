package com.lambrk.splitease.ui.forms

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.data.objects
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.components.Field
import com.lambrk.splitease.ui.components.FormSheet
import com.lambrk.splitease.ui.components.ProfileAvatar
import com.lambrk.splitease.ui.util.optionalText

@Composable
internal fun InviteMemberSheet(model: SplitEaseViewModel, dismiss: () -> Unit) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var choosingFriends by remember { mutableStateOf(false) }
    FormSheet("Better together", model, dismiss) {
        OutlinedButton(
            enabled = !model.busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = { choosingFriends = true },
        ) {
            Icon(Icons.Rounded.People, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Invite from Friends")
        }
        Text("Share a link so friends can sign in and join this group. Links are valid for 7 days.")
        OutlinedButton(
            enabled = !model.busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                model.shareInvite { invitation ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, invitation)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share group invitation"))
                }
            },
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Share invite link")
        }
        Text("Or invite a friend by email.")
        Text("Invite a friend using the email they use to sign in to SplitEase.")
        Text("They can accept after signing in, even if they haven't joined yet.")
        Field(email, { email = it }, "Friend's email address", keyboardType = KeyboardType.Email)
        Button(
            enabled = !model.busy && email.isNotBlank(),
            onClick = { model.invite(email, dismiss) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Send invitation")
        }
    }
    if (choosingFriends) {
        GroupFriendPicker(
            model = model,
            onDismiss = { choosingFriends = false },
            onSent = {
                choosingFriends = false
                dismiss()
            },
        )
    }
}

@Composable
private fun GroupFriendPicker(
    model: SplitEaseViewModel,
    onDismiss: () -> Unit,
    onSent: () -> Unit,
) {
    val group = model.selected ?: return
    var checkedIds by remember(group.getString("id")) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(Unit) { model.refreshFriends() }

    val memberIds = group.getJSONArray("members").objects().map { it.getString("userId") }.toSet()
    val pendingIds = model.groupInvitations
        .filter { it.optString("status") == "PENDING" }
        .map { it.optionalText("inviteeUserId") }
    val unavailableIds = memberIds + pendingIds
    val available = model.friends.filter { it.getString("id") !in unavailableIds }
    val selectedIds = available.map { it.getString("id") }.filter { it in checkedIds }.toSet()

    AlertDialog(
        onDismissRequest = { if (!model.busy) onDismiss() },
        title = { Text("Invite from Friends") },
        text = {
            if (available.isEmpty()) {
                when {
                    model.friendsLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    model.friendsLoadFailed -> Text("Couldn’t load your friends. Try again.")
                    else -> Text("No friends are available to invite to this group.")
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    item { Text("Select up to 20 friends to invite.") }
                    items(available, key = { it.getString("id") }) { friend ->
                        val id = friend.getString("id")
                        val checked = id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(
                                    value = checked,
                                    enabled = !model.busy && (checked || selectedIds.size < 20),
                                    role = Role.Checkbox,
                                    onValueChange = {
                                        checkedIds = if (it) checkedIds + id else checkedIds - id
                                    },
                                ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            ProfileAvatar(friend.optionalText("photoUrl"), size = 32.dp)
                            Text(friend.getString("displayName"), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (available.isEmpty() && model.friendsLoadFailed) {
                TextButton(onClick = model::refreshFriends) { Text("Retry") }
            } else {
                TextButton(
                    onClick = { model.inviteGroupFriends(selectedIds, onSent) },
                    enabled = selectedIds.isNotEmpty() && !model.busy && !model.friendsLoading,
                ) { Text("Send invitations") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !model.busy) { Text("Cancel") }
        },
    )
}
