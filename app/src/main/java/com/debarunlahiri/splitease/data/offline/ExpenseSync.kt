package com.debarunlahiri.splitease.data.offline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.debarunlahiri.splitease.data.ApiClient
import com.debarunlahiri.splitease.data.ApiException
import com.debarunlahiri.splitease.data.json
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

object ExpenseSync {
    private val mutex = Mutex()

    enum class Outcome {
        COMPLETE,
        RETRY,
        SIGN_IN_REQUIRED,
    }

    fun schedule(context: Context, account: String) {
        val request =
            OneTimeWorkRequestBuilder<ExpenseSyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf("accountKey" to account))
                .addTag("expense-sync")
                .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "expense-sync:$account",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        val recovery =
            PeriodicWorkRequestBuilder<ExpenseSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setInputData(workDataOf("accountKey" to account))
                .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "expense-recovery:$account",
                ExistingPeriodicWorkPolicy.KEEP,
                recovery,
            )
    }

    fun cancel(context: Context, account: String) {
        WorkManager.getInstance(context).cancelUniqueWork("expense-sync:$account")
        WorkManager.getInstance(context).cancelUniqueWork("expense-recovery:$account")
    }

    suspend fun synchronize(context: Context, accountKey: String): Outcome = mutex.withLock {
        val store = OfflineStore(context)
        val pending = store.dao.pending(accountKey)
        if (pending.isEmpty()) return@withLock Outcome.COMPLETE
        val account = store.dao.account(accountKey) ?: return@withLock Outcome.SIGN_IN_REQUIRED
        if (!account.active || account.serverBase != ApiClient.serverBase())
            return@withLock Outcome.SIGN_IN_REQUIRED
        if (FirebaseApp.getApps(context).isEmpty()) return@withLock Outcome.SIGN_IN_REQUIRED
        val firebase =
            FirebaseAuth.getInstance().currentUser ?: return@withLock Outcome.SIGN_IN_REQUIRED
        if (firebase.uid != account.firebaseUid) return@withLock Outcome.SIGN_IN_REQUIRED
        val api = ApiClient()
        try {
            val token =
                firebase.getIdToken(false).await().token ?: return@withLock Outcome.SIGN_IN_REQUIRED
            val session = JSONObject(api.request("auth/firebase", "POST", json("idToken" to token)))
            if (session.getJSONObject("user").getString("id") != account.backendUserId)
                return@withLock Outcome.SIGN_IN_REQUIRED
            api.accessToken = session.getString("accessToken")
            api.requestUserId = account.backendUserId
            try {
                for (row in pending) {
                    if (
                        FirebaseAuth.getInstance().currentUser?.uid != account.firebaseUid ||
                            store.dao.account(accountKey)?.active != true
                    ) {
                        return@withLock Outcome.SIGN_IN_REQUIRED
                    }
                    try {
                        val response =
                            api.request(
                                "expenses",
                                "POST",
                                JSONObject(row.payloadJson),
                                idempotencyKey = row.operationId,
                            )
                        // Validate the response before marking the operation complete. A crash
                        // reuses the key.
                        JSONObject(response).getString("id")
                        store.dao.synced(accountKey, row.operationId, response)
                    } catch (failure: ApiException) {
                        when {
                            failure.status == 401 -> {
                                store.dao.state(accountKey, row.operationId, "AUTH_REQUIRED")
                                return@withLock Outcome.SIGN_IN_REQUIRED
                            }
                            failure.status in setOf(408, 409, 429) || failure.status >= 500 ->
                                return@withLock Outcome.RETRY
                            else -> store.dao.state(accountKey, row.operationId, "FAILED")
                        }
                    }
                }
            } finally {
                // Exchange sessions belong only to this sync attempt, not the foreground app.
                try {
                    api.request(
                        "auth/logout",
                        "POST",
                        json("refreshToken" to session.getString("refreshToken")),
                        timeoutSeconds = 8,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    /* Server-side expiry remains the fallback. */
                }
            }
            Outcome.COMPLETE
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ApiException) {
            if (failure.status == 401 || failure.status == 403) {
                pending.forEach { store.dao.state(accountKey, it.operationId, "AUTH_REQUIRED") }
                Outcome.SIGN_IN_REQUIRED
            } else Outcome.RETRY
        } catch (_: IOException) {
            Outcome.RETRY
        } catch (_: com.google.firebase.auth.FirebaseAuthException) {
            pending.forEach { store.dao.state(accountKey, it.operationId, "AUTH_REQUIRED") }
            Outcome.SIGN_IN_REQUIRED
        } catch (_: com.google.firebase.FirebaseNetworkException) {
            Outcome.RETRY
        } catch (_: org.json.JSONException) {
            Outcome.RETRY
        }
    }
}
