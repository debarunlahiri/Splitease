package com.lambrk.splitease.ui.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lambrk.splitease.data.ApiClient
import org.json.JSONArray
import org.json.JSONObject

class AppLanguage(val code: String, val name: String, val nativeName: String)

/**
 * The app's words in the chosen language. The English wording is the key: [tr] returns the translation from the
 * backend when there is one and the English text otherwise, so a missing translation is never an empty screen.
 * Reading [strings] inside a composable makes it redraw when the language or the translations change.
 */
object I18n {
    private const val PREFS = "splitease-language"

    var lang by mutableStateOf("en")
        private set
    var strings by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var languages by mutableStateOf(
        listOf(AppLanguage("en", "English", "English"), AppLanguage("hi", "Hindi", "हिन्दी")),
    )
        private set

    /** Restores the last language and its saved translations so the app opens in it without waiting for the network. */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        lang = prefs.getString("lang", "en") ?: "en"
        prefs.getString("strings:$lang", null)?.let { strings = parse(it) }
    }

    /** Fetches the language list and the current language's translations; keeps what is saved if offline. */
    suspend fun refresh(context: Context, api: ApiClient) {
        runCatching {
            val list = JSONArray(api.request("app/languages"))
            languages = List(list.length()) {
                val item = list.getJSONObject(it)
                AppLanguage(item.getString("code"), item.getString("name"), item.getString("nativeName"))
            }
        }
        load(context, api, lang)
    }

    suspend fun select(context: Context, api: ApiClient, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("lang", code).apply()
        lang = code
        if (code == "en") strings = emptyMap()
        else context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("strings:$code", null)
            ?.let { strings = parse(it) }
        load(context, api, code)
    }

    private suspend fun load(context: Context, api: ApiClient, code: String) {
        if (code == "en") {
            strings = emptyMap()
            return
        }
        runCatching {
            val body = api.request("app/strings/$code")
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("strings:$code", body).apply()
            if (lang == code) strings = parse(body)
        }
    }

    /** Translations whose English has blanks, like "You owe {0} to {1}", matched against finished sentences. */
    class Pattern(val regex: Regex, val value: String)

    private var patterns: List<Pattern> = emptyList()

    private fun parse(body: String): Map<String, String> {
        val strings = JSONObject(body).optJSONObject("strings") ?: return emptyMap()
        val map = strings.keys().asSequence().associateWith { strings.getString(it) }
        patterns = map.filterKeys { PLACEHOLDER.containsMatchIn(it) && it.replace(PLACEHOLDER, "").trim().length >= 9 }.map { (key, value) ->
            val parts = key.split(PLACEHOLDER)
            Pattern(Regex(parts.joinToString("(.+?)") { Regex.escape(it) }, RegexOption.DOT_MATCHES_ALL).let { Regex("^" + it.pattern + "$", RegexOption.DOT_MATCHES_ALL) }, value)
        }.sortedByDescending { it.regex.pattern.length }
        return map
    }

    private val PLACEHOLDER = Regex("\\{\\d+\\}")

    /** Finds a translation for a finished sentence by matching it against the patterns. */
    fun translate(text: String): String? {
        strings[text]?.let { return it }
        if (strings.isEmpty()) return null
        // Words around the text (spaces, new lines) are kept; only the words are translated.
        val core = text.trim()
        if (core.isEmpty()) return null
        if (core != text) {
            val inner = translate(core) ?: return null
            return text.substring(0, text.indexOf(core)) + inner + text.substring(text.indexOf(core) + core.length)
        }
        for (pattern in patterns) {
            val match = pattern.regex.matchEntire(text) ?: continue
            return PLACEHOLDER.replace(pattern.value) { slot ->
                val index = slot.value.trim('{', '}').toInt()
                match.groupValues.getOrNull(index + 1)?.let { tr(it) } ?: slot.value
            }
        }
        return null
    }
}

/** Translates [text] (English) into the chosen language, or returns it unchanged. */
fun tr(text: String): String = if (I18n.lang == "en") text else I18n.translate(text) ?: text
