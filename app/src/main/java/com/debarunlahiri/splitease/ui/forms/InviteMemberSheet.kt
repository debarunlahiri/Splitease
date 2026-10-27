package com.debarunlahiri.splitease.ui.forms

import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.debarunlahiri.splitease.ui.SplitEaseViewModel
import com.debarunlahiri.splitease.ui.components.Field
import com.debarunlahiri.splitease.ui.components.FormSheet

@Composable
internal fun InviteMemberSheet(model: SplitEaseViewModel, dismiss: () -> Unit) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    FormSheet("Better together", model, dismiss) {
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
}
