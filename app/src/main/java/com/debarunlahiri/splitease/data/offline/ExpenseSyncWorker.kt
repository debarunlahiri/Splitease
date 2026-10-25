package com.debarunlahiri.splitease.data.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ExpenseSyncWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val account = inputData.getString("accountKey") ?: return Result.failure()
        return try {
            when (ExpenseSync.synchronize(applicationContext, account)) {
                ExpenseSync.Outcome.COMPLETE -> Result.success()
                ExpenseSync.Outcome.RETRY -> Result.retry()
                ExpenseSync.Outcome.SIGN_IN_REQUIRED -> Result.failure()
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
