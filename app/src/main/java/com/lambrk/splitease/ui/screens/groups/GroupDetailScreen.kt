@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lambrk.splitease.ui.screens.groups

import com.lambrk.splitease.ui.i18n.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.lambrk.splitease.data.objects
import com.lambrk.splitease.ui.components.ProfileAvatar
import com.lambrk.splitease.ui.util.memberPhotoUrl
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.lambrk.splitease.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.components.CardShape
import com.lambrk.splitease.ui.components.RefreshableList
import com.lambrk.splitease.ui.util.money
import java.math.BigDecimal
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
internal fun GroupDetailScreen(
    model: SplitEaseViewModel,
    onInvite: () -> Unit,
    onEdit: (JSONObject) -> Unit,
    onDraft: (String) -> Unit,
    onPay: (JSONObject) -> Unit,
) {
    val group = model.selected ?: return
    val groupId = group.getString("id")
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(groupId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                model.refreshVisibleGroup(groupId)
                delay(15_000)
            }
        }
    }
    var section by remember(groupId) { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = group.getString("name"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            navigationIcon = {
                IconButton(onClick = model::closeGroup) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to Splits")
                }
            },
            actions = {
                // Only the person who created the Splits can change its settings.
                if (group.optString("createdBy") == model.userId) {
                    var menu by remember { mutableStateOf(false) }
                    val invoicesOn = group.optBoolean("invoicesEnabled", true)
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Rounded.MoreVert, "Splits settings")
                        }
                        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Invoices")
                                        Text(
                                            if (invoicesOn) "On · friends can download invoices" else "Off · no invoices for this Splits",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                trailingIcon = {
                                    androidx.compose.material3.Switch(checked = invoicesOn, onCheckedChange = null)
                                },
                                enabled = !model.busy,
                                onClick = {
                                    menu = false
                                    model.setGroupInvoices(!invoicesOn)
                                },
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        Box(Modifier.weight(1f)) {
            RefreshableList(
                isRefreshing = model.refreshing,
                onRefresh = { model.refreshSelectedGroup(showIndicator = true) },
                enabled = !model.busy,
                contentPadding = PaddingValues(24.dp, 16.dp, 24.dp, 110.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    val amount =
                        model.balances.find { it.optString("userId") == model.userId }?.optString("amount")
                            ?: "0"
                    val value = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val colors = MaterialTheme.colorScheme
                    val container = when {
                        !model.groupDataAvailable || value.signum() == 0 -> colors.secondaryContainer
                        value.signum() > 0 -> colors.primaryContainer
                        else -> colors.errorContainer
                    }
                    val onContainer = when {
                        !model.groupDataAvailable || value.signum() == 0 -> colors.onSecondaryContainer
                        value.signum() > 0 -> colors.onPrimaryContainer
                        else -> colors.onErrorContainer
                    }
                    val members = group.getJSONArray("members").objects()
                    Surface(color = container, contentColor = onContainer, shape = CardShape) {
                        Column(
                            Modifier.fillMaxWidth().padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Surface(shape = CircleShape, color = onContainer.copy(alpha = 0.12f)) {
                                Text(
                                    if (value.signum() > 0) "You get back"
                                    else if (value.signum() < 0) "You owe" else "Where you stand",
                                    Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            Text(
                                if (model.groupDataAvailable)
                                    money(value.abs().toPlainString(), group.getString("defaultCurrency"))
                                else "Not available",
                                Modifier.padding(top = 6.dp),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (!model.groupDataAvailable)
                                    "Connect to see what you owe or get back. Splits still syncing aren't counted yet."
                                else if (value.signum() == 0) "All square. That's a good feeling."
                                else if (value.signum() > 0) "Your friends in this group owe you overall."
                                else "You owe your friends in this group overall.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = onContainer.copy(alpha = 0.8f),
                            )
                            if (
                                model.showingSavedData ||
                                    (!model.busy && model.hasPendingGroupExpenses)
                            ) {
                                Text(
                                    tr("Last saved amount. Splits still syncing aren't counted."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onContainer.copy(alpha = 0.8f),
                                )
                            }
                            Row(
                                Modifier.padding(top = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val shown = members.take(4)
                                Box(Modifier.width(30.dp + 22.dp * (shown.size - 1).coerceAtLeast(0))) {
                                    shown.forEachIndexed { i, m ->
                                        Box(Modifier.offset(x = 22.dp * i).border(2.dp, container, CircleShape)) {
                                            ProfileAvatar(memberPhotoUrl(m.optString("userId"), model), size = 30.dp)
                                        }
                                    }
                                }
                                Text(
                                    (if (members.size > 4) "+${members.size - 4} · " else "") +
                                        "${members.size} members · ${group.getString("defaultCurrency")}",
                                    Modifier.padding(start = 10.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
                item {
                    // Pill-shaped switcher: one line per label, no wrapping.
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Splits", "Balances", "People").forEachIndexed { index, label ->
                                val on = section == index
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .clip(CircleShape)
                                        .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable {
                                            section = index
                                            model.refreshSelectedGroup()
                                        }
                                        .padding(vertical = 11.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        tr(label),
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                when (section) {
                    0 -> GroupExpensesContent(model, onEdit, onDraft)
                    1 -> GroupBalancesContent(model, onPay)
                    else -> GroupPeopleContent(model, group, onInvite)
                }
            }
        }
    }
}
