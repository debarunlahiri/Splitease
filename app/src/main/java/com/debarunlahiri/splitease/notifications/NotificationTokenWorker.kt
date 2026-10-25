package com.debarunlahiri.splitease.notifications

import android.content.Context
import androidx.work.*
import com.debarunlahiri.splitease.data.ApiClient
import com.debarunlahiri.splitease.data.ApiException
import com.debarunlahiri.splitease.data.json
import com.debarunlahiri.splitease.data.local.SplitEaseDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class NotificationTokenWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()
        val account =
            SplitEaseDatabase.get(applicationContext)
                .offlineDao()
                .activeAccount(user.uid, ApiClient.serverBase()) ?: return Result.success()
        val api = ApiClient()
        var refresh: String? = null
        return try {
            val credential = user.getIdToken(false).await().token ?: return Result.failure()
            val session =
                JSONObject(api.request("auth/firebase", "POST", json("idToken" to credential)))
            refresh = session.getString("refreshToken")
            if (session.getJSONObject("user").getString("id") != account.backendUserId)
                return Result.failure()
            api.accessToken = session.getString("accessToken")
            api.requestUserId = account.backendUserId
            val preferences = api.objectAt("notifications/preferences")
            if (
                preferences.optBoolean("pushEnabled") &&
                    FirebaseAuth.getInstance().currentUser?.uid == account.firebaseUid
            ) {
                val token = FirebaseMessaging.getInstance().token.await()
                if (preferences.optString("pushToken") != token) {
                    val payload =
                        json(
                            "inboxEnabled" to preferences.optBoolean("inboxEnabled", true),
                            "pushEnabled" to true,
                            "pushToken" to token,
                        )
                    val key =
                        java.util.UUID.nameUUIDFromBytes(
                                (id.toString() + payload.toString()).toByteArray()
                            )
                            .toString()
                    api.request("notifications/preferences", "PUT", payload, idempotencyKey = key)
                }
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ApiException) {
            if (failure.status in listOf(400, 401, 403)) Result.failure() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            refresh?.let { token ->
                try {
                    api.request(
                        "auth/logout",
                        "POST",
                        json("refreshToken" to token),
                        timeoutSeconds = 8,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {}
            }
        }
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "notification-token-refresh",
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<NotificationTokenWorker>()
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        )
                        .build(),
                )
        }
    }
}
