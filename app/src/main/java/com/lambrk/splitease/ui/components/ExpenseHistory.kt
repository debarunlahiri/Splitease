package com.lambrk.splitease.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.lambrk.splitease.data.ExpenseAttachment
import com.lambrk.splitease.data.objects
import com.lambrk.splitease.ui.util.friendlyDateTime
import com.lambrk.splitease.ui.util.optionalText
import org.json.JSONObject

/** A timeline of everything that happened to an expense, newest first, with exactly what changed. */
@Composable
internal fun ExpenseHistory(
    entries: List<JSONObject>,
    load: suspend (ExpenseAttachment) -> java.io.File,
    onOpen: (ExpenseAttachment) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val newestFirst = entries.sortedByDescending { it.optLong("revision") }
    Column(Modifier.fillMaxWidth()) {
        newestFirst.forEachIndexed { index, entry ->
            val action = entry.optString("action")
            val dot = when (action) {
                "DELETED" -> colors.error
                "PAYMENT_RECORDED" -> colors.tertiary
                "CREATED" -> colors.primary
                else -> colors.secondary
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(16.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.padding(top = 6.dp).size(12.dp).then(Modifier), contentAlignment = Alignment.Center) {
                        Surface(Modifier.size(12.dp), shape = CircleShape, color = dot) {}
                    }
                    if (index != newestFirst.lastIndex) {
                        Box(Modifier.weight(1f).width(2.dp).padding(top = 4.dp)) {
                            Surface(Modifier.fillMaxHeight().width(2.dp), color = colors.outlineVariant) {}
                        }
                    }
                }
                Column(Modifier.weight(1f).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val summary = entry.optionalText("summary").ifBlank {
                        entry.optionalText("action").lowercase().replaceFirstChar { it.titlecase() }
                    }
                    Text(summary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        friendlyDateTime(entry.optString("occurredAt")) + " · revision ${entry.optLong("revision")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    val changes = entry.optJSONArray("changes")?.objects().orEmpty()
                    if (changes.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(14.dp), color = colors.surfaceContainer) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                changes.forEach { change -> ChangeRow(change, load, onOpen) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeRow(
    change: JSONObject,
    load: suspend (ExpenseAttachment) -> java.io.File,
    onOpen: (ExpenseAttachment) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val kind = change.optString("kind")
    val from = change.optionalText("from")
    val to = change.optionalText("to")
    val file = change.optJSONObject("attachment")?.let {
        ExpenseAttachment(it.getString("id"), it.getString("name"), it.getString("contentType"))
    }
    if (file != null) {
        val removed = kind == "REMOVED"
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(change.optString("label"), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AttachmentCard(
                    attachment = file,
                    load = load,
                    onClick = { onOpen(file) },
                    onRemove = null,
                    modifier = Modifier.width(96.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (removed) colors.error else colors.primary,
                        textDecoration = if (removed) TextDecoration.LineThrough else null,
                    )
                    Text(
                        "Tap to view",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(change.optString("label"), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        when (kind) {
            "ADDED" -> Text(to, style = MaterialTheme.typography.bodyMedium, color = colors.primary)
            "REMOVED" -> Text(
                from,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
                textDecoration = TextDecoration.LineThrough,
            )
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (from.isNotBlank()) {
                    Text(
                        from,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text("→", color = colors.onSurfaceVariant)
                }
                Text(to, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
            }
        }
    }
}
