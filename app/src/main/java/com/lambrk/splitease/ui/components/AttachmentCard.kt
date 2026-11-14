package com.lambrk.splitease.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.lambrk.splitease.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lambrk.splitease.data.ExpenseAttachment
import java.io.File
import kotlinx.coroutines.CancellationException

private fun documentIcon(attachment: ExpenseAttachment): ImageVector {
    val type = attachment.contentType.lowercase()
    val ext = attachment.name.substringAfterLast('.', "").lowercase()
    return when {
        attachment.isImage -> Icons.Rounded.Image
        type == "application/pdf" || ext == "pdf" -> Icons.Rounded.PictureAsPdf
        ext in setOf("xls", "xlsx", "csv", "ods") || "spreadsheet" in type || "excel" in type || type == "text/csv" ->
            Icons.Rounded.TableChart
        ext in setOf("zip", "rar", "7z", "gz", "tar") || "zip" in type || "compressed" in type ->
            Icons.Rounded.FolderZip
        type.startsWith("text/") || ext in setOf("txt", "md", "rtf") -> Icons.Rounded.TextSnippet
        else -> Icons.Rounded.Description
    }
}

/** A square card: an image preview or a document-type icon, the file name, and a close button on the top right. */
@Composable
internal fun AttachmentCard(
    attachment: ExpenseAttachment,
    load: suspend (ExpenseAttachment) -> File,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var file by remember(attachment.id) { mutableStateOf<File?>(null) }
    if (attachment.isImage) {
        LaunchedEffect(attachment.id) {
            file = try {
                load(attachment)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(modifier.aspectRatio(1f)) {
        Surface(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                .clickable(enabled = enabled, onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            val preview = file
            if (attachment.isImage && preview != null) {
                AsyncImage(
                    model = preview,
                    contentDescription = attachment.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        documentIcon(attachment),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        attachment.name,
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (onRemove != null) {
            Surface(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                contentColor = androidx.compose.ui.graphics.Color.White,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Remove ${attachment.name}",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
