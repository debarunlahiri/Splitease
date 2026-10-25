package com.debarunlahiri.splitease.ui.screens.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.debarunlahiri.splitease.data.objects
import com.debarunlahiri.splitease.ui.SplitEaseViewModel
import com.debarunlahiri.splitease.ui.components.Panel
import com.debarunlahiri.splitease.ui.components.Symbol
import com.debarunlahiri.splitease.ui.util.memberName
import com.debarunlahiri.splitease.ui.util.optionalText
import org.json.JSONObject

internal fun LazyListScope.GroupPeopleContent(
    model: SplitEaseViewModel,
    group: JSONObject,
    onInvite: () -> Unit,
) {
    item {
        Text(
            "Invite friends by email. They can join after accepting your invitation.",
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
    items(model.groupInvitations, key = { "invitation-${it.getString("id")}" }) { invitation ->
        Panel {
            Text(
                "Invitation to ${invitation.optionalText("inviteeEmail").ifBlank { "a group member" }}"
            )
            Text(
                invitation.getString("status"),
                style = MaterialTheme.typography.labelMedium,
            )
            if (invitation.getString("status") == "PENDING")
                TextButton(
                    enabled = !model.busy,
                    onClick = { model.revoke(invitation) },
                ) {
                    Text("Revoke invitation")
                }
        }
    }
    items(group.getJSONArray("members").objects(), key = { it.getString("userId") }) { member ->
        Panel {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Symbol(Icons.Rounded.Person, Modifier.size(42.dp))
                Column {
                    Text(
                        memberName(member.getString("userId"), model),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        member.getString("role").lowercase().replaceFirstChar {
                            it.uppercase()
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
