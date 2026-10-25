package com.debarunlahiri.splitease.data

import com.debarunlahiri.splitease.BuildConfig
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ApiException(val status: Int) : IOException("API request failed")

class ApiClient(private val baseUrl: String = ApiClient.serverBase()) {
    companion object {
        fun serverBase(): String =
            BuildConfig.API_BASE_URL.trim().trimEnd('/').removeSuffix("/api/v1")
    }

    private val client =
        OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .build()
    @Volatile var accessToken: String? = null
    var onUnauthorized: (suspend () -> Boolean)? = null
    var requestUserId: String = ""
    private val pendingKeys = ConcurrentHashMap<String, String>()

    fun clearRequestKeys() {
        pendingKeys.clear()
    }

    suspend fun request(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        idempotencyKey: String? = null,
        timeoutSeconds: Long? = null,
    ): String =
        withContext(Dispatchers.IO) {
            val base = baseUrl.trim().trimEnd('/').removeSuffix("/api/v1")
            check(base.toHttpUrlOrNull() != null && !base.contains("example.invalid")) {
                "Set SPLITEASE_API_URL to your HTTP or HTTPS gateway address."
            }
            val request =
                Request.Builder()
                    .url(base.trimEnd('/') + "/api/v1/" + path)
                    .header("Accept", "application/json")
            accessToken?.let { request.header("Authorization", "Bearer $it") }
            val protectedWrite =
                method in setOf("POST", "PUT", "PATCH", "DELETE") && !path.startsWith("auth/")
            val signature = "$requestUserId\n$method\n$path\n${body?.toString().orEmpty()}"
            if (protectedWrite) {
                val key =
                    idempotencyKey
                        ?: pendingKeys.computeIfAbsent(signature) { UUID.randomUUID().toString() }
                request.header("Idempotency-Key", key)
            }
            val payload =
                if (method == "GET" || method == "DELETE") null
                else
                    (body ?: JSONObject())
                        .toString()
                        .toRequestBody("application/json".toMediaType())
            val requestClient =
                if (timeoutSeconds == null) client
                else client.newBuilder().callTimeout(timeoutSeconds, TimeUnit.SECONDS).build()
            repeat(2) { attempt ->
                val response =
                    requestClient.newCall(request.method(method, payload).build()).execute()
                if (
                    response.code == 401 &&
                        attempt == 0 &&
                        !path.startsWith("auth/") &&
                        onUnauthorized != null
                ) {
                    response.close()
                    if (onUnauthorized?.invoke() == true) {
                        accessToken?.let { request.header("Authorization", "Bearer $it") }
                        return@repeat
                    }
                    throw ApiException(401)
                }
                response.use {
                    val text = response.body?.string().orEmpty()
                    if (
                        protectedWrite &&
                            (response.isSuccessful ||
                                (response.code in 400..499 &&
                                    response.code !in setOf(401, 408, 409, 429)))
                    ) {
                        pendingKeys.remove(signature)
                    }
                    if (!response.isSuccessful) throw ApiException(response.code)
                    return@withContext text
                }
            }
            throw ApiException(401)
        }

    suspend fun checkSignInAvailability() {
        val response = JSONObject(request("auth/firebase/status", timeoutSeconds = 8))
        if (!response.optBoolean("available", false)) throw ApiException(503)
    }

    suspend fun objectAt(path: String) = JSONObject(request(path))

    // Follow the shared PageResponse contract rather than silently discarding later pages.
    suspend fun all(path: String): List<JSONObject> {
        val items = mutableListOf<JSONObject>()
        var page = 0
        do {
            val separator = if ('?' in path) '&' else '?'
            val result = objectAt("$path${separator}page=$page&size=100")
            val array = result.getJSONArray("items")
            repeat(array.length()) { items += array.getJSONObject(it) }
            page++
        } while (page < result.getInt("totalPages"))
        return items
    }
}

fun json(vararg values: Pair<String, Any?>) =
    JSONObject().apply {
        values.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
    }

fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
