package com.debarunlahiri.splitease.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.debarunlahiri.splitease.ui.components.RecordPaymentDialog
import com.debarunlahiri.splitease.ui.components.SessionRestoringScreen
import com.debarunlahiri.splitease.ui.forms.CreateGroupSheet
import com.debarunlahiri.splitease.ui.forms.ExpenseEditorSheet
import com.debarunlahiri.splitease.ui.forms.InviteMemberSheet
import com.debarunlahiri.splitease.ui.navigation.AppDestination
import com.debarunlahiri.splitease.ui.navigation.AppForm
import com.debarunlahiri.splitease.ui.navigation.SplitEaseBottomBar
import com.debarunlahiri.splitease.ui.navigation.SplitEaseNavigation
import com.debarunlahiri.splitease.ui.screens.auth.LoginScreen
import org.json.JSONObject

@Composable
fun SplitEaseApp(model: SplitEaseViewModel, activity: Activity) {
    var form by remember { mutableStateOf<AppForm?>(null) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var payment by remember { mutableStateOf<JSONObject?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(model.message) {
        model.message?.let {
            snackbar.showSnackbar(it)
            model.message = null
        }
    }
    BackHandler(enabled = model.selected != null && form == null) { model.closeGroup() }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (model.user != null && model.selected == null) {
                SplitEaseBottomBar(
                    model.destination,
                    model.notifications.count { !it.optBoolean("read") },
                ) {
                    model.destination = it
                }
            }
        },
        floatingActionButton = {
            if (
                model.user != null &&
                    (model.selected != null || model.destination == AppDestination.Groups)
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!model.busy) {
                            editing = null
                            form =
                                if (model.selected == null) AppForm.CreateGroup
                                else AppForm.ExpenseEditor
                        }
                    },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text(if (model.selected == null) "New group" else "Add expense") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (model.busy && !model.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            model.error?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            error,
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        IconButton(onClick = { model.error = null }) {
                            Icon(Icons.Rounded.Close, "Dismiss error")
                        }
                    }
                }
            }
            if (model.user != null && (model.showingSavedData || model.pendingExpenseCount > 0)) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (model.pendingExpenseCount > 0)
                            "${model.pendingExpenseCount} expenses waiting to sync. Saved on this device."
                        else "Showing saved data. Updates will load when connected.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (model.user != null && model.needsReconnect) {
                Text(
                    "Your saved data is available. Reconnect your account from Profile to sync updates.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (model.user == null && model.restoringSession) {
                SessionRestoringScreen()
            } else if (model.user == null) {
                LoginScreen(model.busy) { model.login(activity) }
            } else {
                SplitEaseNavigation(
                    model = model,
                    onInvite = { form = AppForm.InviteMember },
                    onEdit = {
                        model.clearHistory()
                        editing = it
                        form = AppForm.ExpenseEditor
                    },
                    onPay = { payment = it },
                    onLogout = { model.logout(activity) },
                )
            }
        }
    }
    if (form != null && model.user != null) {
        when (form) {
            null -> Unit
            AppForm.CreateGroup -> CreateGroupSheet(model) { form = null }
            AppForm.InviteMember -> InviteMemberSheet(model) { form = null }
            AppForm.ExpenseEditor ->
                ExpenseEditorSheet(model, editing) {
                    form = null
                    editing = null
                }
        }
    }
    payment?.let { suggestion ->
        RecordPaymentDialog(
            suggestion,
            model.busy,
            onDismiss = { payment = null },
            onConfirm = { model.settle(suggestion) },
        )
    }
}
