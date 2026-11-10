package com.lambrk.splitease.update

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lambrk.splitease.data.ApiClient
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** A failure whose message is safe to show to the user. */
private class UpdateException(message: String) : Exception(message)

/**
 * Self-hosted app updates, without Google Play. The backend says which version is the latest
 * and where its APK is; this class offers the update, downloads and verifies the APK, and
 * hands it to Android's installer.
 */
class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    data class Available(
        val versionCode: Long,
        val versionName: String,
        val notes: String,
        val apkUrl: String,
        val sha256: String?,
        /** The installed version is too old to keep working. */
        val required: Boolean,
    )

    enum class Phase {
        Idle,
        Downloading,
        /** Downloaded and handed to the installer. */
        Ready,
        /** Android needs the user to allow installs from this app first. */
        NeedsPermission,
        Failed,
    }

    private val context = application.applicationContext
    private val preferences = context.getSharedPreferences("app_update", Context.MODE_PRIVATE)
    private val http =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

    /** The update on offer, or null when the app is up to date. */
    var available by mutableStateOf<Available?>(null)
        private set

    var phase by mutableStateOf(Phase.Idle)
        private set

    /** 0..1 while downloading, or -1 when the size is unknown. */
    var progress by mutableFloatStateOf(-1f)
        private set

    var failure by mutableStateOf<String?>(null)
        private set

    private var downloaded: File? = null
    private var checking = false

    /** Asks the server for the latest version, at most every [CHECK_INTERVAL_MS]. */
    fun check() {
        if (checking || available != null || phase == Phase.Downloading) return
        val now = System.currentTimeMillis()
        if (now - preferences.getLong(LAST_CHECK, 0) < CHECK_INTERVAL_MS) return
        checking = true
        viewModelScope.launch {
            try {
                val info = fetchLatest() ?: return@launch
                preferences.edit().putLong(LAST_CHECK, now).apply()
                val installed = installedVersionCode()
                if (info.versionCode <= installed) return@launch
                val required = installed < info.minSupported
                // "Later" is remembered per version, except for updates that are required.
                if (!required && preferences.getLong(DISMISSED, 0) == info.versionCode) return@launch
                available =
                    Available(
                        versionCode = info.versionCode,
                        versionName = info.versionName,
                        notes = info.notes,
                        apkUrl = info.apkUrl,
                        sha256 = info.sha256,
                        required = required,
                    )
                phase = Phase.Idle
                failure = null
            } finally {
                checking = false
            }
        }
    }

    /** Call when the app returns to the foreground. */
    fun onResume() {
        val file = downloaded
        if (phase == Phase.NeedsPermission && file != null && canInstall()) install(file)
        check()
    }

    /** "Later". Required updates and running downloads cannot be dismissed. */
    fun dismiss() {
        val update = available ?: return
        if (update.required || phase == Phase.Downloading) return
        preferences.edit().putLong(DISMISSED, update.versionCode).apply()
        available = null
        phase = Phase.Idle
        failure = null
    }

    fun startUpdate() {
        val update = available ?: return
        if (phase == Phase.Downloading) return
        downloaded?.takeIf { it.exists() }?.let {
            install(it)
            return
        }
        phase = Phase.Downloading
        progress = -1f
        failure = null
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { download(update) }
                downloaded = file
                install(file)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w(TAG, "Update download failed: ${exception.javaClass.simpleName}")
                failure =
                    (exception as? UpdateException)?.message
                        ?: "The update could not be downloaded. Check your connection and try again."
                phase = Phase.Failed
            }
        }
    }

    /** Opens the Android screen where the user allows this app to install updates. */
    fun openInstallSettings() {
        val intent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            failure = "Open Android Settings and allow SplitEase to install apps."
            phase = Phase.Failed
        }
    }

    // ---------------------------------------------------------------- internals

    private class Latest(
        val versionCode: Long,
        val versionName: String,
        val minSupported: Long,
        val notes: String,
        val apkUrl: String,
        val sha256: String?,
    )

    /** Null when the server cannot be reached or has no update published. */
    private suspend fun fetchLatest(): Latest? =
        try {
            val json = JSONObject(ApiClient().request("app/version", timeoutSeconds = 10))
            val code = json.optLong("latestVersionCode", 0)
            val url = if (json.isNull("apkUrl")) "" else json.optString("apkUrl")
            if (code <= 0 || url.toHttpUrlOrNull() == null) null
            else
                Latest(
                    versionCode = code,
                    versionName = json.optString("latestVersionName"),
                    minSupported = json.optLong("minSupportedVersionCode", 0),
                    notes = json.optString("releaseNotes").trim(),
                    apkUrl = url,
                    sha256 =
                        if (json.isNull("apkSha256")) null
                        else json.optString("apkSha256").lowercase().ifBlank { null },
                )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // Being offline or the server being down must never get in the user's way.
            Log.w(TAG, "Update check failed: ${exception.javaClass.simpleName}")
            null
        }

    private fun installedVersionCode(): Long =
        PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0)
        )

    /** Downloads the APK, then checks it is the right file before it is ever installed. */
    private fun download(update: Available): File {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val partial = File(directory, "download.part")
        val digest = MessageDigest.getInstance("SHA-256")

        http.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) {
                throw UpdateException("The update could not be downloaded. Please try again later.")
            }
            val total = body.contentLength()
            var received = 0L
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        received += read
                        if (total > 0) progress = received.toFloat() / total
                    }
                }
            }
        }

        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (update.sha256 != null && !update.sha256.equals(actual, ignoreCase = true)) {
            partial.delete()
            throw UpdateException("The downloaded update did not match and was not installed.")
        }
        // It must be this app, at least as new as the version that was announced.
        val archive = context.packageManager.getPackageArchiveInfo(partial.path, 0)
        if (
            archive == null ||
                archive.packageName != context.packageName ||
                PackageInfoCompat.getLongVersionCode(archive) < update.versionCode
        ) {
            partial.delete()
            throw UpdateException("The downloaded file is not a SplitEase update.")
        }
        val target = File(directory, "splitease-${update.versionCode}.apk")
        if (!partial.renameTo(target)) throw IOException("Could not store the update")
        return target
    }

    private fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    private fun install(file: File) {
        if (!canInstall()) {
            phase = Phase.NeedsPermission
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            phase = Phase.Ready
        } catch (_: ActivityNotFoundException) {
            failure = "This phone could not open the installer."
            phase = Phase.Failed
        }
    }

    private companion object {
        const val TAG = "SplitEaseUpdate"
        const val APK_MIME = "application/vnd.android.package-archive"
        const val LAST_CHECK = "last_check"
        const val DISMISSED = "dismissed_version"
        const val CHECK_INTERVAL_MS = 30 * 60 * 1000L
    }
}
