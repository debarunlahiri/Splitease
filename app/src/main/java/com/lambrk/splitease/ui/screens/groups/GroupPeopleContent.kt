package com.lambrk.splitease.ui.screens.groups

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
import androidx.compose.material3.Text
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
            "Invite friends by link or email to join your circle.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (group.getString("createdBy") == model.userId) {
            FilledTonalButton(enabled = !model.busy, onClick = onInvite) {
                Icon(Icons.Rounded.PersonAdd, null)
                Spacer(Modifier.width(8.dp))
                Text("Invite a friend")
            }
        }
    }
    items(group.getJSONArray("members").objects(), key = { it.getString("userId") }) { member ->
        val id = member.getString("userId")
        PersonCard(
            name = memberName(id, model),
            detail = member.getString("role").lowercase().replaceFirstChar { it.uppercase() },
            photoUrl = memberPhotoUrl(id, model),
        )
    }
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
                Text("Resend invite")
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
