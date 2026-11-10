package com.lambrk.splitease.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal const val MAX_EXPENSE_ATTACHMENTS = 5
private const val MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024

internal data class ExpenseAttachment(val id: String, val name: String, val contentType: String) {
    val isImage: Boolean get() = contentType.startsWith("image/")

    fun toJson() = json("id" to id, "name" to name, "contentType" to contentType)

    companion object {
        fun fromExpense(expense: JSONObject?): List<ExpenseAttachment> =
            expense?.optJSONArray("attachments")?.objects().orEmpty().map {
                ExpenseAttachment(it.getString("id"), it.getString("name"), it.getString("contentType"))
            }
    }
}

internal class AttachmentFiles(private val context: Context, accountKey: String) {
    private val accountDirectory = UUID.nameUUIDFromBytes(accountKey.toByteArray()).toString()
    private val drafts = File(context.filesDir, "expense-drafts/$accountDirectory")
    private val cache = File(context.cacheDir, "expense-attachments/$accountDirectory")

    suspend fun stage(uri: Uri): ExpenseAttachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
        val name = safeName(displayName.orEmpty())
        val guessed = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase(Locale.ROOT))
        val reported = resolver.getType(uri)?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        val mime = reported?.takeIf {
            it.length <= 120 && it != "application/octet-stream" && it.matches(MIME_TYPE)
        }
            ?: guessed ?: "application/octet-stream"
        val attachment = ExpenseAttachment(UUID.randomUUID().toString(), name, mime)
        val file = fileIn(drafts, attachment)
        try {
            file.parentFile?.mkdirs()
            val input = resolver.openInputStream(uri) ?: throw IOException("File is unavailable")
            input.use { source -> file.outputStream().use { copyAttachment(source, it) } }
            attachment
        } catch (failure: Exception) {
            file.delete()
            file.parentFile?.delete()
            throw failure
        }
    }

    suspend fun upload(api: ApiClient, groupId: String, attachments: List<ExpenseAttachment>) {
        for (attachment in attachments) {
            val file = fileIn(drafts, attachment)
            if (file.isFile) api.uploadAttachment(groupId, attachment, file)
        }
    }

    suspend fun open(api: ApiClient, attachment: ExpenseAttachment): File = withContext(Dispatchers.IO) {
        val staged = fileIn(drafts, attachment)
        val target = fileIn(cache, attachment)
        if (target.isFile) return@withContext target
        target.parentFile?.mkdirs()
        val temp = File.createTempFile("download-", ".part", target.parentFile)
        try {
            if (staged.isFile) {
                staged.inputStream().use { source -> temp.outputStream().use { copyAttachment(source, it) } }
            } else {
                api.downloadAttachment(attachment.id, temp)
            }
            if (!temp.renameTo(target) && !target.isFile) throw IOException("Could not cache attachment")
            target
        } finally {
            temp.delete()
        }
    }

    suspend fun discard(attachments: List<ExpenseAttachment>) = withContext(Dispatchers.IO) {
        attachments.forEach {
            val file = fileIn(drafts, it)
            file.delete()
            file.parentFile?.delete()
        }
    }

    private fun fileIn(root: File, attachment: ExpenseAttachment): File =
        File(File(root, UUID.fromString(attachment.id).toString()), safeName(attachment.name))

    companion object {
        private val MIME_TYPE = Regex("[a-z0-9_.+-]+/[a-z0-9_.+-]+")
        private val UNSAFE_NAME = Regex("[\\p{Cntrl}/\\\\]")

        private fun safeName(value: String): String {
            var name = value.replace(UNSAFE_NAME, "_").trim().take(255)
            if (name.isBlank() || name == "." || name == "..") return "Attachment"
            val extension = name.substringAfterLast('.', "").takeIf { it.length in 1..12 }
            val suffix = extension?.let { ".$it" }.orEmpty()
            name = name.removeSuffix(suffix)
            while ((name + suffix).toByteArray().size > 240) {
                name = name.dropLast(Character.charCount(name.codePointBefore(name.length)))
            }
            return name + suffix
        }
    }
}

internal fun copyAttachment(source: InputStream, target: OutputStream) {
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        val count = source.read(buffer)
        if (count == -1) break
        total += count
        if (total > MAX_ATTACHMENT_BYTES) throw IOException("Attachment exceeds 10 MB")
        target.write(buffer, 0, count)
    }
    if (total == 0L) throw IOException("Attachment is empty")
}
