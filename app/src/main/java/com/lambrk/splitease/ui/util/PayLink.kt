package com.lambrk.splitease.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** The details a pay link shows: what, how much, who to pay and who owes what. */
internal fun payPayload(
    description: String,
    currency: String,
    payerName: String,
    payerUpi: String?,
    people: List<Pair<String, String>>,
    payBy: String?,
    /** Each person's own due date by name, when they have one. */
    dues: Map<String, String?> = emptyMap(),
    /** Lets friends who open the link tell you they paid: the group, the expense, and who each name is. */
    groupId: String? = null,
    expenseId: String? = null,
    userIds: Map<String, String> = emptyMap(),
): JSONObject {
    val payload = JSONObject()
        .put("v", 1)
        .put("d", description.take(160))
        .put("c", currency)
        .put("n", payerName.take(100))
        .put(
            "t",
            JSONArray(people.map { person ->
                JSONObject().put("n", person.first.take(100)).put("a", person.second).also { json ->
                    dues[person.first]?.takeIf { it.isNotBlank() }?.let { json.put("d", it.take(10)) }
                    userIds[person.first]?.let { json.put("u", it) }
                }
            }),
        )
    if (currency == "INR" && !payerUpi.isNullOrBlank()) payload.put("u", payerUpi)
    if (!payBy.isNullOrBlank()) payload.put("b", payBy)
    if (groupId != null && userIds.isNotEmpty()) {
        payload.put("g", groupId)
        expenseId?.let { payload.put("x", it) }
    }
    return payload
}

/**
 * Fallback when the server can't be reached: the whole split inside the link itself (long, and it
 * shows the details in the URL). The normal path is a short link created by the backend.
 */
internal fun payLink(webBase: String, payload: JSONObject): String {
    val encoded = Base64.encodeToString(
        payload.toString().toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )
    return "${webBase.trimEnd('/')}/pay#$encoded"
}

/** Opens the user's UPI app with the payment filled in. Returns false when no UPI app is installed. */
internal fun openUpiPayment(context: Context, upiId: String, payeeName: String, amount: String, note: String): Boolean {
    val uri = Uri.Builder().scheme("upi").authority("pay")
        .appendQueryParameter("pa", upiId)
        .appendQueryParameter("pn", payeeName)
        .appendQueryParameter("am", amount)
        .appendQueryParameter("cu", "INR")
        .appendQueryParameter("tn", note.take(50))
        .build()
    return try {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Pay with"))
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * Opens a UPI app on a payment to [upiId] with no amount. The app shows the name registered to that ID, which is how the
 * owner checks it is really theirs; nothing is sent unless they enter an amount and confirm.
 */
internal fun openUpiVerification(context: Context, upiId: String, name: String): Boolean {
    val uri = Uri.Builder().scheme("upi").authority("pay")
        .appendQueryParameter("pa", upiId)
        .appendQueryParameter("pn", name)
        .appendQueryParameter("cu", "INR")
        .build()
    return try {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Open with"))
        true
    } catch (_: Exception) {
        false
    }
}
