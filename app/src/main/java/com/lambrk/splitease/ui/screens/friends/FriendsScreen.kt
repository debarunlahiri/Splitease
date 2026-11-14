package com.lambrk.splitease.ui.screens.friends

import com.lambrk.splitease.ui.util.openWhatsApp
import com.lambrk.splitease.ui.util.formatAsYouType
import com.lambrk.splitease.ui.util.exampleNumber
import com.lambrk.splitease.ui.util.withCountryCode
import com.lambrk.splitease.ui.util.defaultCountry
import com.lambrk.splitease.ui.util.COUNTRIES
import com.lambrk.splitease.ui.screens.profile.Flag
import com.lambrk.splitease.ui.screens.profile.CountryPicker
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Phone
import com.lambrk.splitease.ui.i18n.tr

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
import com.lambrk.splitease.ui.i18n.Text
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
    var showingPhoneInvite by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<org.json.JSONObject?>(null) }

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
            SectionTitle(tr("Invite friends"), modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
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
                        headlineContent = { Text(tr("Invite by email")) },
                        supportingContent = { Text(tr("Send invitations to up to 20 people.")) },
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
                            onClick = { showingPhoneInvite = true },
                        ),
                        leadingContent = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                        headlineContent = { Text("Add by phone number") },
                        supportingContent = { Text("Send the invite on WhatsApp or SMS.") },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
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
                        headlineContent = { Text(tr("Share invite link")) },
                        supportingContent = { Text(tr("The link is valid for seven days.")) },
                    )
                }
            }
        }
        item {
            SectionTitle(tr("Your friends"), modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))
        }
        if (model.friendsLoading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } else if (model.friendsLoadFailed) {
            item {
                Column {
                    Text(tr("Couldn’t refresh your friends."), color = colors.onSurfaceVariant)
                    TextButton(onClick = model::refreshFriends) { Text("Retry") }
                }
            }
        } else if (model.friends.isEmpty() && !model.busy) {
            item {
                Text(
                    tr("Friends appear here after they accept an invitation."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        items(model.friends, key = { it.getString("id") }) { friend ->
            ListItem(
                modifier = Modifier.clickable(role = Role.Button) { model.openFriend(friend.getString("id")) },
                headlineContent = {
                    Text(friend.getString("displayName"), fontWeight = FontWeight.Medium)
                },
                leadingContent = { ProfileAvatar(friend.optionalText("photoUrl"), size = 40.dp) },
                trailingContent = {
                    androidx.compose.material3.IconButton(onClick = { removing = friend }, enabled = !model.busy) {
                        Icon(Icons.Rounded.PersonRemove, contentDescription = "Remove friend", tint = colors.onSurfaceVariant)
                    }
                },
            )
            HorizontalDivider(color = colors.outlineVariant)
        }
    }

    if (model.friendDetailLoading || model.friendDetail != null || model.friendDetailFailed) FriendDetailSheet(model)

    removing?.let { friend ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${friend.getString("displayName")}?") },
            text = { Text("You will no longer be friends. Splits you already share stay as they are.") },
            confirmButton = {
                TextButton(onClick = { model.removeFriend(friend.getString("id")); removing = null }, enabled = !model.busy) {
                    Text("Remove", color = colors.error)
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }

    if (showingPhoneInvite) AddByPhoneSheet(model) { showingPhoneInvite = false }

    if (showingEmailInvite) {
        AlertDialog(
            onDismissRequest = { if (!model.busy) showingEmailInvite = false },
            title = { Text(tr("Invite by email")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr("Enter up to 20 email addresses separated by commas or new lines."))
                    OutlinedTextField(
                        value = emails,
                        onValueChange = { emails = it },
                        label = { Text(tr("Email addresses")) },
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
                ) { Text(tr("Send invitations")) }
            },
            dismissButton = {
                TextButton(onClick = { showingEmailInvite = false }, enabled = !model.busy) {
                    Text("Cancel")
                }
            },
        )
    }
}

/** Type a number with its country code, then send the friend invite from your own phone (WhatsApp or SMS). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AddByPhoneSheet(model: SplitEaseViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var country by rememberSaveable { mutableStateOf(defaultCountry(context).iso) }
    val current = COUNTRIES.firstOrNull { it.iso == country } ?: COUNTRIES.first()
    var digits by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    val full = if (digits.isBlank()) null else withCountryCode(digits, current.iso)
    val example = exampleNumber(current.iso)
    val shown = formatAsYouType(current.iso, digits)

    if (picking) CountryPicker(onPick = { country = it.iso; picking = false }, onDismiss = { picking = false })

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Add by phone number", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("We'll open WhatsApp or SMS with an invite ready to send.", color = colors.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.OutlinedButton(onClick = { picking = true }, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(56.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Flag(current.iso)
                        Text("+${current.dial}")
                    }
                }
                OutlinedTextField(
                    value = androidx.compose.ui.text.input.TextFieldValue(shown, androidx.compose.ui.text.TextRange(shown.length)),
                    onValueChange = { digits = it.text.filter { ch -> ch.isDigit() }.take(15) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(example ?: "Phone number") },
                    isError = digits.isNotBlank() && full == null,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }
            if (digits.isNotBlank() && full == null) {
                Text("Enter a valid number for ${current.name}" + (example?.let { ", like $it" } ?: "") + ".", style = MaterialTheme.typography.bodySmall, color = colors.error)
            }
            androidx.compose.material3.Button(
                onClick = { model.shareFriendInvite { text -> openWhatsApp(context, full, text); onDismiss() } },
                enabled = !model.busy && full != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Send on WhatsApp") }
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    model.shareFriendInvite { text ->
                        val sms = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:${android.net.Uri.encode(full)}")).putExtra("sms_body", text)
                        runCatching { context.startActivity(sms) }
                        onDismiss()
                    }
                },
                enabled = !model.busy && full != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Send as SMS") }
        }
    }
}
