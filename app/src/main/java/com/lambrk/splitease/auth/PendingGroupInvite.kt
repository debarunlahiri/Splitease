package com.lambrk.splitease.auth

import android.content.Context
import android.net.Uri
import com.lambrk.splitease.data.ApiClient

/** Retains an invite across sign-in and process recreation, scoped to this server. */
class PendingGroupInvite(context: Context, private val host: String = "join") {
    private val preferences = context.getSharedPreferences("pending_group_invite", Context.MODE_PRIVATE)
    private val key = if (host == "join") ApiClient.serverBase() else "$host:${ApiClient.serverBase()}"

    fun capture(uri: Uri?): Boolean {
        val token = when {
            uri?.scheme == "splitease" && uri.host == host -> uri.getQueryParameter("token")
            host == "friend" && uri?.scheme == "https" &&
                uri.host == "splitease.lambrk.com" && uri.path == "/friend" -> uri.fragment
            else -> null
        } ?: return false
        if (!token.matches(Regex("[A-Za-z0-9_-]{43}"))) return false
        preferences.edit().putString(key, token).commit()
        return true
    }

    fun token(): String? = preferences.getString(key, null)

    fun clear(token: String) {
        if (token() == token) preferences.edit().remove(key).commit()
    }
}
