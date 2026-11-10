package com.lambrk.splitease.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambrk.splitease.ui.util.currencySymbol

/** A round badge showing a currency's symbol, used as its icon in lists and fields. */
@Composable
internal fun CurrencyBadge(code: String, modifier: Modifier = Modifier) {
    val symbol = currencySymbol(code)
    Box(
        modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = if (symbol.length > 1) 11.sp else 16.sp,
            maxLines = 1,
        )
    }
}
