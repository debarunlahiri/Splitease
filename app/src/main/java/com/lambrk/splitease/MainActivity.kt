package com.lambrk.splitease

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.InetAddresses
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.lambrk.splitease.data.ApiClient
import com.lambrk.splitease.ui.SplitEaseApp
import com.lambrk.splitease.ui.SplitEaseViewModel
import com.lambrk.splitease.ui.theme.SpliteaseTheme
import com.lambrk.splitease.ui.theme.ThemeMode
import com.lambrk.splitease.update.AppUpdateViewModel
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class MainActivity : ComponentActivity() {
    private val model: SplitEaseViewModel by viewModels()
    private val updates: AppUpdateViewModel by viewModels()
    private var canConnect by mutableStateOf(false)
    private var notificationPending = false
    private val localNetworkPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            connectIfAllowed()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model.receiveInvite(intent.data)
        notificationPending = intent.hasExtra("notificationId")
        val appearance = getSharedPreferences("appearance", MODE_PRIVATE)
        val savedMode = appearance.getString("theme_mode", null)
        var themeMode by mutableStateOf(
            ThemeMode.entries.firstOrNull { it.name == savedMode } ?: ThemeMode.SYSTEM
        )
        setContent {
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            SpliteaseTheme(darkTheme = darkTheme) {
                if (canConnect) {
                    SplitEaseApp(model, this, updates, themeMode) { mode ->
                        themeMode = mode
                        appearance.edit().putString("theme_mode", mode.name).apply()
                    }
                } else {
                    Surface(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "Allow local network access",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text("SplitEase needs this permission to connect to your server on Wi-Fi.")
                            Button(
                                onClick = {
                                    localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                                },
                            ) { Text("Allow access") }
                            TextButton(
                                onClick = {
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", packageName, null),
                                        )
                                    )
                                },
                            ) { Text("Open app settings") }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        connectIfAllowed()
    }

    private fun connectIfAllowed() {
        canConnect = !needsLocalNetworkPermission()
        if (!canConnect) return

        model.restore(this)
        if (notificationPending) {
            notificationPending = false
            model.notificationTap()
        }
        // Looks for a new version (at most every 30 minutes) and continues an install that was
        // waiting for the user to allow installs.
        updates.onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        model.receiveInvite(intent.data)
        notificationPending = notificationPending || intent.hasExtra("notificationId")
    }

    private fun needsLocalNetworkPermission(): Boolean {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN ||
                checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) ==
                    PackageManager.PERMISSION_GRANTED
        ) return false

        val host = ApiClient.serverBase().toHttpUrlOrNull()?.host ?: return false
        if (host.trimEnd('.').endsWith(".local")) return true

        val address =
            runCatching { InetAddresses.parseNumericAddress(host) }.getOrNull() ?: return false
        return address.isSiteLocalAddress || address.isLinkLocalAddress ||
            (address.address.size == 16 && (address.address[0].toInt() and 0xfe) == 0xfc)
    }
}
