package com.lambrk.splitease.ui.forms

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.lambrk.splitease.ui.components.Field
import com.lambrk.splitease.ui.i18n.Text
import com.lambrk.splitease.ui.util.friendlyDate
import com.lambrk.splitease.ui.util.money
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** What the person confirmed from a scanned receipt. */
internal class ScannedReceipt(
    val merchant: String,
    val total: String,
    val date: LocalDate?,
    /** The line items as text for the note, or null when they chose not to add them. */
    val itemsNote: String?,
    val photo: Uri,
)

/** A tappable card at the top of "What did you share?" that opens the scanner. */
@Composable
internal fun ScanReceiptCard(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = colors.secondaryContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = colors.onSecondaryContainer)
            Column(Modifier.weight(1f)) {
                Text("Scan a receipt", fontWeight = FontWeight.SemiBold, color = colors.onSecondaryContainer)
                Text("We'll fill in the total for you.", style = MaterialTheme.typography.bodySmall, color = colors.onSecondaryContainer)
            }
        }
    }
}

private sealed interface ScanStage {
    data object Choose : ScanStage
    data object Reading : ScanStage
    data object Failed : ScanStage
    class Review(val result: ReceiptResult, val photo: Uri) : ScanStage
}

/**
 * Take a photo or pick one, read it on the phone (nothing is uploaded), then let the person check and fix what was
 * found before anything is filled in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReceiptScanSheet(currency: String, onApply: (ScannedReceipt) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf<ScanStage>(ScanStage.Choose) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun read(uri: Uri) {
        stage = ScanStage.Reading
        scope.launch {
            stage = try {
                val text = withContext(Dispatchers.IO) {
                    val image = InputImage.fromFilePath(context, uri)
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).await().text
                }
                val result = ReceiptParser.parse(text)
                if (result.total == null && result.merchant == null) ScanStage.Failed else ScanStage.Review(result, uri)
            } catch (_: Exception) {
                ScanStage.Failed
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = cameraUri
        if (taken && uri != null) read(uri) else stage = ScanStage.Choose
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) read(uri) else stage = ScanStage.Choose
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Scan a receipt", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            when (val s = stage) {
                ScanStage.Choose -> {
                    Text("We read it on your phone. Nothing is uploaded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            val dir = File(context.cacheDir, "scans").apply { mkdirs() }
                            val file = File.createTempFile("receipt-", ".jpg", dir)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
                            cameraUri = uri
                            camera.launch(uri)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                        Text("Take a photo", Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = { gallery.launch("image/*") }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                        Text("Choose from gallery", Modifier.padding(start = 8.dp))
                    }
                    Text("Tip: lay the receipt flat in good light so the total is easy to read.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ScanStage.Reading -> Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text("Reading your receipt…")
                }
                ScanStage.Failed -> {
                    Text("We couldn't read that one. Try a clearer photo, or type the details yourself.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { stage = ScanStage.Choose }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Try again") }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Enter it myself") }
                }
                is ScanStage.Review -> ReviewScan(s, currency, onAgain = { stage = ScanStage.Choose }, onApply = onApply)
            }
        }
    }
}

@Composable
private fun ReviewScan(review: ScanStage.Review, currency: String, onAgain: () -> Unit, onApply: (ScannedReceipt) -> Unit) {
    val r = review.result
    var merchant by remember { mutableStateOf(r.merchant.orEmpty()) }
    var total by remember { mutableStateOf(r.total?.stripTrailingZeros()?.let { if (it.scale() < 0) it.setScale(0) else it }?.toPlainString().orEmpty()) }
    var addItems by remember { mutableStateOf(false) }
    val valid = total.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO && it.scale() <= 2 } == true
    Text("Check what we found, then fix anything that looks off.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Field(merchant, { merchant = it }, "Shop or description")
    Field(total, { total = it }, "Total", decimal = true, prefixText = com.lambrk.splitease.ui.util.currencySymbol(currency), groupingCurrency = currency)
    r.date?.let {
        Text("Date on the receipt: ${friendlyDate(it.toString())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (r.items.isNotEmpty()) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${r.items.size} items found", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("Add to note", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
                    Switch(checked = addItems, onCheckedChange = { addItems = it })
                }
                r.items.take(5).forEach { (name, price) ->
                    Row {
                        Text(name, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                        Text(money(price.toPlainString(), currency), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (r.items.size > 5) Text("and ${r.items.size - 5} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Button(
        onClick = {
            val note = if (addItems) r.items.joinToString("\n") { "${it.first}: ${it.second.toPlainString()}" } else null
            onApply(ScannedReceipt(merchant.trim(), total, r.date, note, review.photo))
        },
        enabled = valid,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Use these details") }
    TextButton(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text("Scan again") }
}
