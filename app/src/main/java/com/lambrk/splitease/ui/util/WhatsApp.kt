package com.lambrk.splitease.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lambrk.splitease.ui.i18n.tr

/**
 * Opens WhatsApp with [text] ready to send, from the person's own phone (so it costs nothing and needs no business
 * account). With a phone number it opens that chat; without one WhatsApp asks which chat to use.
 */
internal fun openWhatsApp(context: Context, phoneE164: String?, text: String) {
    // The message goes to someone else, so it stays in the wording it was written in, not the sender's app language.
    val message = text
    val chat = phoneE164?.filter { it.isDigit() }?.takeIf { it.length >= 7 }?.let {
        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$it?text=${Uri.encode(message)}"))
    }
    val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
    val attempts = listOfNotNull(chat, Intent(share).setPackage("com.whatsapp"), Intent.createChooser(share, tr("Send a message")))
    for (intent in attempts) {
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}
