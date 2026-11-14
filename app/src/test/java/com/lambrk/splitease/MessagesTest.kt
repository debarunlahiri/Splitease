package com.lambrk.splitease

import com.lambrk.splitease.ui.util.Messages
import com.lambrk.splitease.ui.util.OwedLine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagesTest {
    @Test
    fun reminderSaysWhoWhatAndHowMuch() {
        val text = Messages.reminder("Test 2", "Debarun Lahiri", listOf(OwedLine("erger", "₹8,558.50", "13 Nov")), "₹8,558.50")
        println(text)
        assertTrue(text.contains("Hi Test, this is a friendly reminder from Debarun on SplitEase."))
        assertTrue(text.contains("You still owe ₹8,558.50 for \"erger\" (due 13 Nov)."))
        assertTrue(text.contains("I'll mark it as paid"))
    }

    @Test
    fun upiIdIsHiddenWhenThereIsALink() {
        val withLink = Messages.groupSplit("Dinner", "₹300", "Debarun", listOf("Test" to "₹100"), null, "https://x.test/p/abc", "me@ybl")
        assertFalse(withLink.contains("me@ybl"))
        assertTrue(withLink.contains("Pay in one tap: https://x.test/p/abc"))
        val without = Messages.personalShare("Test", "Dinner", "₹100", "Debarun", null, null, "me@ybl")
        assertTrue(without.contains("me@ybl"))
    }
}
