package com.debarunlahiri.splitease.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineDao {
    @Query("UPDATE local_accounts SET active = 0 WHERE firebaseUid = :uid AND serverBase = :server")
    suspend fun deactivateIdentity(uid: String, server: String)

    @Upsert suspend fun saveAccount(account: LocalAccount)

    @Query(
        "SELECT * FROM local_accounts WHERE firebaseUid = :uid AND serverBase = :server AND active = 1 ORDER BY signedInAt DESC LIMIT 1"
    )
    suspend fun activeAccount(uid: String, server: String): LocalAccount?

    @Query(
        "SELECT * FROM local_accounts WHERE serverBase = :server AND active = 1 ORDER BY signedInAt DESC LIMIT 1"
    )
    suspend fun lastActiveAccount(server: String): LocalAccount?

    @Query("SELECT * FROM local_accounts WHERE accountKey = :key LIMIT 1")
    suspend fun account(key: String): LocalAccount?

    @Query("UPDATE local_accounts SET active = 0 WHERE accountKey = :key")
    suspend fun deactivate(key: String)

    @Upsert suspend fun cache(snapshot: CachedSnapshot)

    @Query(
        "SELECT * FROM cached_snapshots WHERE accountKey = :account AND snapshotKey = :key LIMIT 1"
    )
    suspend fun snapshot(account: String, key: String): CachedSnapshot?

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun enqueue(expense: PendingExpense)

    @Query("SELECT * FROM expense_outbox WHERE accountKey = :account ORDER BY createdAt DESC")
    fun observeExpenses(account: String): Flow<List<PendingExpense>>

    @Query(
        "SELECT * FROM expense_outbox WHERE accountKey = :account AND state = 'PENDING' ORDER BY createdAt, operationId"
    )
    suspend fun pending(account: String): List<PendingExpense>

    @Query(
        "UPDATE expense_outbox SET state = :state WHERE operationId = :id AND accountKey = :account"
    )
    suspend fun state(account: String, id: String, state: String)

    @Query(
        "UPDATE expense_outbox SET state = 'SYNCED', serverJson = :response, syncedAt = :completedAt WHERE operationId = :id AND accountKey = :account"
    )
    suspend fun synced(
        account: String,
        id: String,
        response: String,
        completedAt: Long = System.currentTimeMillis(),
    )

    @Query(
        "UPDATE expense_outbox SET state = 'PENDING' WHERE accountKey = :account AND state = 'AUTH_REQUIRED'"
    )
    suspend fun resumeAfterLogin(account: String)

    @Query(
        "UPDATE expense_outbox SET state = 'PENDING' WHERE accountKey = :account AND operationId = :id AND state IN ('FAILED', 'AUTH_REQUIRED')"
    )
    suspend fun retry(account: String, id: String)

    @Query(
        "DELETE FROM expense_outbox WHERE accountKey = :account AND groupId = :group AND state = 'SYNCED' AND syncedAt <= :fetchedAfter"
    )
    suspend fun pruneSynced(account: String, group: String, fetchedAfter: Long)

    @Query(
        "DELETE FROM expense_outbox WHERE accountKey = :account AND operationId = :id AND state = 'SYNCED'"
    )
    suspend fun removeSynced(account: String, id: String)
}
