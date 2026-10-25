package com.debarunlahiri.splitease.ui

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.debarunlahiri.splitease.auth.GoogleLogin
import com.debarunlahiri.splitease.auth.SessionPreferences
import com.debarunlahiri.splitease.data.ApiClient
import com.debarunlahiri.splitease.data.ApiException
import com.debarunlahiri.splitease.data.json
import com.debarunlahiri.splitease.data.local.LocalAccount
import com.debarunlahiri.splitease.data.local.PendingExpense
import com.debarunlahiri.splitease.data.objects
import com.debarunlahiri.splitease.data.offline.ExpenseSync
import com.debarunlahiri.splitease.data.offline.OfflineStore
import com.debarunlahiri.splitease.ui.errors.UserFacingError
import com.debarunlahiri.splitease.ui.errors.validateInput
import com.debarunlahiri.splitease.ui.navigation.AppDestination
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class SplitEaseViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val offline = OfflineStore(context)
    private var account: LocalAccount? = null
    private var outboxJob: Job? = null
    private var localExpenses by mutableStateOf<List<PendingExpense>>(emptyList())
    var showingSavedData by mutableStateOf(false)
        private set

    var groupDataAvailable by mutableStateOf(false)
        private set

    val pendingExpenseCount: Int
        get() = localExpenses.count { it.state != "SYNCED" }

    private val api = ApiClient()
    private val sessionPreferences = SessionPreferences(context)
    private val sessionMutex = Mutex()
    var restoringSession by mutableStateOf(true)
        private set

    var needsReconnect by mutableStateOf(false)
        private set

    init {
        api.onUnauthorized = {
            recoverSession(forceFirebase = true)
            true
        }
    }

    private val operation = Mutex()
    private var restored = false
    private var refreshToken: String? = null
    var user by mutableStateOf<JSONObject?>(null)
        private set

    var groups by mutableStateOf<List<JSONObject>>(emptyList())
        private set

    var invitations by mutableStateOf<List<JSONObject>>(emptyList())
        private set

    var notifications by mutableStateOf<List<JSONObject>>(emptyList())
        private set

    var preferences by mutableStateOf<JSONObject?>(null)
        private set

    var entitlement by mutableStateOf<JSONObject?>(null)
        private set

    var audit by mutableStateOf<List<JSONObject>>(emptyList())
        private set

    var groupInvitations by mutableStateOf<List<JSONObject>>(emptyList())
        private set

    var selected by mutableStateOf<JSONObject?>(null)
        private set

    private var remoteExpenses by mutableStateOf<List<JSONObject>>(emptyList())
    val expenses: List<JSONObject>
        get() {
            val groupId = selected?.optString("id") ?: return emptyList()
            val merged = linkedMapOf<String, JSONObject>()
            localExpenses
                .filter { it.groupId == groupId }
                .forEach { row ->
                    val item = OfflineStore.displayExpense(row)
                    merged[item.getString("id")] = item
                }
            remoteExpenses.forEach { merged[it.getString("id")] = it }
            return merged.values.sortedByDescending { it.optString("expenseDate") }
        }

    var balances by mutableStateOf<List<JSONObject>>(emptyList())
        private set

    var suggestions by mutableStateOf<List<JSONObject>>(emptyList())
        private set

    var settlements by mutableStateOf<List<JSONObject>>(emptyList())
        private set

    var busy by mutableStateOf(false)
        private set

    var refreshing by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var destination by mutableStateOf(AppDestination.Groups)
    val userId: String
        get() = user?.optString("id").orEmpty()

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            operation.withLock {
                busy = true
                error = null
                try {
                    block()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    // An API rejection is not proof that the persisted Google identity is lost.
                    // Reconnection is set only by silent identity recovery itself.
                    Log.w("SplitEaseSession", "Operation failed: ${exception.javaClass.simpleName}")
                    if (exception is ReconnectionRequiredException) {
                        api.accessToken = null
                        refreshToken = null
                        needsReconnect = user != null
                        showingSavedData = user != null
                    }
                    error = UserFacingError.message(exception)
                } finally {
                    busy = false
                }
            }
        }
    }

    fun restore(activity: Activity) {
        if (restored) return
        restored = true
        work {
            try {
                if (sessionPreferences.explicitlySignedOut()) return@work
                val firebase =
                    if (FirebaseApp.getApps(context).isEmpty()) null
                    else FirebaseAuth.getInstance().currentUser
                val saved = offline.dao.lastActiveAccount(ApiClient.serverBase())
                saved?.let {
                    user = JSONObject(it.userJson)
                    attachAccount(it)
                    restoreHome()
                    if (firebase != null) ExpenseSync.schedule(context, it.accountKey)
                }
                if (!OfflineStore.networkAvailable(context)) {
                    showingSavedData = user != null
                    return@work
                }
                if (user != null && firebase?.uid != saved?.firebaseUid) {
                    needsReconnect = true
                    return@work
                }
                try {
                    GoogleLogin.restore(activity)?.let { exchange(it) }
                } catch (failure: IOException) {
                    if (user == null || failure is ApiException) throw failure
                    showingSavedData = true
                }
            } finally {
                restoringSession = false
            }
        }
    }

    private fun attachAccount(saved: LocalAccount) {
        account = saved
        api.requestUserId = saved.backendUserId
        outboxJob?.cancel()
        outboxJob = viewModelScope.launch {
            offline.dao
                .observeExpenses(saved.accountKey)
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    Log.w(
                        "SplitEaseSession",
                        "Observe saved expenses failed: ${failure.javaClass.simpleName}",
                    )
                    message = "Saved expenses are temporarily unavailable. Please reopen the app."
                }
                .collect { localExpenses = it }
        }
    }

    private suspend fun restoreHome() {
        val key = account?.accountKey ?: return
        offline.cached(key, "home")?.let { saved ->
            groups = saved.getJSONArray("groups").objects()
            invitations = saved.getJSONArray("invitations").objects()
            notifications = saved.getJSONArray("notifications").objects()
            preferences = saved.optJSONObject("preferences")
            entitlement = saved.optJSONObject("entitlement")
            showingSavedData = true
        }
    }

    private suspend fun saveHome() {
        val saved = account ?: return
        offline.cache(
            saved.accountKey,
            "home",
            json(
                "groups" to JSONArray(groups),
                "invitations" to JSONArray(invitations),
                "notifications" to JSONArray(notifications),
                "preferences" to preferences,
                "entitlement" to entitlement,
            ),
        )
        user?.let { offline.dao.saveAccount(saved.copy(userJson = it.toString())) }
        showingSavedData = false
    }

    fun login(activity: Activity) {
        if (busy) return
        work {
            api.checkSignInAvailability()
            val expectedIdentity = account?.firebaseUid
            val token = GoogleLogin.signIn(activity)
            validateInput(
                expectedIdentity == null ||
                    FirebaseAuth.getInstance().currentUser?.uid == expectedIdentity
            ) {
                "Choose the Google account you're already signed in with."
            }
            exchange(token)
        }
    }

    private suspend fun exchange(token: String) {
        val session = JSONObject(api.request("auth/firebase", "POST", json("idToken" to token)))
        api.accessToken = session.getString("accessToken")
        refreshToken = session.getString("refreshToken")
        user = session.getJSONObject("user")
        api.requestUserId = userId
        val firebaseUid =
            FirebaseAuth.getInstance().currentUser?.uid ?: error("No signed-in identity")
        // Commit successful authentication before cache or scheduler work can fail.
        sessionPreferences.signedIn()
        needsReconnect = false
        showingSavedData = false
        val saved =
            LocalAccount(
                ApiClient.serverBase() + "|" + userId,
                firebaseUid,
                ApiClient.serverBase(),
                userId,
                user!!.toString(),
            )
        attachAccount(saved)
        localMaintenance("save account") { offline.saveAccount(firebaseUid, user!!) }
        localMaintenance("restore home") { restoreHome() }
        localMaintenance("schedule sync") { ExpenseSync.schedule(context, saved.accountKey) }
        loadHome()
        if (preferences?.optBoolean("pushEnabled") == true) {
            localMaintenance("schedule notification registration") {
                com.debarunlahiri.splitease.notifications.NotificationTokenWorker.schedule(context)
            }
        }
    }

    // Firebase persists its identity securely; backend tokens stay in memory and are recovered
    // silently.
    private suspend fun renew() = recoverSession(forceFirebase = false)

    private suspend fun recoverSession(forceFirebase: Boolean) = sessionMutex.withLock {
        if (!OfflineStore.networkAvailable(context)) throw IOException("Connection unavailable")
        var session: JSONObject? = null
        val token = refreshToken
        if (!forceFirebase && token != null) {
            try {
                session =
                    JSONObject(api.request("auth/refresh", "POST", json("refreshToken" to token)))
            } catch (failure: ApiException) {
                if (failure.status != 401 && failure.status != 403) throw failure
                refreshToken = null
            }
        }
        if (session == null) {
            val firebase =
                FirebaseAuth.getInstance().currentUser ?: throw ReconnectionRequiredException()
            if (firebase.uid != account?.firebaseUid) throw ReconnectionRequiredException()
            val idToken =
                try {
                    firebase.getIdToken(true).await().token ?: throw ReconnectionRequiredException()
                } catch (failure: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                    throw ReconnectionRequiredException()
                }
            session = JSONObject(api.request("auth/firebase", "POST", json("idToken" to idToken)))
        }
        if (session.getJSONObject("user").getString("id") != userId) throw ApiException(401)
        api.accessToken = session.getString("accessToken")
        refreshToken = session.getString("refreshToken")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
            needsReconnect = false
        }
    }

    private class ReconnectionRequiredException : IOException()

    private suspend fun localMaintenance(stage: String, action: suspend () -> Unit) {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // No tokens, personal data, server payloads or exception messages in diagnostics.
            Log.w("SplitEaseSession", "$stage failed: ${failure.javaClass.simpleName}")
        }
    }

    private suspend fun loadHome() {
        groups = api.all("groups")
        invitations = api.all("groups/invitations/me")
        notifications = api.all("notifications")
        user = api.objectAt("users/me")
        preferences = api.objectAt("notifications/preferences")
        entitlement = api.objectAt("subscriptions/me/entitlement")
        localMaintenance("save home") { saveHome() }
        showingSavedData = false
    }

    fun refresh() {
        if (busy) return
        work {
            refreshing = true
            try {
                try {
                    renew()
                    loadHome()
                    selected?.let { loadGroup(it) }
                    account?.let { ExpenseSync.schedule(context, it.accountKey) }
                } catch (failure: IOException) {
                    if (failure is ApiException) throw failure
                    showingSavedData = true
                    message = "Showing saved data. Your expenses are safe on this device."
                }
            } finally {
                refreshing = false
            }
        }
    }

    private suspend fun loadGroup(group: JSONObject) {
        val fetchedAfter = System.currentTimeMillis()
        val id = group.getString("id")
        val currency = group.getString("defaultCurrency")
        val newExpenses = api.all("expenses/group/$id")
        val newBalances =
            JSONArray(api.request("settlements/group/$id/balances?currency=$currency")).objects()
        val newSuggestions =
            JSONArray(api.request("settlements/group/$id/suggestions?currency=$currency")).objects()
        val newSettlements = api.all("settlements/group/$id")
        remoteExpenses = newExpenses
        balances = newBalances
        suggestions = newSuggestions
        settlements = newSettlements
        groupInvitations =
            if (group.getString("createdBy") == userId) api.all("groups/$id/invitations")
            else emptyList()
        selected = groups.firstOrNull { it.optString("id") == id } ?: group
        groupDataAvailable = true
        showingSavedData = false
        account?.let {
            offline.cacheGroup(
                it.accountKey,
                id,
                json(
                    "expenses" to JSONArray(remoteExpenses),
                    "balances" to JSONArray(balances),
                    "suggestions" to JSONArray(suggestions),
                    "settlements" to JSONArray(settlements),
                    "invitations" to JSONArray(groupInvitations),
                ),
                fetchedAfter,
            )
        }
    }

    fun openGroup(group: JSONObject) = work {
        selected = group
        remoteExpenses = emptyList()
        balances = emptyList()
        suggestions = emptyList()
        settlements = emptyList()
        groupInvitations = emptyList()
        groupDataAvailable = false
        account?.let { saved ->
            offline.cached(saved.accountKey, "group:${group.getString("id")}")?.let { cached ->
                remoteExpenses = cached.getJSONArray("expenses").objects()
                balances = cached.getJSONArray("balances").objects()
                suggestions = cached.getJSONArray("suggestions").objects()
                settlements = cached.getJSONArray("settlements").objects()
                groupInvitations = cached.getJSONArray("invitations").objects()
                groupDataAvailable = true
                showingSavedData = true
            }
        }
        if (!OfflineStore.networkAvailable(context)) {
            showingSavedData = true
            return@work
        }
        try {
            renew()
            loadGroup(group)
        } catch (failure: IOException) {
            if (failure is ApiException) throw failure
            showingSavedData = true
        }
    }

    fun closeGroup() {
        selected = null
    }

    fun createGroup(name: String, currency: String, done: () -> Unit) = work {
        validateInput(name.isNotBlank() && name.length <= 120) {
            "Enter a group name, up to 120 characters."
        }
        val code = currency.trim().uppercase(Locale.ROOT)
        validateInput(runCatching { java.util.Currency.getInstance(code) }.isSuccess) {
            "Choose a valid currency, such as INR, USD or EUR."
        }
        renew()
        api.request("groups", "POST", json("name" to name.trim(), "defaultCurrency" to code))
        loadHome()
        done()
    }

    fun invite(email: String, done: () -> Unit) = work {
        val group = selected ?: return@work
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        validateInput(android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
            "Enter a valid email address."
        }
        renew()
        api.request(
            "groups/${group.getString("id")}/invitations",
            "POST",
            json("email" to normalizedEmail),
        )
        loadGroup(group)
        message = "Invitation created"
        done()
    }

    fun invitationAction(invitation: JSONObject, accept: Boolean) = work {
        renew()
        val action = if (accept) "accept" else "decline"
        api.request("groups/invitations/${invitation.getString("id")}/$action", "POST")
        loadHome()
    }

    fun saveExpense(
        description: String,
        amount: String,
        category: String,
        notes: String,
        splitType: String,
        date: String,
        participantIds: Set<String>,
        sharesInput: List<String>,
        original: JSONObject?,
        done: () -> Unit,
    ) = work {
        val group = selected ?: return@work
        val total = amount.toBigDecimalOrNull()
        validateInput(total != null && total > BigDecimal.ZERO && total.scale() <= 2) {
            "Enter a positive amount with up to two decimals."
        }
        validateInput(description.isNotBlank() && description.length <= 160) {
            "Enter a description, up to 160 characters."
        }
        validateInput(category.length <= 80 && notes.length <= 2000) {
            "Category or notes are too long."
        }
        validateInput(runCatching { LocalDate.parse(date) }.isSuccess) {
            "Enter a valid date in YYYY-MM-DD format."
        }
        validateInput(participantIds.isNotEmpty()) { "Choose at least one person to split with." }
        val members = group.getJSONArray("members").objects()
        val shares = JSONArray()
        members.forEachIndexed { index, member ->
            if (member.getString("userId") !in participantIds) return@forEachIndexed
            val value =
                if (splitType == "EQUAL") BigDecimal.ZERO
                else sharesInput.getOrNull(index)?.toBigDecimalOrNull()
            validateInput(value != null && value >= BigDecimal.ZERO) {
                "Enter a valid share for every member."
            }
            shares.put(json("userId" to member.getString("userId"), "value" to value))
        }
        if (splitType != "EQUAL") {
            val sum =
                shares.objects().fold(BigDecimal.ZERO) { acc, item ->
                    acc + item.getString("value").toBigDecimal()
                }
            val expected = if (splitType == "PERCENTAGE") BigDecimal(100) else total
            validateInput(sum.compareTo(expected) == 0) { "Shares must add up to $expected." }
        }
        val payload =
            json(
                "groupId" to group.getString("id"),
                "paidBy" to (original?.getString("paidBy") ?: userId),
                "description" to description.trim(),
                "amount" to total,
                "currency" to group.getString("defaultCurrency"),
                "expenseDate" to date,
                "splitType" to splitType,
                "shares" to shares,
                "category" to category.trim(),
                "notes" to notes.trim(),
                "receipt" to original?.optJSONObject("receipt"),
            )
        if (original == null) {
            val saved = account ?: throw ApiException(401)
            offline.queue(saved.accountKey, payload)
            showingSavedData = true
            ExpenseSync.schedule(context, saved.accountKey)
            message = "Expense saved on this device. It will sync when connected."
            done()
        } else {
            validateInput(!original.getString("id").startsWith("local:")) {
                "Wait for this expense to sync before editing."
            }
            renew()
            api.request("expenses/${original.getString("id")}", "PUT", payload)
            loadGroup(group)
            message = "Expense saved"
            done()
        }
    }

    fun retryExpense(operationId: String) = work {
        val saved = account ?: return@work
        offline.dao.retry(saved.accountKey, operationId)
        ExpenseSync.schedule(context, saved.accountKey)
        message = "We'll retry when a connection is available."
    }

    fun deleteExpense(expense: JSONObject, done: () -> Unit) = work {
        renew()
        api.request("expenses/${expense.getString("id")}", "DELETE")
        account?.let { saved ->
            localExpenses
                .filter {
                    it.state == "SYNCED" &&
                        it.serverJson?.let(::JSONObject)?.optString("id") == expense.getString("id")
                }
                .forEach { offline.dao.removeSynced(saved.accountKey, it.operationId) }
        }
        selected?.let { loadGroup(it) }
        done()
    }

    fun settle(suggestion: JSONObject) = work {
        val group = selected ?: return@work
        validateInput(suggestion.getString("fromUserId") == userId) {
            "Only your own payments can be recorded."
        }
        renew()
        api.request(
            "settlements",
            "POST",
            json(
                "groupId" to group.getString("id"),
                "payeeId" to suggestion.getString("toUserId"),
                "amount" to suggestion.get("amount"),
                "currency" to suggestion.getString("currency"),
                "note" to "Recorded from SplitEase Android",
            ),
        )
        loadGroup(group)
        message = "Payment recorded"
    }

    fun enablePush(enabled: Boolean) = work {
        renew()
        val token =
            if (enabled) com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
            else null
        preferences =
            JSONObject(
                api.request(
                    "notifications/preferences",
                    "PUT",
                    json(
                        "inboxEnabled" to (preferences?.optBoolean("inboxEnabled") ?: true),
                        "pushEnabled" to enabled,
                        "pushToken" to token,
                    ),
                )
            )
        saveHome()
    }

    fun openNotification(item: JSONObject) = work {
        renew()
        if (!item.optBoolean("read"))
            api.request("notifications/${item.getString("id")}/read", "PATCH")
        loadHome()
        if (item.optString("type") == "GROUP_INVITATION") {
            selected = null
            destination = AppDestination.Groups
        } else {
            groups
                .firstOrNull { it.optString("id") == item.optString("groupId") }
                ?.let { loadGroup(it) }
        }
    }

    fun notificationTap() {
        destination = AppDestination.Activity
        selected = null
        refresh()
    }

    fun markRead(item: JSONObject) = work {
        renew()
        api.request("notifications/${item.getString("id")}/read", "PATCH")
        notifications = api.all("notifications")
    }

    fun toggleInbox(enabled: Boolean) = work {
        val current = preferences ?: return@work
        renew()
        preferences =
            JSONObject(
                api.request(
                    "notifications/preferences",
                    "PUT",
                    json(
                        "inboxEnabled" to enabled,
                        "pushEnabled" to current.getBoolean("pushEnabled"),
                        "pushToken" to
                            current.optString("pushToken").takeUnless {
                                it == "null" || it.isBlank()
                            },
                    ),
                )
            )
        notifications = api.all("notifications")
    }

    fun history(expense: JSONObject) = work {
        renew()
        audit = api.all("expenses/${expense.getString("id")}/history")
    }

    fun clearHistory() {
        audit = emptyList()
    }

    fun revoke(invitation: JSONObject) = work {
        renew()
        api.request("groups/invitations/${invitation.getString("id")}/revoke", "POST")
        selected?.let { loadGroup(it) }
    }

    fun logout(activity: Activity) = work {
        sessionPreferences.signedOut()
        // Always clear the local session, including when the gateway is unavailable.
        try {
            if (OfflineStore.networkAvailable(context)) {
                if (preferences?.optBoolean("pushEnabled") == true) {
                    api.request(
                        "notifications/preferences",
                        "PUT",
                        json(
                            "inboxEnabled" to (preferences?.optBoolean("inboxEnabled") ?: true),
                            "pushEnabled" to false,
                            "pushToken" to null,
                        ),
                    )
                }
                refreshToken?.let { api.request("auth/logout", "POST", json("refreshToken" to it)) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Remote cleanup is best effort; explicit local sign-out must still complete.
        } finally {
            try {
                account?.let {
                    offline.dao.deactivate(it.accountKey)
                    ExpenseSync.cancel(context, it.accountKey)
                }
            } finally {
                clear()
                GoogleLogin.signOut(activity)
            }
        }
    }

    private fun clear() {
        androidx.work.WorkManager.getInstance(context)
            .cancelUniqueWork("notification-token-refresh")
        context.getSystemService(android.app.NotificationManager::class.java).cancelAll()
        outboxJob?.cancel()
        outboxJob = null
        account = null
        localExpenses = emptyList()
        showingSavedData = false
        groupDataAvailable = false
        api.accessToken = null
        api.requestUserId = ""
        api.clearRequestKeys()
        refreshToken = null
        user = null
        groups = emptyList()
        invitations = emptyList()
        notifications = emptyList()
        selected = null
        preferences = null
        entitlement = null
        groupInvitations = emptyList()
        audit = emptyList()
        remoteExpenses = emptyList()
        balances = emptyList()
        suggestions = emptyList()
        settlements = emptyList()
        destination = AppDestination.Groups
    }
}
