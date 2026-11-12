package com.lambrk.splitease.ui.util

/** The result of checking what the user typed as a UPI ID. */
internal class UpiCheck(val valid: Boolean, val reason: String? = null, val unknownHandle: Boolean = false)

private val KNOWN_HANDLES = setOf(
    "ybl", "ibl", "axl", "okaxis", "oksbi", "okhdfcbank", "okicici", "paytm", "pthdfc", "ptsbi", "ptyes", "upi", "apl",
    "sbi", "hdfcbank", "icici", "axisbank", "axisb", "kotak", "yesbank", "idfcfirst", "idfcbank", "indus", "federal",
    "fbl", "rbl", "pnb", "boi", "cnrb", "canarabank", "unionbank", "uboi", "barodampay", "aubank", "jupiteraxis",
    "freecharge", "mairtel", "airtel", "jio", "postbank", "ikwik", "abfspay", "waicici", "wahdfcbank", "wasbi",
    "waaxis", "slc", "fam", "naviaxis", "ezetap", "cmsidfc", "yapl", "timecosmos", "tapicici", "niyoicici",
)

/** Checks the shape of a UPI ID (name@handle) with the same rules as the server, so mistakes show as you type. */
internal fun checkUpiId(input: String): UpiCheck {
    val value = input.trim().lowercase()
    if (value.isEmpty()) return UpiCheck(false, "Enter your UPI ID, like name@bank.")
    if (value.any { it.isWhitespace() }) return UpiCheck(false, "A UPI ID cannot contain spaces.")
    val at = value.indexOf('@')
    if (at == -1) return UpiCheck(false, "A UPI ID has an @, like name@bank.")
    if (value.indexOf('@', at + 1) != -1) return UpiCheck(false, "A UPI ID has only one @.")
    val local = value.substring(0, at)
    val handle = value.substring(at + 1)
    if ('.' in handle) return UpiCheck(false, "That looks like an email address, not a UPI ID.")
    if (local.length < 3) return UpiCheck(false, "The part before @ needs at least 3 characters.")
    if (local.length > 100) return UpiCheck(false, "The part before @ is too long.")
    if (!Regex("[a-z0-9._-]+").matches(local)) {
        return UpiCheck(false, "Before @ use only letters, numbers, dots, dashes and underscores.")
    }
    if (!local.first().isLetterOrDigit() || local.last() in "._-" || Regex("[._-]{2,}").containsMatchIn(local)) {
        return UpiCheck(false, "The part before @ cannot start or end with a symbol or repeat symbols.")
    }
    if (!Regex("[a-z][a-z0-9]{1,31}").matches(handle)) {
        return UpiCheck(false, "After @ enter your bank or app handle, like ybl, oksbi or paytm.")
    }
    return UpiCheck(true, null, unknownHandle = handle !in KNOWN_HANDLES)
}
