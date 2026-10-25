package com.debarunlahiri.splitease.ui.errors

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.debarunlahiri.splitease.data.ApiException
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
            is IOException -> "Unable to connect. Check your connection and try again."
            is GetCredentialException,
            is FirebaseAuthException,
            is GoogleApiException -> "We couldn't sign you in. Please try again."
            else -> "Something went wrong. Please try again."
        }
    }

    private fun apiMessage(status: Int): String =
        when (status) {
            400,
            422 -> "Please check your details and try again."
            401 -> "Please sign in again to continue."
            403 -> "You don't have permission to do that."
            404 -> "This item is no longer available. Please refresh."
            408,
            504 -> "This is taking longer than expected. Please try again."
            409 -> "We couldn't complete this action. Please refresh and try again."
            429 -> "Too many attempts. Please wait a moment and try again."
            in 500..599 -> "This service is temporarily unavailable. Please try again later."
            else -> "We couldn't complete this action. Please try again."
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
