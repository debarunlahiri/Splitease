package com.debarunlahiri.splitease.auth

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.debarunlahiri.splitease.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

object GoogleLogin {
    suspend fun signIn(activity: Activity): String {
        check(FirebaseApp.getApps(activity).isNotEmpty()) {
            "Add your Firebase google-services.json to the app folder to enable Google sign-in."
        }
        val resourceId =
            activity.resources.getIdentifier(
                "default_web_client_id",
                "string",
                activity.packageName,
            )
        val clientId =
            BuildConfig.GOOGLE_WEB_CLIENT_ID.ifBlank {
                if (resourceId != 0) activity.getString(resourceId) else ""
            }
        check(clientId.isNotBlank()) { "Configure your Google web client ID in Firebase." }
        val option = GetSignInWithGoogleOption.Builder(clientId).build()
        val result =
            CredentialManager.create(activity)
                .getCredential(
                    activity,
                    GetCredentialRequest.Builder().addCredentialOption(option).build(),
                )
        val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
        val user =
            FirebaseAuth.getInstance()
                .signInWithCredential(GoogleAuthProvider.getCredential(credential.idToken, null))
                .await()
                .user ?: error("Google sign-in did not return a user.")
        return user.getIdToken(true).await().token ?: error("Unable to verify your sign-in.")
    }

    suspend fun restore(activity: Activity): String? {
        if (FirebaseApp.getApps(activity).isEmpty()) return null
        return FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()?.token
    }

    suspend fun signOut(activity: Activity) {
        if (FirebaseApp.getApps(activity).isNotEmpty()) FirebaseAuth.getInstance().signOut()
        CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
    }
}
