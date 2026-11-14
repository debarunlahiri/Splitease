package com.lambrk.splitease.ui.errors

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.lambrk.splitease.data.ApiException
import com.google.android.gms.common.api.ApiException as GoogleApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import java.io.IOException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/** Only application-authored validation copy can pass through to the UI. */
class UserInputException(val userMessage: String) : IllegalArgumentException()

@OptIn(ExperimentalContracts::class)
inline fun validateInput(condition: Boolean, message: () -> String) {
    contract { returns() implies condition }
    if (!condition) throw UserInputException(message())
}

object UserFacingError {
    /** Never return exception messages, server response text, SDK codes or stack traces. */
    fun message(failure: Throwable): String? {
        if (isSignInCancellation(failure)) return null
        return when (failure) {
            is UserInputException -> failure.userMessage
            is ApiException -> apiMessage(failure.status)
            is FirebaseNetworkException,
            is IOException -> "Can't reach SplitEase. Check your internet and try again."
            is GetCredentialException,
            is FirebaseAuthException,
            is GoogleApiException -> "Couldn't sign you in. Please try again."
            else -> "Something went wrong. Please try again."
        }
    }

    private fun apiMessage(status: Int): String =
        when (status) {
            400,
            422 -> "Something doesn't look right. Please check what you entered and try again."
            401 -> "Your session has ended. Please sign in again."
            403 -> "You can't do that here. Ask the person who created it to help."
            404 -> "We couldn't find that. It may have been removed, so try refreshing."
            408,
            504 -> "That's taking a while. Check your internet and try again."
            409 -> "This changed while you were looking. Refresh and try again."
            429 -> "You're going a little fast. Wait a moment and try again."
            in 500..599 -> "Something went wrong on our side. Please try again in a few minutes."
            else -> "Something went wrong. Please try again."
        }

    private fun isSignInCancellation(failure: Throwable): Boolean {
        var current: Throwable? = failure
        repeat(8) {
            val cause = current ?: return false
            if (
                cause is GetCredentialCancellationException ||
                    (cause is GoogleApiException && cause.statusCode == CommonStatusCodes.CANCELED)
            ) {
                return true
            }
            current = cause.cause
        }
        return false
    }
}
