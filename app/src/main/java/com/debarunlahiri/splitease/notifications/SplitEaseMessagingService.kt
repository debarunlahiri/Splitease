package com.debarunlahiri.splitease.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.debarunlahiri.splitease.MainActivity
import com.debarunlahiri.splitease.R
import com.debarunlahiri.splitease.data.ApiClient
import com.debarunlahiri.splitease.data.local.SplitEaseDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class SplitEaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        NotificationTokenWorker.schedule(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val account =
            runBlocking(Dispatchers.IO) {
                SplitEaseDatabase.get(applicationContext)
                    .offlineDao()
                    .activeAccount(uid, ApiClient.serverBase())
            } ?: return
        if (message.data["userId"] != account.backendUserId) return
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        )
            return
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            manager.createNotificationChannel(
                NotificationChannel(
                    "group_updates",
                    "Group updates",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        val id = message.data["notificationId"] ?: return
        val intent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = android.net.Uri.parse("splitease://notification/$id")
                putExtra("notificationId", id)
            }
        val tap =
            PendingIntent.getActivity(
                this,
                id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(this, "group_updates")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(message.data["title"] ?: "SplitEase update")
                .setContentText(message.data["message"] ?: "There's a new update in your groups.")
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
        // Stable notification IDs replace a duplicate delivery instead of adding another tray
        // entry.
        manager.notify(id, 0, notification)
    }
}
