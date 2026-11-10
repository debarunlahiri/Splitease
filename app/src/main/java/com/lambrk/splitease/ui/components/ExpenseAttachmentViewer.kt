package com.lambrk.splitease.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import com.lambrk.splitease.data.ExpenseAttachment
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun openAttachmentInAndroid(context: Context, attachment: ExpenseAttachment, file: File): Boolean {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.attachments", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, attachment.contentType)
        clipData = ClipData.newUri(context.contentResolver, attachment.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

@Composable
internal fun ExpenseAttachmentViewer(
    attachments: List<ExpenseAttachment>,
    initialPage: Int,
    load: suspend (ExpenseAttachment) -> File,
    openDocument: (ExpenseAttachment) -> Unit,
    onDismiss: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = initialPage) { attachments.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val imageLoader = remember(context.applicationContext) {
        ImageLoader.Builder(context.applicationContext)
            .components {
                add(SvgDecoder.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
            }
            .build()
    }
    val current = attachments[pager.currentPage]
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close attachment") }
                    Text(
                        current.name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("${pager.currentPage + 1} of ${attachments.size}")
                }
                HorizontalPager(
                    state = pager,
                    reverseLayout = true,
                    key = { attachments[it].id },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { page ->
                    val attachment = attachments[page]
                    if (attachment.isImage) {
                        AttachmentImage(attachment, load, imageLoader, openDocument)
                    } else {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { openDocument(attachment) }) {
                                Text("Open ${attachment.name}")
                            }
                        }
                    }
                }
                if (attachments.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(
                            enabled = pager.currentPage < attachments.lastIndex,
                            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        ) { Icon(Icons.Rounded.ChevronLeft, "Next attachment") }
                        IconButton(
                            enabled = pager.currentPage > 0,
                            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                        ) { Icon(Icons.Rounded.ChevronRight, "Previous attachment") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentImage(
    attachment: ExpenseAttachment,
    load: suspend (ExpenseAttachment) -> File,
    imageLoader: ImageLoader,
    openDocument: (ExpenseAttachment) -> Unit,
) {
    var file by remember(attachment.id) { mutableStateOf<File?>(null) }
    var failed by remember(attachment.id) { mutableStateOf(false) }
    LaunchedEffect(attachment.id) {
        try {
            file = load(attachment)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val image = file
        when {
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("This image couldn’t be previewed.")
                TextButton(onClick = { openDocument(attachment) }) { Text("Open with another app") }
            }
            image == null -> CircularProgressIndicator()
            else -> ZoomableAttachment(image, attachment.name, imageLoader, onError = { failed = true })
        }
    }
}

@Composable
private fun ZoomableAttachment(file: File, name: String, imageLoader: ImageLoader, onError: () -> Unit) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    fun constrain(value: Offset, scale: Float): Offset {
        if (bounds.width == 0 || bounds.height == 0 || scale <= 1f) return Offset.Zero
        val fit = if (imageSize.width > 0 && imageSize.height > 0) {
            minOf(bounds.width.toFloat() / imageSize.width, bounds.height.toFloat() / imageSize.height)
        } else 0f
        val x = ((imageSize.width * fit * scale - bounds.width) / 2).coerceAtLeast(0f)
        val y = ((imageSize.height * fit * scale - bounds.height) / 2).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-x, x), value.y.coerceIn(-y, y))
    }

    val transform = rememberTransformableState { zoomChange, pan, _ ->
        val nextZoom = (zoom * zoomChange).coerceIn(1f, 8f)
        offset = constrain(offset * (nextZoom / zoom) + pan, nextZoom)
        zoom = nextZoom
    }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { bounds = it }
            .clipToBounds()
            .transformable(state = transform, canPan = { zoom > 1f }, lockRotationOnZoomPan = true)
            .pointerInput(file) {
                detectTapGestures(onDoubleTap = { tap ->
                    val nextZoom = if (zoom > 1f) 1f else 2.5f
                    val center = Offset(bounds.width / 2f, bounds.height / 2f)
                    offset = constrain((center - tap) * (nextZoom - 1f), nextZoom)
                    zoom = nextZoom
                })
            },
    ) {
        AsyncImage(
            model = file,
            imageLoader = imageLoader,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            onSuccess = {
                val drawable = it.result.drawable
                imageSize = IntSize(drawable.intrinsicWidth, drawable.intrinsicHeight)
            },
            onError = { onError() },
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                translationX = offset.x
                translationY = offset.y
            },
        )
    }
}
