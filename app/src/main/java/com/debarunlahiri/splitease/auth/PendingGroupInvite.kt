package com.debarunlahiri.splitease.auth

import android.content.Context
import android.net.Uri
import com.debarunlahiri.splitease.data.ApiClient

/** Retains an invite across sign-in and process recreation, scoped to this server. */
class PendingGroupInvite(context: Context) {
    private val preferences = context.getSharedPreferences("pending_group_invite", Context.MODE_PRIVATE)
    private val key = ApiClient.serverBase()

    fun capture(uri: Uri?): Boolean {
        if (uri?.scheme != "splitease" || uri.host != "join") return false
        val token = uri.getQueryParameter("token") ?: return false
        if (!token.matches(Regex("[A-Za-z0-9_-]{43}"))) return false
        preferences.edit().putString(key, token).commit()
        return true
    }

    fun token(): String? = preferences.getString(key, null)

    fun clear(token: String) {
        if (token() == token) preferences.edit().remove(key).commit()
    }
}
