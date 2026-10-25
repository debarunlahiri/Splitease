package com.debarunlahiri.splitease.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineDatabaseTest {
    private lateinit var database: SplitEaseDatabase
    private lateinit var dao: OfflineDao

    @Before
    fun prepare() {
        database =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    SplitEaseDatabase::class.java,
                )
                .build()
        dao = database.offlineDao()
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun pendingWritesKeepTheirStableOperationIdAndPayload() = runBlocking {
        val original = PendingExpense("stable-id", "account-a", "group-a", "{\"amount\":25}")
        dao.enqueue(original)
        assertEquals(original, dao.pending("account-a").single())
        assertTrue(runCatching { dao.enqueue(original) }.isFailure)
        assertEquals("stable-id", dao.pending("account-a").single().operationId)
    }

    @Test
    fun failedWritesRemainVisibleButAreNotAutomaticallyReposted() = runBlocking {
        dao.enqueue(PendingExpense("id", "account-a", "group-a", "{}"))
        dao.state("account-a", "id", "FAILED")
        assertTrue(dao.pending("account-a").isEmpty())
        assertEquals("FAILED", dao.observeExpenses("account-a").first().single().state)
        dao.retry("account-a", "id")
        assertEquals("id", dao.pending("account-a").single().operationId)
    }

    @Test
    fun syncedExpenseKeepsServerIdentityAndLeavesThePendingQueue() = runBlocking {
        dao.enqueue(PendingExpense("local-id", "account-a", "group-a", "{}"))
        dao.synced("account-a", "local-id", "{\"id\":\"server-id\"}")
        assertTrue(dao.pending("account-a").isEmpty())
        val synced = dao.observeExpenses("account-a").first().single()
        assertEquals("SYNCED", synced.state)
        assertTrue(synced.serverJson!!.contains("server-id"))
    }

    @Test
    fun cacheAndOutboxAreIsolatedBetweenAccounts() = runBlocking {
        dao.enqueue(PendingExpense("a-id", "account-a", "group-a", "{}"))
        dao.cache(CachedSnapshot("account-a", "home", "{\"private\":true}"))
        assertTrue(dao.pending("account-b").isEmpty())
        assertTrue(dao.observeExpenses("account-b").first().isEmpty())
        assertNull(dao.snapshot("account-b", "home"))
    }

    @Test
    fun snapshotCleanupDoesNotRemovePendingOrNewerSyncs() = runBlocking {
        dao.enqueue(PendingExpense("pending", "account-a", "group-a", "{}"))
        dao.enqueue(PendingExpense("older", "account-a", "group-a", "{}"))
        dao.enqueue(PendingExpense("newer", "account-a", "group-a", "{}"))
        dao.synced("account-a", "older", "{\"id\":\"old-server\"}", 100)
        dao.synced("account-a", "newer", "{\"id\":\"new-server\"}", 300)
        dao.pruneSynced("account-a", "group-a", 200)
        assertEquals(
            setOf("pending", "newer"),
            dao.observeExpenses("account-a").first().map { it.operationId }.toSet(),
        )
    }

    @Test
    fun signOutDeactivatesIdentityWithoutDeletingUnsyncedExpenses() = runBlocking {
        dao.saveAccount(LocalAccount("account-a", "firebase-a", "http://server", "user-a", "{}"))
        dao.enqueue(PendingExpense("id", "account-a", "group-a", "{}"))
        dao.deactivate("account-a")
        assertNull(dao.activeAccount("firebase-a", "http://server"))
        assertEquals(1, dao.pending("account-a").size)
    }
}
