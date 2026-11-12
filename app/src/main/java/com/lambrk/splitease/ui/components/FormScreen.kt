package com.lambrk.splitease.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.ui.SplitEaseViewModel

/** One page of a [FormScreen]. [valid] gates the Next button (and Save on the last page). */
internal class FormStep(
    val title: String,
    val subtitle: String = "",
    val valid: Boolean = true,
    val content: @Composable ColumnScope.() -> Unit,
)

/**
 * Full-screen, step-by-step form. Replaces the old bottom sheet: each [FormStep] is shown on its
 * own page with a progress indicator and Back / Next buttons; the final page shows [submitLabel].
 * Pass [onSubmit] = null for a read-only form (the last page then has no submit button).
 */
@Composable
internal fun FormScreen(
    title: String,
    model: SplitEaseViewModel,
    dismiss: () -> Unit,
    steps: List<FormStep>,
    submitLabel: String,
    submitEnabled: Boolean = true,
    onSubmit: (() -> Unit)? = null,
    confirmDismiss: (() -> Boolean)? = null,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val last = index >= steps.lastIndex
    val latestConfirmDismiss = rememberUpdatedState(confirmDismiss)
    fun close() {
        if (!model.busy && latestConfirmDismiss.value?.invoke() != false) dismiss()
    }
    fun back() {
        if (index > 0) index-- else close()
    }
    BackHandler(onBack = ::back)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::back, enabled = !model.busy) {
                    if (index > 0) Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Previous step")
                    else Icon(Icons.Rounded.Close, "Close")
                }
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (steps.size > 1) {
                    Text(
                        "Step ${index + 1} of ${steps.size}",
                        modifier = Modifier.padding(end = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (steps.size > 1) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.indices.forEach { i ->
                        Surface(
                            modifier = Modifier.weight(1f).height(4.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            color = if (i <= index) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {}
                    }
                }
            }
            if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    step.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (step.subtitle.isNotBlank()) {
                    Text(
                        step.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                step.content(this)
            }
            if (!last || onSubmit != null) {
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (index > 0) {
                        OutlinedButton(
                            onClick = ::back,
                            enabled = !model.busy,
                            modifier = Modifier.weight(1f),
                        ) { Text("Back") }
                    }
                    if (last) {
                        Button(
                            onClick = { onSubmit?.invoke() },
                            enabled = !model.busy && submitEnabled && step.valid,
                            modifier = Modifier.weight(1f),
                        ) { Text(submitLabel) }
                    } else {
                        Button(
                            onClick = { index++ },
                            enabled = !model.busy && step.valid,
                            modifier = Modifier.weight(1f),
                        ) { Text("Next") }
                    }
                }
            } else {
                Column(Modifier.navigationBarsPadding()) {}
            }
        }
    }
}
