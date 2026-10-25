package com.debarunlahiri.splitease.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
internal fun Symbol(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            RoundedCornerShape(22.dp),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
