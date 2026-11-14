package com.lambrk.splitease.ui.util

import android.content.Context
import android.telephony.TelephonyManager
import io.michaelrocks.libphonenumber.android.NumberParseException
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import com.lambrk.splitease.SplitEaseApplication
import java.util.Locale

/** A country with its calling code, for the number picker. */
internal class Country(val iso: String, val name: String, val dial: String)

private var cachedUtil: PhoneNumberUtil? = null

/** Google's phone number rules (libphonenumber): every country's valid lengths and formats. */
private fun phoneUtil(): PhoneNumberUtil =
    cachedUtil ?: PhoneNumberUtil.createInstance(
        requireNotNull(SplitEaseApplication.instance) { "No application" }.applicationContext,
    ).also { cachedUtil = it }

/** Every country with phone numbers, A to Z. */
internal val COUNTRIES: List<Country> by lazy {
    val util = phoneUtil()
    util.supportedRegions.filter { it != "001" && it.length == 2 }
        .map { Country(it, Locale("", it).getDisplayCountry(Locale.ENGLISH).ifBlank { it }, util.getCountryCodeForRegion(it).toString()) }
        .sortedBy { it.name }
}

private const val COUNTRY_PREFS = "splitease-country"

/** The country the person chose in their profile, if any. */
internal fun chosenCountry(context: Context): Country? =
    context.getSharedPreferences(COUNTRY_PREFS, Context.MODE_PRIVATE).getString("iso", null)
        ?.let { iso -> COUNTRIES.firstOrNull { it.iso == iso } }

internal fun saveChosenCountry(context: Context, iso: String?) {
    context.getSharedPreferences(COUNTRY_PREFS, Context.MODE_PRIVATE).edit().apply {
        if (iso == null) remove("iso") else putString("iso", iso)
    }.apply()
}

/** The country to start from everywhere: the one chosen in the profile, else the SIM's, else the phone's region, else India. */
internal fun defaultCountry(context: Context): Country {
    chosenCountry(context)?.let { return it }
    val iso = (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.simCountryIso
        ?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)
        ?: Locale.getDefault().country.takeIf { it.isNotBlank() }
    return COUNTRIES.firstOrNull { it.iso == iso } ?: COUNTRIES.first { it.iso == "IN" }
}

/** The currency people in [iso] normally use (INR for India), or null when it has none we can tell. */
internal fun currencyOf(iso: String): String? =
    runCatching { java.util.Currency.getInstance(Locale("", iso)).currencyCode }.getOrNull()

/** The number as +<country code><digits> when it is a real number for that country, else null. */
internal fun withCountryCode(raw: String, region: String): String? = try {
    val util = phoneUtil()
    val parsed = util.parse(raw.trim(), region)
    if (util.isValidNumber(parsed)) util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164) else null
} catch (_: NumberParseException) {
    null
}

/** Same, using the country this phone is in for numbers written without a country code. */
internal fun withCountryCode(raw: String, context: Context): String? = withCountryCode(raw, defaultCountry(context).iso)

/** The country a saved +… number belongs to, and the rest of it as the person would dial locally. */
internal fun splitE164(e164: String): Pair<String, String>? = try {
    val util = phoneUtil()
    val parsed = util.parse(e164, null)
    val region = util.getRegionCodeForNumber(parsed)
    if (region == null || region == "ZZ") null
    // The number as dialled inside the country, without the 0 some countries put in front (the trunk prefix).
    else region to util.getNationalSignificantNumber(parsed)
} catch (_: NumberParseException) {
    null
}

/** +91 98765 43210 style text for a saved number. */
internal fun prettyPhone(e164: String): String = try {
    val util = phoneUtil()
    util.format(util.parse(e164, null), PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
} catch (_: NumberParseException) {
    e164
}

/** What a typical number for the country looks like, like "081234 56789". */
internal fun exampleNumber(region: String): String? = try {
    val util = phoneUtil()
    util.getExampleNumberForType(region, PhoneNumberUtil.PhoneNumberType.MOBILE)
        ?.let { formatAsYouType(region, util.getNationalSignificantNumber(it)) }
} catch (_: Exception) {
    null
}

/** Groups the digits the way people in that country write them, as they are typed. */
internal fun formatAsYouType(region: String, digits: String): String {
    val formatter = phoneUtil().getAsYouTypeFormatter(region)
    var shown = ""
    for (digit in digits) shown = formatter.inputDigit(digit)
    return shown
}
