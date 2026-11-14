package com.lambrk.splitease.ui.forms

import com.lambrk.splitease.ui.i18n.tr

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import com.lambrk.splitease.data.DeviceContact
import com.lambrk.splitease.data.loadDeviceContacts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.lambrk.splitease.ui.i18n.Text
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
import com.lambrk.splitease.ui.components.FormScreen
import com.lambrk.splitease.ui.components.FormStep
import com.lambrk.splitease.ui.components.ProfileAvatar
import com.lambrk.splitease.ui.util.optionalText

@Composable
internal fun InviteMemberSheet(model: SplitEaseViewModel, dismiss: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var choosingFriends by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf<List<DeviceContact>?>(null) }
    // Picked people by key (email, else phone): what the Invite button will send to.
    var picked by remember { mutableStateOf(linkedMapOf<String, DeviceContact>()) }
    var contactsAllowed by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contactsAllowed = it }
    LaunchedEffect(contactsAllowed) {
        if (contactsAllowed && contacts == null) {
            contacts = try { loadDeviceContacts(context) } catch (_: Exception) { emptyList() }
        } else if (!contactsAllowed) {
            permission.launch(Manifest.permission.READ_CONTACTS)
        }
    }
    val group = model.selected
    val memberEmails = group?.optJSONArray("members")?.objects().orEmpty()
        .map { it.optString("email").lowercase() }.filter { it.isNotBlank() }.toSet()
    val memberNames = group?.optJSONArray("members")?.objects().orEmpty()
        .map { com.lambrk.splitease.ui.util.memberName(it.optString("userId"), model).lowercase() }.toSet()
    val needle = query.trim().lowercase()
    fun matches(vararg text: String?) = needle.isEmpty() || text.any { it?.lowercase()?.contains(needle) == true }
    fun keyOf(c: DeviceContact) = c.email ?: c.phone ?: c.id
    val typedEmail = needle.takeIf { android.util.Patterns.EMAIL_ADDRESS.matcher(it).matches() }
    val shown = (contacts ?: emptyList()).filter {
        it.email !in memberEmails && it.name.lowercase() !in memberNames && matches(it.name, it.email, it.phone)
    }.take(100)
    val enabled = !model.busy
    fun toggle(c: DeviceContact) {
        picked = LinkedHashMap(picked).also { if (keyOf(c) in it) it.remove(keyOf(c)) else it[keyOf(c)] = c }
    }

    fun sendInvites() {
        val chosen = picked.values.toList()
        val emails = chosen.mapNotNull { it.email }
        val phones = chosen.filter { it.email == null }.mapNotNull { p -> p.phone?.let { com.lambrk.splitease.ui.util.withCountryCode(it, context) ?: it } }
        fun smsLink() {
            if (phones.isEmpty()) return
            model.shareInvite { text ->
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phones.joinToString(";") { Uri.encode(it) }))
                    .putExtra("sms_body", text)
                context.startActivity(Intent.createChooser(intent, "Invite by message"))
            }
        }
        if (emails.isNotEmpty()) {
            model.inviteMany(emails) {
                smsLink()
                if (phones.isEmpty()) dismiss()
            }
        } else {
            smsLink()
        }
    }

    FormScreen(
        title = tr("Invite to Splits"),
        model = model,
        dismiss = dismiss,
        submitLabel = if (picked.isEmpty()) "Pick people to invite" else "Invite ${picked.size} ${if (picked.size == 1) "person" else "people"}",
        submitEnabled = picked.isNotEmpty(),
        onSubmit = ::sendInvites,
        steps = listOf(
            FormStep(tr("Invite your people"), tr("Pick people from your contacts, or share a link.")) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text(tr("Search contacts or type an email")) },
                    shape = RoundedCornerShape(18.dp),
                )
                if (picked.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        picked.values.forEach { c ->
                            androidx.compose.material3.Surface(
                                shape = RoundedCornerShape(50),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.inverseSurface,
                                modifier = Modifier.clickable { toggle(c) },
                            ) {
                                Text(
                                    c.name.substringBefore(" "),
                                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.inverseOnSurface,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(enabled = enabled, modifier = Modifier.weight(1f), onClick = { choosingFriends = true }) {
                        Icon(Icons.Rounded.People, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Friends")
                    }
                    OutlinedButton(
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            model.shareInvite { invitation ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, invitation)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share invite link"))
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Share link"))
                    }
                }
                Text(
                    tr("Invite links are valid for 7 days. People with an email get an invitation; people with only a phone number get the link by message."),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (typedEmail != null && typedEmail !in memberEmails && shown.none { it.email == typedEmail }) {
                    val typed = DeviceContact("typed:$typedEmail", typedEmail.substringBefore('@'), null, typedEmail)
                    PersonRow(typedEmail, "Invite by email", null, keyOf(typed) in picked, enabled, null, {}) { toggle(typed) }
                }
                if (!contactsAllowed) {
                    Text(
                        tr("Allow access to your contacts to invite people straight from your phone book. Contacts stay on your phone."),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { permission.launch(Manifest.permission.READ_CONTACTS) }) { Text(tr("Show my contacts")) }
                } else {
                    shown.forEach { contact ->
                        PersonRow(
                            name = contact.name,
                            detail = contact.phone ?: contact.email.orEmpty(),
                            photo = null,
                            selected = keyOf(contact) in picked,
                            enabled = enabled,
                            action = null,
                            onAction = {},
                        ) { toggle(contact) }
                    }
                    if (contacts != null && shown.isEmpty() && typedEmail == null) {
                        Text(tr("No contacts found."), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
        ),
    )
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
        title = { Text(tr("Invite from Friends")) },
        text = {
            if (available.isEmpty()) {
                when {
                    model.friendsLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    model.friendsLoadFailed -> Text(tr("Couldn’t load your friends. Try again."))
                    else -> Text(tr("None of your friends are left to invite."))
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    item { Text(tr("Select up to 20 friends to invite.")) }
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
                ) { Text(tr("Send invitations")) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !model.busy) { Text("Cancel") }
        },
    )
}
