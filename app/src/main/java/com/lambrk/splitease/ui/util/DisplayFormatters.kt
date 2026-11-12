package com.lambrk.splitease.ui.util

import com.lambrk.splitease.data.objects
import com.lambrk.splitease.ui.SplitEaseViewModel
import java.text.NumberFormat
import java.util.Currency
import org.json.JSONObject

internal fun shortId(id: String) = id.take(8)

/** The short symbol for a currency code, such as ₹, $ or €; the code itself when it has none. */
internal fun currencySymbol(code: String): String =
    runCatching { Currency.getInstance(code).getSymbol(java.util.Locale.getDefault()) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() && it.length <= 3 }
        ?: code

internal fun memberName(id: String, model: SplitEaseViewModel): String {
    if (id == model.userId) {
        return model.user?.optionalText("displayName")?.takeIf { it.isNotBlank() } ?: "You"
    }
    return memberDetail(id, model, "displayName") ?: "Name unavailable"
}

internal fun memberPhotoUrl(id: String, model: SplitEaseViewModel): String? =
    if (id == model.userId) model.user?.optionalText("photoUrl")?.ifBlank { null }
        ?: memberDetail(id, model, "photoUrl")
    else memberDetail(id, model, "photoUrl")

private fun memberDetail(id: String, model: SplitEaseViewModel, key: String): String? =
    (sequenceOf(model.selected).filterNotNull() + model.groups.asSequence())
        .flatMap { it.optJSONArray("members")?.objects().orEmpty().asSequence() }
        .filter { it.optString("userId") == id }
        .map { it.optionalText(key) }
        .firstOrNull { it.isNotBlank() }

internal fun money(amount: String, currency: String): String = runCatching {
    NumberFormat.getCurrencyInstance()
        .apply { this.currency = Currency.getInstance(currency) }
        .format(amount.toBigDecimal())
}
    .getOrElse { "$currency $amount" }

internal fun JSONObject.optionalText(key: String): String = if (isNull(key)) "" else optString(key)

/**
 * Human-readable date (e.g. "Nov 12, 2026", in the device's language) for an ISO date
 * (`2026-11-12`) or timestamp (`2026-11-12T08:30:00Z`). Timestamps are shown in the local time zone.
 * Falls back to the original text if it can't be parsed.
 */
internal fun friendlyDate(value: String): String {
    if (value.isBlank()) return value
    val date = runCatching {
        java.time.OffsetDateTime.parse(value).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate()
    }.getOrNull() ?: runCatching { java.time.LocalDate.parse(value.take(10)) }.getOrNull()
        ?: return value
    return date.format(
        java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
    )
}

/** A readable date and time in the local time zone, e.g. "Nov 12, 2026, 8:21 PM". */
internal fun friendlyDateTime(value: String): String {
    val time = runCatching {
        java.time.OffsetDateTime.parse(value).atZoneSameInstant(java.time.ZoneId.systemDefault())
    }.getOrNull() ?: return friendlyDate(value)
    return time.format(
        java.time.format.DateTimeFormatter.ofLocalizedDateTime(
            java.time.format.FormatStyle.MEDIUM,
            java.time.format.FormatStyle.SHORT,
        )
    )
}
