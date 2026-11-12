package com.lambrk.splitease.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.lambrk.splitease.ui.util.money
import java.io.File

/** The `upi://pay` link every UPI app understands; the QR code is just this link. */
internal fun upiPayUri(upiId: String, payeeName: String, amount: String, note: String): String =
    Uri.Builder().scheme("upi").authority("pay")
        .appendQueryParameter("pa", upiId)
        .appendQueryParameter("pn", payeeName)
        .appendQueryParameter("am", amount)
        .appendQueryParameter("cu", "INR")
        .appendQueryParameter("tn", note.take(50))
        .build().toString()

internal fun qrBitmap(content: String, size: Int = 768): Bitmap {
    val matrix = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
    )
    val pixels = IntArray(size * size) { i ->
        if (matrix[i % size, i / size]) AndroidColor.BLACK else AndroidColor.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

/**
 * "Scan to settle": a QR code with the payer's UPI ID and the amount, so friends can pay from any
 * UPI app. When everyone owes the same, one code works for all; otherwise pick a person.
 */
@Composable
internal fun UpiQrDialog(
    upiId: String,
    payeeName: String,
    note: String,
    entries: List<Pair<String, String>>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(0) }
    val same = entries.map { it.second }.distinct().size <= 1
    val (person, amount) = entries.getOrElse(selected) { "" to "0" }
    val uri = remember(upiId, payeeName, amount, note) { upiPayUri(upiId, payeeName, amount, note) }
    val bitmap = remember(uri) { qrBitmap(uri) }

    fun share() {
        val file = File(context.cacheDir, "expense-attachments/qr/upi-qr.png").also { it.parentFile?.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val image = FileProvider.getUriForFile(context, "${context.packageName}.attachments", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, image)
            .putExtra(Intent.EXTRA_TEXT, "Pay ${money(amount, "INR")} to $payeeName on UPI ($upiId) for \"$note\".")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share payment QR"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan to settle", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (same) "Everyone owes the same, one code works for all" else "Pick who is paying",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!same) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        entries.forEachIndexed { i, (name, amt) ->
                            FilterChip(selected = i == selected, onClick = { selected = i }, label = { Text("$name · ${money(amt, "INR")}") })
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(16.dp), color = androidx.compose.ui.graphics.Color.White) {
                    Image(bitmap.asImageBitmap(), "UPI payment QR code", Modifier.size(240.dp).padding(8.dp))
                }
                Text(money(amount, "INR"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("$payeeName · $upiId", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Hold up your phone and let them scan with any UPI app.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = ::share, Modifier.fillMaxWidth()) { Text("Share QR") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
