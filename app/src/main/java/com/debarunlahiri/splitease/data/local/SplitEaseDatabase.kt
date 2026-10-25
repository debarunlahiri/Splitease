package com.debarunlahiri.splitease.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LocalAccount::class, CachedSnapshot::class, PendingExpense::class],
    version = 2,
    exportSchema = true,
)
abstract class SplitEaseDatabase : RoomDatabase() {
    abstract fun offlineDao(): OfflineDao

    companion object {
        // Version 1 shipped both before and after syncedAt was introduced. Accept both
        // layouts and let Room validate the complete version 2 schema after migration.
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    val hasSyncedAt =
                        db.query("PRAGMA table_info(`expense_outbox`)").use { cursor ->
                            val nameColumn = cursor.getColumnIndexOrThrow("name")
                            var found = false
                            while (cursor.moveToNext()) {
                                if (cursor.getString(nameColumn) == "syncedAt") found = true
                            }
                            found
                        }
                    if (!hasSyncedAt) {
                        db.execSQL("ALTER TABLE `expense_outbox` ADD COLUMN `syncedAt` INTEGER")
                    }
                    // Previously synced shadows can be reconciled by the next authoritative fetch.
                    db.execSQL(
                        "UPDATE `expense_outbox` SET `syncedAt` = 0 WHERE `state` = 'SYNCED' AND `syncedAt` IS NULL"
                    )
                }
            }

        @Volatile private var instance: SplitEaseDatabase? = null

        fun get(context: Context): SplitEaseDatabase =
            instance
                ?: synchronized(this) {
                    instance
                        ?: Room.databaseBuilder(
                                context.applicationContext,
                                SplitEaseDatabase::class.java,
                                "splitease.db",
                            )
                            .addMigrations(MIGRATION_1_2)
                            .build()
                            .also { instance = it }
                }
    }
}
