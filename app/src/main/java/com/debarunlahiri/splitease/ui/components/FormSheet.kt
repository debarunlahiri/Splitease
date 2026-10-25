@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.debarunlahiri.splitease.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.debarunlahiri.splitease.ui.SplitEaseViewModel

@Composable
internal fun FormSheet(
    title: String,
    model: SplitEaseViewModel,
    dismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { if (!model.busy) dismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().imePadding(),
            contentPadding = PaddingValues(24.dp, 0.dp, 24.dp, 32.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    content()
                }
            }
        }
    }
}
