package com.lambrk.splitease.auth

import android.app.Activity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.tasks.await

/** Why a phone check did not work, in words for the person using the app. */
class PhoneVerificationException(override val message: String) : Exception(message)

/**
 * Proves a phone number is the person's own: Firebase texts a code, and entering it links the number to their
 * signed-in account. The backend then reads the number from Firebase's signed token, so it cannot be faked.
 */
class PhoneVerifier {
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    /** Texts a code to [e164]. [onAutoVerified] fires when the phone reads the SMS by itself. */
    fun sendCode(
        activity: Activity,
        e164: String,
        onCodeSent: () -> Unit,
        onAutoVerified: (PhoneAuthCredential) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (FirebaseApp.getApps(activity).isEmpty()) {
            onError("Phone verification isn't set up yet.")
            return
        }
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) = onAutoVerified(credential)

            override fun onVerificationFailed(error: FirebaseException) = onError(friendly(error))

            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id
                resendToken = token
                onCodeSent()
            }
        }
        val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(e164)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        resendToken?.let { options.setForceResendingToken(it) }
        PhoneAuthProvider.verifyPhoneNumber(options.build())
    }

    /** Checks the code the person typed; returns a fresh Firebase ID token that carries the verified number. */
    suspend fun confirm(code: String): String {
        val id = verificationId ?: throw PhoneVerificationException("Ask for a new code first.")
        return link(PhoneAuthProvider.getCredential(id, code.trim()))
    }

    suspend fun link(credential: PhoneAuthCredential): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw PhoneVerificationException("Please sign in again, then try once more.")
        try {
            user.updatePhoneNumber(credential).await()
            return user.getIdToken(true).await().token
                ?: throw PhoneVerificationException("Couldn't finish. Please try again.")
        } catch (error: PhoneVerificationException) {
            throw error
        } catch (error: Exception) {
            throw PhoneVerificationException(friendly(error))
        }
    }

    private fun friendly(error: Exception): String = when {
        // Firebase refuses to text a country the developer hasn't switched on (Authentication > Settings > SMS region policy).
        error.message?.contains("region", ignoreCase = true) == true && error.message?.contains("SMS", ignoreCase = true) == true ->
            "Codes can't be sent to this country yet. Please try again later."
        else -> friendlyByType(error)
    }

    private fun friendlyByType(error: Exception): String = when (error) {
        is FirebaseAuthInvalidCredentialsException ->
            if (error.errorCode == "ERROR_INVALID_PHONE_NUMBER") "That phone number isn't right."
            else "That code isn't right. Try again."
        is FirebaseTooManyRequestsException -> "Too many tries. Please wait a while and try again."
        is FirebaseAuthUserCollisionException -> "This number is already used on another account."
        else -> "Couldn't verify the number. Check it and try again."
    }
}
