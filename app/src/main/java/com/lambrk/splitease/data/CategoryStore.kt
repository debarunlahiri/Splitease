package com.lambrk.splitease.data

import android.content.Context

/**
 * Expense categories the user has typed before, most recent first, kept on this device
 * separately for each account so a shared phone does not mix them up.
 */
class CategoryStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("expense_categories", Context.MODE_PRIVATE)

    private fun key(userId: String) = "categories:$userId"

    fun all(userId: String): List<String> =
        preferences.getString(key(userId), "").orEmpty().split('\n').filter { it.isNotBlank() }

    /** Moves the category to the front, replacing any copy that differs only by case. */
    fun remember(userId: String, category: String) {
        val name = category.trim()
        if (userId.isBlank() || name.isEmpty()) return
        val updated =
            (listOf(name) + all(userId).filterNot { it.equals(name, ignoreCase = true) }).take(MAX)
        preferences.edit().putString(key(userId), updated.joinToString("\n")).apply()
    }

    private companion object {
        const val MAX = 50
    }
}
