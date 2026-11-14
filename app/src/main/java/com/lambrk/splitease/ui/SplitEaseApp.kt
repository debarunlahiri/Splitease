package com.lambrk.splitease.ui

import com.lambrk.splitease.ui.i18n.tr

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import com.lambrk.splitease.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.ui.components.RecordPaymentDialog
import com.lambrk.splitease.ui.components.SessionRestoringScreen
import com.lambrk.splitease.ui.forms.CreateGroupSheet
import com.lambrk.splitease.ui.forms.ExpenseEditorSheet
import com.lambrk.splitease.ui.forms.InviteMemberSheet
import com.lambrk.splitease.ui.navigation.AppDestination
import com.lambrk.splitease.ui.components.UpdateDialog
import com.lambrk.splitease.ui.navigation.AppForm
import com.lambrk.splitease.ui.navigation.SplitEaseBottomBar
import com.lambrk.splitease.ui.navigation.SplitEaseNavigation
import com.lambrk.splitease.ui.screens.auth.LoginScreen
import com.lambrk.splitease.ui.theme.ThemeMode
import com.lambrk.splitease.update.AppUpdateViewModel
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
internal fun SplitEaseApp(
    model: SplitEaseViewModel,
    activity: Activity,
    updates: AppUpdateViewModel,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
) {
    if (model.user != null && !model.restoringSession && model.friendInvitePending) {
        AlertDialog(
            onDismissRequest = { if (!model.busy) model.dismissFriendInvite() },
            title = { Text(tr("Accept friend invitation?")) },
            text = { Text(tr("You'll be connected in Friends on SplitEase.")) },
            confirmButton = {
                TextButton(onClick = model::acceptPendingFriendInvite, enabled = !model.busy) {
                    Text("Accept")
                }
            },
            dismissButton = {
                TextButton(onClick = model::dismissFriendInvite, enabled = !model.busy) {
                    Text("Cancel")
                }
            },
        )
    }
    var form by remember { mutableStateOf<AppForm?>(null) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var viewing by remember { mutableStateOf<JSONObject?>(null) }
    var expenseDraftKey by remember { mutableStateOf<String?>(null) }
    var viewingDraft by remember { mutableStateOf(false) }
    var payment by remember { mutableStateOf<JSONObject?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val retrySavedHome = model.showingSavedData && model.selected == null && !model.needsReconnect
    LaunchedEffect(retrySavedHome, lifecycle) {
        if (!retrySavedHome) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(10_000)
                model.refreshSavedHome()
            }
        }
    }
    LaunchedEffect(model.message) {
        model.message?.let {
            snackbar.showSnackbar(it)
            model.message = null
        }
    }
    // Ask to show notifications as soon as the app starts (Android 13+), before sign-in.
    // Android stops showing the dialog after two refusals, so we ask at most twice.
    val prompts = remember { activity.getSharedPreferences("permission_prompts", Context.MODE_PRIVATE) }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) return@rememberLauncherForActivityResult
            // Allowing notifications also turns on push, once there is an account to save it to.
            if (model.user != null) model.enablePush(true)
            else prompts.edit().putBoolean("enable_push_after_sign_in", true).apply()
        }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted =
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) return@LaunchedEffect
        val asked = prompts.getInt("notifications_asked", 0)
        if (asked >= 2) return@LaunchedEffect
        prompts.edit().putInt("notifications_asked", asked + 1).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // Allowed before signing in: turn push on as soon as an account exists.
    LaunchedEffect(model.user != null) {
        if (model.user != null && prompts.getBoolean("enable_push_after_sign_in", false)) {
            prompts.edit().remove("enable_push_after_sign_in").apply()
            model.enablePush(true)
        }
    }
    fun openDraft(key: String) {
        val groupId = model.selected?.optString("id") ?: return
        val draft = model.groupDrafts.firstOrNull { it.snapshotKey == key } ?: return
        val target = key.removePrefix("expense-draft:$groupId:")
        val isNew = target == "new" || target.startsWith("new:")
        editing = if (isNew) null else {
            model.expenses.firstOrNull { it.optString("id") == target }
                ?: JSONObject(draft.json).optJSONObject("sourceExpense")
        }
        model.clearHistory()
        expenseDraftKey = key
        viewingDraft = !isNew && editing == null
        form = AppForm.ExpenseEditor
    }

    BackHandler(enabled = model.selected != null && form == null) {
        model.closeGroup()
    }
    val profileDetail =
        model.destination == AppDestination.Settings || model.destination == AppDestination.Plan
    BackHandler(enabled = model.user != null && model.selected == null && profileDetail) {
        model.destination = AppDestination.Profile
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (
                model.user != null && model.selected == null && !profileDetail
            ) {
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
                            if (model.selected == null) {
                                form = AppForm.CreateGroup
                            } else {
                                editing = null
                                expenseDraftKey = null
                                viewingDraft = false
                                form = AppForm.ExpenseEditor
                            }
                        }
                    },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text(if (model.selected == null) "New Splits" else "Add split") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (model.busy && !model.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            val waitingToSync = !model.busy && model.pendingExpenseCount > 0
            if (model.user != null && (model.showingSavedData || waitingToSync)) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (waitingToSync)
                            "${model.pendingExpenseCount} splits waiting to be saved online. Safe on this phone."
                        else "Showing what's saved on this phone. We'll try again soon.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (model.user != null && model.needsReconnect) {
                Text(
                    tr("Your saved splits are here. Sign in again from Profile to get the latest."),
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
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    onInvite = { form = AppForm.InviteMember },
                    onEdit = {
                        model.clearHistory()
                        viewing = it
                    },
                    onDraft = ::openDraft,
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
                ExpenseEditorSheet(model, editing, expenseDraftKey, viewingDraft) {
                    form = null
                    editing = null
                    expenseDraftKey = null
                    viewingDraft = false
                }
        }
    }
    viewing?.let { expense ->
        if (model.user != null) {
            com.lambrk.splitease.ui.forms.ExpenseViewSheet(
                model = model,
                expense = expense,
                onEdit = {
                    model.clearHistory()
                    editing = expense
                    expenseDraftKey = null
                    viewingDraft = false
                    viewing = null
                    form = AppForm.ExpenseEditor
                },
                dismiss = { viewing = null; model.clearHistory() },
            )
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
    UpdateDialog(updates)
    model.error?.let { error ->
        AlertDialog(
            onDismissRequest = { model.error = null },
            icon = {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(tr("Something needs attention")) },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { model.error = null }) { Text("OK") }
            },
        )
    }
}
