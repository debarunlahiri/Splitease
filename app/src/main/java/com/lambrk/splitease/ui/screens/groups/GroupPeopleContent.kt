package com.lambrk.splitease.ui.screens.groups

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.lambrk.splitease.ui.util.money
import com.lambrk.splitease.ui.util.friendlyDate
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.PersonRemove
import com.lambrk.splitease.ui.i18n.tr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.lambrk.splitease.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.data.objects
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.components.Panel
import com.lambrk.splitease.ui.components.ProfileAvatar
import com.lambrk.splitease.ui.components.Symbol
import com.lambrk.splitease.ui.util.memberName
import com.lambrk.splitease.ui.util.memberPhotoUrl
import com.lambrk.splitease.ui.util.optionalText
import java.util.Locale
import org.json.JSONObject

internal fun LazyListScope.GroupPeopleContent(
    model: SplitEaseViewModel,
    group: JSONObject,
    onInvite: () -> Unit,
) {
    item {
        Text(
            tr("Invite friends by link or email to join your circle."),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (group.getString("createdBy") == model.userId) {
            FilledTonalButton(enabled = !model.busy, onClick = onInvite) {
                Icon(Icons.Rounded.PersonAdd, null)
                Spacer(Modifier.width(8.dp))
                Text(tr("Invite a friend"))
            }
        }
    }
    items(group.getJSONArray("members").objects(), key = { it.getString("userId") }) { member ->
        val id = member.getString("userId")
        val owner = group.getString("createdBy")
        val canRemove = owner == model.userId && id != owner
        PersonCard(
            name = memberName(id, model),
            detail = member.getString("role").lowercase().replaceFirstChar { it.uppercase() },
            photoUrl = memberPhotoUrl(id, model),
            actions = if (canRemove) {
                {
                    TextButton(
                        enabled = !model.busy,
                        onClick = { model.checkMemberRemoval(id, memberName(id, model).substringBefore(" (")) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Rounded.PersonRemove, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Remove")
                    }
                }
            } else null,
        )
    }
    item(key = "member-removal") { MemberRemovalDialog(model) }
    // People who were invited by email but have not joined yet sit in the same list as the
    // members, with their status and the actions the owner can take.
    val pendingInvitations = model.groupInvitations.filter { it.optString("status") == "PENDING" }
    items(pendingInvitations, key = { "invitation-${it.getString("id")}" }) { invitation ->
        PersonCard(
            icon = Icons.Rounded.Email,
            name = invitation.optionalText("inviteeEmail").ifBlank { "Invited member" },
            detail = "Invited by email",
            status =
                invitation.getString("status").lowercase(Locale.ROOT).replaceFirstChar {
                    it.uppercase()
                },
        ) {
            TextButton(
                enabled = !model.busy,
                onClick = { model.resendInvitation(invitation) },
            ) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Resend invite"))
            }
            TextButton(
                enabled = !model.busy,
                onClick = { model.revoke(invitation) },
                colors =
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Revoke")
            }
        }
    }
}

/** One person in the People list: a member, or an invitee with a status and actions. */
@Composable
private fun PersonCard(
    name: String,
    detail: String,
    photoUrl: String? = null,
    icon: ImageVector? = null,
    status: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Panel {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon == null) ProfileAvatar(photoUrl, size = 42.dp)
            else Symbol(icon, Modifier.size(42.dp))
            Column(Modifier.weight(1f)) {
                // The name gets the full width on one line; the status sits below with the detail.
                Text(
                    name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(detail, style = MaterialTheme.typography.bodySmall)
                    if (status != null) StatusChip(status)
                }
            }
        }
        if (actions != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Asks before removing someone; if they are already in splits, lists those splits instead of removing them. */
@Composable
private fun MemberRemovalDialog(model: SplitEaseViewModel) {
    val check = model.memberCheck ?: return
    val count = check.usage.optInt("count")
    val canAdjust = check.usage.optBoolean("canAdjust")
    var confirming by androidx.compose.runtime.saveable.rememberSaveable(check.id) { androidx.compose.runtime.mutableStateOf(false) }
    if (confirming) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Remove ${check.name} and update the splits?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${check.name}'s part of each split will be shared among everyone else in it, so the totals stay the same.")
                    Text("Everyone affected gets a message, and each split's history shows the change.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { confirming = false; model.confirmRemoveMember(check.id, adjust = true) }, enabled = !model.busy) {
                    Text("Remove and update", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
    val splits = check.usage.optJSONArray("expenses")?.objects().orEmpty()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { model.dismissMemberCheck() },
        title = { Text(if (count == 0) "Remove ${check.name}?" else "${check.name} is already in ${if (count == 1) "1 split" else "$count splits"}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (count == 0) {
                    Text("${check.name} isn't in any split yet, so nothing changes for anyone else.")
                } else {
                    Text(
                        when {
                            canAdjust -> "You can take them out of these splits and share their part among everyone else."
                            check.usage.optString("blockedReason") == "PAID_FOR_SPLITS" -> "${check.name} paid for some of these splits, so delete those splits first."
                            else -> "${check.name} has already paid part of these splits, so they can't be taken out automatically."
                        },
                    )
                    Column(
                        Modifier.heightIn(max = 240.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        splits.forEach { e ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.optString("description"), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        friendlyDate(e.optString("expenseDate")) + if (e.optBoolean("paidByThem")) " · paid by them" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(money(e.optString("amount"), e.optString("currency")), fontWeight = FontWeight.Bold)
                            }
                        }
                        if (count > splits.size) Text("and ${count - splits.size} more", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            if (count == 0) {
                TextButton(onClick = { model.confirmRemoveMember(check.id) }, enabled = !model.busy) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            } else if (canAdjust) {
                TextButton(onClick = { confirming = true }, enabled = !model.busy) {
                    Text("Remove and update splits", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = { model.dismissMemberCheck() }) { Text("Got it") }
            }
        },
        dismissButton = if (count == 0 || canAdjust) {
            { TextButton(onClick = { model.dismissMemberCheck() }) { Text(if (count == 0) "Cancel" else "Not now") } }
        } else null,
    )
}
