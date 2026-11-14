@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lambrk.splitease.ui.screens.profile

import com.lambrk.splitease.ui.i18n.tr

import com.lambrk.splitease.ui.util.friendlyDate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.lambrk.splitease.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.components.Panel
import com.lambrk.splitease.ui.components.SectionTitle
import com.lambrk.splitease.ui.util.money
import com.lambrk.splitease.ui.util.optionalText
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

@Composable
internal fun PlanScreen(model: SplitEaseViewModel, onBack: () -> Unit) {
    val entitlement = model.entitlement
    val activeCode = entitlement?.optionalText("planCode")
    var plans by remember { mutableStateOf<List<JSONObject>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var request by remember { mutableIntStateOf(0) }

    LaunchedEffect(request) {
        loadFailed = false
        try {
            plans = model.availablePlans()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadFailed = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Plan") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to profile")
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SectionTitle(tr("Current plan")) }
            item {
                Panel {
                    if (entitlement == null) {
                        Text(tr("Your plan details are unavailable."))
                    } else {
                        val name = plans?.firstOrNull { it.optString("code") == activeCode }
                            ?.optString("name") ?: planName(activeCode.orEmpty())
                        Text(name, style = MaterialTheme.typography.titleLarge)
                        Text(if (entitlement.optBoolean("adsEnabled", true)) "Includes ads" else "No ads")
                        val expiry = friendlyDate(entitlement.optionalText("expiresAt"))
                        if (expiry.isNotBlank()) Text("Valid until $expiry")
                    }
                }
            }
            item { SectionTitle(tr("Plan options")) }
            val catalog = plans
            when {
                loadFailed -> item {
                    Panel {
                        Text(tr("Couldn’t load plan options."))
                        TextButton(onClick = { request++ }) { Text("Retry") }
                    }
                }
                catalog == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                catalog.isEmpty() -> item { Text(tr("No other plans are available.")) }
                else -> items(catalog, key = { it.getString("code") }) { plan ->
                    Panel {
                        Text(plan.getString("name"), style = MaterialTheme.typography.titleMedium)
                        val price = money(plan.getString("price"), plan.getString("currency"))
                        Text("$price for ${plan.getInt("durationDays")} days")
                        if (plan.optBoolean("removesAds")) Text(tr("No ads"))
                    }
                }
            }
        }
    }
}

internal fun planName(code: String): String =
    code.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
