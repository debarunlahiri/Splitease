package com.debarunlahiri.splitease.ui.forms

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.debarunlahiri.splitease.ui.SplitEaseViewModel
import com.debarunlahiri.splitease.ui.components.Field
import com.debarunlahiri.splitease.ui.components.FormSheet

@Composable
internal fun InviteMemberSheet(model: SplitEaseViewModel, dismiss: () -> Unit) {
    var email by remember { mutableStateOf("") }
    FormSheet("Better together", model, dismiss) {
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
