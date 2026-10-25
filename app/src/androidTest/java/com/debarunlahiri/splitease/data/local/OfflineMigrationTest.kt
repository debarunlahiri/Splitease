package com.debarunlahiri.splitease.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineMigrationTest {
    @Test fun originalVersionOnePreservesPendingExpense() = verifyMigration(false)

    @Test fun revisedVersionOnePreservesPendingExpense() = verifyMigration(true)

    private fun verifyMigration(withSyncedAt: Boolean) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-" + UUID.randomUUID() + ".db"
        val callback =
            object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `local_accounts` (`accountKey` TEXT NOT NULL, `firebaseUid` TEXT NOT NULL, `serverBase` TEXT NOT NULL, `backendUserId` TEXT NOT NULL, `userJson` TEXT NOT NULL, `active` INTEGER NOT NULL, `signedInAt` INTEGER NOT NULL, PRIMARY KEY(`accountKey`))"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_local_accounts_firebaseUid_serverBase` ON `local_accounts` (`firebaseUid`, `serverBase`)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `cached_snapshots` (`accountKey` TEXT NOT NULL, `snapshotKey` TEXT NOT NULL, `json` TEXT NOT NULL, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`accountKey`, `snapshotKey`))"
                    )
                    db.execSQL(
                        ("CREATE TABLE IF NOT EXISTS `expense_outbox` (`operationId` TEXT NOT NULL, `accountKey` TEXT NOT NULL, `groupId` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `state` TEXT NOT NULL, `serverJson` TEXT, `syncedAt` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`operationId`))")
                            .let {
                                if (withSyncedAt) it else it.replace("`syncedAt` INTEGER, ", "")
                            }
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_expense_outbox_accountKey_state_createdAt` ON `expense_outbox` (`accountKey`, `state`, `createdAt`)"
                    )
                    db.execSQL(
                        "INSERT INTO expense_outbox (operationId, accountKey, groupId, payloadJson, state, createdAt) VALUES ('pending-id', 'account', 'group', '{}', 'PENDING', 1)"
                    )
                    db.execSQL(
                        "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
                    )
                    db.execSQL(
                        "INSERT INTO room_master_table VALUES (42, '877ff8f75607c3e896a2f559d0fd3bb7')"
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            }
        val helper =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(name)
                        .callback(callback)
                        .build()
                )
        try {
            helper.writableDatabase
            helper.close()
            val migrated =
                Room.databaseBuilder(context, SplitEaseDatabase::class.java, name)
                    .addMigrations(SplitEaseDatabase.MIGRATION_1_2)
                    .build()
            try {
                val pending = migrated.offlineDao().pending("account").single()
                assertEquals("pending-id", pending.operationId)
                assertEquals("{}", pending.payloadJson)
                assertNull(pending.syncedAt)
            } finally {
                migrated.close()
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
