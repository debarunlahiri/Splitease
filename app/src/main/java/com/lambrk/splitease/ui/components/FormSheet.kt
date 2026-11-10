@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lambrk.splitease.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.ui.SplitEaseViewModel

@Composable
internal fun FormSheet(
    title: String,
    model: SplitEaseViewModel,
    dismiss: () -> Unit,
    confirmDismiss: (() -> Boolean)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val latestConfirmDismiss = rememberUpdatedState(confirmDismiss)
    val allowDismiss = { !model.busy && latestConfirmDismiss.value?.invoke() != false }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> target != SheetValue.Hidden || allowDismiss() },
    )
    ModalBottomSheet(
        onDismissRequest = { if (allowDismiss()) dismiss() },
        sheetState = sheetState,
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().imePadding(),
            contentPadding = PaddingValues(24.dp, 0.dp, 24.dp, 32.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Heading(
                        title = title,
                        subtitle = "Good times, shared fairly.",
                        label = "LET’S GET IT SORTED",
                    )
                    if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    content()
                }
            }
        }
    }
}
