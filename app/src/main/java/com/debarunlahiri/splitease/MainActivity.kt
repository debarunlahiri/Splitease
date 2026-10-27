package com.debarunlahiri.splitease

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.debarunlahiri.splitease.ui.SplitEaseApp
import com.debarunlahiri.splitease.ui.SplitEaseViewModel
import com.debarunlahiri.splitease.ui.theme.SpliteaseTheme

class MainActivity : ComponentActivity() {
    private val model: SplitEaseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model.receiveInvite(intent.data)
        model.restore(this)
        if (intent.hasExtra("notificationId")) model.notificationTap()
        setContent { SpliteaseTheme { SplitEaseApp(model, this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        model.receiveInvite(intent.data)
        if (intent.hasExtra("notificationId")) model.notificationTap()
    }
}
