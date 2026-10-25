package com.debarunlahiri.splitease.auth

import android.content.Context
import com.debarunlahiri.splitease.data.ApiClient

/** Explicit sign-out state; no passwords or bearer/refresh tokens are stored here. */
class SessionPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("session_state", Context.MODE_PRIVATE)
    private val key
        get() = "signed_out:" + ApiClient.serverBase()

    fun explicitlySignedOut(): Boolean = preferences.getBoolean(key, false)

    fun signedIn() {
        preferences.edit().putBoolean(key, false).commit()
    }

    fun signedOut() {
        preferences.edit().putBoolean(key, true).commit()
    }
}
