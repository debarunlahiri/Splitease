package com.lambrk.splitease.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.screens.activity.ActivityScreen
import com.lambrk.splitease.ui.screens.friends.FriendsScreen
import com.lambrk.splitease.ui.screens.groups.GroupDetailScreen
import com.lambrk.splitease.ui.screens.groups.GroupsScreen
import com.lambrk.splitease.ui.screens.profile.ProfileScreen
import com.lambrk.splitease.ui.screens.profile.PlanScreen
import com.lambrk.splitease.ui.screens.settings.SettingsScreen
import com.lambrk.splitease.ui.theme.ThemeMode
import org.json.JSONObject

@Composable
internal fun SplitEaseNavigation(
    model: SplitEaseViewModel,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onInvite: () -> Unit,
    onEdit: (JSONObject) -> Unit,
    onDraft: (String) -> Unit,
    onPay: (JSONObject) -> Unit,
    onLogout: () -> Unit,
) {
    val destination = if (model.selected != null) AppDestination.GroupDetails else model.destination
    AnimatedContent(targetState = destination, label = "screen") { screen ->
        when (screen) {
            AppDestination.Groups -> GroupsScreen(model)
            AppDestination.Friends -> FriendsScreen(model)
            AppDestination.Activity -> ActivityScreen(model)
            AppDestination.Profile -> ProfileScreen(
                model,
                onSettings = { model.destination = AppDestination.Settings },
                onPlan = { model.destination = AppDestination.Plan },
            )
            AppDestination.Plan -> PlanScreen(model) {
                model.destination = AppDestination.Profile
            }
            AppDestination.Settings -> SettingsScreen(
                model,
                themeMode = themeMode,
                onThemeChange = onThemeChange,
                onBack = { model.destination = AppDestination.Profile },
                onLogout = onLogout,
            )
            AppDestination.GroupDetails -> GroupDetailScreen(model, onInvite, onEdit, onDraft, onPay)
        }
    }
}
