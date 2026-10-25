package com.debarunlahiri.splitease.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.withTransaction
import com.debarunlahiri.splitease.data.ApiClient
import com.debarunlahiri.splitease.data.json
import com.debarunlahiri.splitease.data.local.CachedSnapshot
import com.debarunlahiri.splitease.data.local.LocalAccount
import com.debarunlahiri.splitease.data.local.PendingExpense
import com.debarunlahiri.splitease.data.local.SplitEaseDatabase
import java.util.UUID
import org.json.JSONObject

class OfflineStore(context: Context) {
    private val database = SplitEaseDatabase.get(context)
    val dao = database.offlineDao()

    suspend fun saveAccount(firebaseUid: String, user: JSONObject): LocalAccount {
        val id = user.getString("id")
        val account =
            LocalAccount(
                ApiClient.serverBase() + "|" + id,
                firebaseUid,
                ApiClient.serverBase(),
                id,
                user.toString(),
            )
        database.withTransaction {
            dao.deactivateIdentity(firebaseUid, ApiClient.serverBase())
            dao.saveAccount(account)
            dao.resumeAfterLogin(account.accountKey)
        }
        return account
    }

    suspend fun cache(account: String, key: String, data: JSONObject) {
        dao.cache(CachedSnapshot(account, key, data.toString()))
    }

    suspend fun cacheGroup(account: String, group: String, data: JSONObject, fetchedAfter: Long) {
        database.withTransaction {
            dao.cache(CachedSnapshot(account, "group:$group", data.toString()))
            // Once an authoritative snapshot includes earlier syncs, it replaces local shadows.
            dao.pruneSynced(account, group, fetchedAfter)
        }
    }

    suspend fun cached(account: String, key: String): JSONObject? =
        dao.snapshot(account, key)?.let { JSONObject(it.json) }

    suspend fun queue(account: String, payload: JSONObject): PendingExpense {
        val operation =
            PendingExpense(
                UUID.randomUUID().toString(),
                account,
                payload.getString("groupId"),
                payload.toString(),
            )
        dao.enqueue(operation)
        return operation
    }

    companion object {
        fun networkAvailable(context: Context): Boolean {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        fun displayExpense(row: PendingExpense): JSONObject {
            val expense =
                row.serverJson?.let(::JSONObject)
                    ?: JSONObject(row.payloadJson).apply {
                        put("id", "local:" + row.operationId)
                    }
            return expense.put("_syncState", row.state).put("_operationId", row.operationId)
        }
    }
}
