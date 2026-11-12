package com.lambrk.splitease.ui.components

import com.lambrk.splitease.ui.util.friendlyDate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A read-only date field that opens a date picker when tapped. The value stays an ISO
 * `YYYY-MM-DD` string, which is the format the API expects.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(
    value: String,
    change: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = friendlyDate(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.DateRange, contentDescription = "Choose date") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = enabled,
            shape = RoundedCornerShape(18.dp),
        )
        // A text field consumes taps itself, so a transparent layer on top opens the picker.
        if (enabled) {
            Box(
                Modifier.matchParentSize().clip(RoundedCornerShape(18.dp)).clickable {
                    open = true
                }
            )
        }
    }
    if (open) {
        // The picker works in UTC milliseconds; a plain date is that day's UTC midnight.
        val initial =
            runCatching {
                    LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                }
                .getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let {
                            change(
                                Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                            )
                        }
                        open = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}
