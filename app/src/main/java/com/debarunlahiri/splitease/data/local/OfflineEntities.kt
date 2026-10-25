package com.debarunlahiri.splitease.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_accounts",
    indices = [Index(value = ["firebaseUid", "serverBase"])],
)
data class LocalAccount(
    @PrimaryKey val accountKey: String,
    val firebaseUid: String,
    val serverBase: String,
    val backendUserId: String,
    val userJson: String,
    val active: Boolean = true,
    val signedInAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "cached_snapshots", primaryKeys = ["accountKey", "snapshotKey"])
data class CachedSnapshot(
    val accountKey: String,
    val snapshotKey: String,
    val json: String,
    val savedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "expense_outbox",
    indices = [Index(value = ["accountKey", "state", "createdAt"])],
)
data class PendingExpense(
    @PrimaryKey val operationId: String,
    val accountKey: String,
    val groupId: String,
    val payloadJson: String,
    val state: String = "PENDING",
    val serverJson: String? = null,
    val syncedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
