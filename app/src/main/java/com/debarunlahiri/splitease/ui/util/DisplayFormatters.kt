package com.debarunlahiri.splitease.ui.util

import com.debarunlahiri.splitease.data.objects
import com.debarunlahiri.splitease.ui.SplitEaseViewModel
import java.text.NumberFormat
import java.util.Currency
import org.json.JSONObject

internal fun shortId(id: String) = id.take(8)

internal fun memberName(id: String, model: SplitEaseViewModel): String {
    if (id == model.userId) {
        return model.user?.optionalText("displayName")?.takeIf { it.isNotBlank() } ?: "You"
    }
    val groups = listOfNotNull(model.selected) + model.groups
    return groups
        .asSequence()
        .flatMap { it.getJSONArray("members").objects().asSequence() }
        .firstOrNull { it.optString("userId") == id && it.optionalText("displayName").isNotBlank() }
        ?.optionalText("displayName") ?: "Name unavailable"
}

internal fun money(amount: String, currency: String): String = runCatching {
    NumberFormat.getCurrencyInstance()
        .apply { this.currency = Currency.getInstance(currency) }
        .format(amount.toBigDecimal())
}
    .getOrElse { "$currency $amount" }

internal fun JSONObject.optionalText(key: String): String = if (isNull(key)) "" else optString(key)
