package com.lambrk.splitease.ui.screens.auth

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.lambrk.splitease.ui.i18n.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import com.lambrk.splitease.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambrk.splitease.ui.components.Heading
import com.lambrk.splitease.ui.components.Symbol

@Composable
internal fun LoginScreen(busy: Boolean, login: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Heading(
            title = "SplitEase",
            subtitle = tr("Good times start together."),
            label = "WELCOME",
        )
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(
                Modifier.fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer,
                            )
                        ),
                        RoundedCornerShape(
                            topStart = 72.dp,
                            topEnd = 28.dp,
                            bottomStart = 28.dp,
                            bottomEnd = 72.dp,
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Symbol(Icons.Rounded.Restaurant, Modifier.size(68.dp))
                    Symbol(Icons.Rounded.Handshake, Modifier.size(100.dp))
                    Symbol(Icons.Rounded.FlightTakeoff, Modifier.size(68.dp))
                }
            }
            Text(
                tr("Good times.\nShared fairly."),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                lineHeight = 52.sp,
            )
            Text(
                tr("Trips, rent, dinner and everything in between. Split costs with friends the easy way, so you can enjoy the moment."),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(tr("Split together")) },
                    icon = { Icon(Icons.Rounded.Groups, null, Modifier.size(18.dp)) },
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text(tr("Pay back easily")) },
                    icon = { Icon(Icons.Rounded.DoneAll, null, Modifier.size(18.dp)) },
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = login,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = CircleShape,
            ) {
                Icon(Icons.Rounded.AccountCircle, null)
                Spacer(Modifier.width(12.dp))
                Text(tr("Continue with Google"), fontSize = 16.sp)
            }
            Text(
                tr("Your plans. Your people. All sorted."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var showPrivacy by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            androidx.compose.material3.TextButton(onClick = { showPrivacy = true }) {
                Text("By continuing you agree to our Privacy policy", style = MaterialTheme.typography.bodySmall, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
            }
            if (showPrivacy) com.lambrk.splitease.ui.components.PrivacyPolicyDialog { showPrivacy = false }
        }
    }
}
