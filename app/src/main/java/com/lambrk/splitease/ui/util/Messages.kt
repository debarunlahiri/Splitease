package com.lambrk.splitease.ui.util

/** One thing a person still owes: what it was for, how much, and when it was due. */
internal class OwedLine(val what: String, val amount: String, val due: String? = null)

/**
 * The messages people send from their own phone (WhatsApp, SMS). They say who it is from, what it is for and what to do
 * next, in plain words. Pay links are shown instead of the UPI ID: the page they open already has everything needed,
 * so the ID is only written out when there is no link to send.
 */
internal object Messages {
    private fun firstName(name: String) = name.trim().substringBefore(" ").ifBlank { name }

    /** A friendly "please pay" reminder for one person. [link] is an optional one-tap payment link. */
    fun reminder(to: String, from: String, lines: List<OwedLine>, total: String, link: String? = null): String = buildString {
        append("Hi ${firstName(to)}, this is a friendly reminder")
        if (from.isNotBlank()) append(" from ${firstName(from)}")
        append(" on SplitEase.\n\n")
        if (lines.size == 1) {
            val line = lines.first()
            append("You still owe $total for \"${line.what}\"")
            line.due?.let { append(" (due $it)") }
            append(".\n")
        } else {
            append("You still owe $total in all:\n")
            lines.forEach { line ->
                append("- ${line.what}: ${line.amount}")
                line.due?.let { append(" (due $it)") }
                append("\n")
            }
        }
        append("\nWhenever you get a chance, please pay it back.")
        link?.let { append("\nPay in one tap: $it") }
        append("\nIf you've already paid, just let me know and I'll mark it as paid.\n\nThank you!")
    }

    /** The whole split for a group chat: who paid, who owes what, and how to pay. */
    fun groupSplit(
        title: String,
        total: String,
        paidBy: String,
        owes: List<Pair<String, String>>,
        payBy: String?,
        link: String?,
        upiId: String?,
    ): String = buildString {
        append("*$title* - $total\n")
        append("Paid by ${firstName(paidBy)}.\n")
        if (owes.isNotEmpty()) {
            append("\nWho owes what:\n")
            owes.forEach { (name, amount) -> append("- ${firstName(name)}: *$amount*\n") }
        }
        payBy?.let { append("\nPlease pay by $it.") }
        if (link != null) append("\nPay in one tap: $link")
        else if (upiId != null) append("\nPay ${firstName(paidBy)} on UPI: $upiId")
        append("\n\nShared with SplitEase")
    }

    /** One person's share, written to them directly. */
    fun personalShare(
        to: String,
        title: String,
        amount: String,
        paidBy: String,
        payBy: String?,
        link: String?,
        upiId: String?,
    ): String = buildString {
        append("Hi ${firstName(to)}, here is your share for \"$title\".\n\n")
        append("You owe $amount to ${firstName(paidBy)}.")
        payBy?.let { append("\nPlease pay by $it.") }
        if (link != null) append("\nPay in one tap: $link")
        else if (upiId != null) append("\nPay ${firstName(paidBy)} on UPI: $upiId")
        append("\n\nThank you!")
    }
}
