package com.debarunlahiri.splitease.ui.navigation

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.runtime.Composable
import com.debarunlahiri.splitease.ui.SplitEaseViewModel
import com.debarunlahiri.splitease.ui.screens.activity.ActivityScreen
import com.debarunlahiri.splitease.ui.screens.groups.GroupDetailScreen
import com.debarunlahiri.splitease.ui.screens.groups.GroupsScreen
import com.debarunlahiri.splitease.ui.screens.profile.ProfileScreen
import org.json.JSONObject

@Composable
internal fun SplitEaseNavigation(
    model: SplitEaseViewModel,
    onInvite: () -> Unit,
    onEdit: (JSONObject) -> Unit,
    onPay: (JSONObject) -> Unit,
    onLogout: () -> Unit,
) {
    val destination = if (model.selected != null) AppDestination.GroupDetails else model.destination
    AnimatedContent(targetState = destination, label = "screen") { screen ->
        when (screen) {
            AppDestination.Groups -> GroupsScreen(model)
            AppDestination.Activity -> ActivityScreen(model)
            AppDestination.Profile -> ProfileScreen(model, onLogout)
            AppDestination.GroupDetails -> GroupDetailScreen(model, onInvite, onEdit, onPay)
        }
    }
}
