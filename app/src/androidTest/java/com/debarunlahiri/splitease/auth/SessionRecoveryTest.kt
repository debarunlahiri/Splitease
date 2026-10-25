package com.debarunlahiri.splitease.auth

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.debarunlahiri.splitease.data.ApiClient
import com.debarunlahiri.splitease.data.ApiException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionRecoveryTest {
    @Test
    fun unauthorizedWriteRecoversOnceAndReusesIdempotencyKey() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
            val api = ApiClient(server.url("/").toString())
            api.accessToken = "old-token"
            var recoveries = 0
            api.onUnauthorized = {
                recoveries++
                api.accessToken = "new-token"
                true
            }
            api.request(
                "groups",
                "POST",
                JSONObject().put("name", "Trip"),
                idempotencyKey = "stable-action-key",
            )
            val first = server.takeRequest()
            val second = server.takeRequest()
            assertEquals("Bearer old-token", first.getHeader("Authorization"))
            assertEquals("Bearer new-token", second.getHeader("Authorization"))
            assertEquals(first.getHeader("Idempotency-Key"), second.getHeader("Idempotency-Key"))
            assertEquals(first.body.readUtf8(), second.body.readUtf8())
            assertEquals(1, recoveries)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun unsuccessfulRecoveryDoesNotLoop() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            repeat(2) { server.enqueue(MockResponse().setResponseCode(401)) }
            val api = ApiClient(server.url("/").toString())
            var recoveries = 0
            api.onUnauthorized = {
                recoveries++
                true
            }
            assertTrue(runCatching { api.request("groups") }.exceptionOrNull() is ApiException)
            assertEquals(1, recoveries)
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun manualSignOutIsPersistedAndSuccessfulLoginClearsIt() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val name = "session-test-" + UUID.randomUUID()
        val context =
            object : ContextWrapper(application) {
                override fun getSharedPreferences(ignored: String, mode: Int): SharedPreferences =
                    application.getSharedPreferences(name, mode)
            }
        try {
            assertFalse(SessionPreferences(context).explicitlySignedOut())
            SessionPreferences(context).signedOut()
            assertTrue(SessionPreferences(context).explicitlySignedOut())
            SessionPreferences(context).signedIn()
            assertFalse(SessionPreferences(context).explicitlySignedOut())
        } finally {
            application.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
