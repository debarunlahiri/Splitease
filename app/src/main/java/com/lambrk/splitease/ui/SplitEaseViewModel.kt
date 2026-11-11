package com.lambrk.splitease.ui

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lambrk.splitease.auth.GoogleLogin
import com.lambrk.splitease.auth.PendingGroupInvite
import com.lambrk.splitease.auth.SessionPreferences
import com.lambrk.splitease.data.ApiClient
import com.lambrk.splitease.data.ApiException
import com.lambrk.splitease.data.AttachmentFiles
import com.lambrk.splitease.data.ExpenseAttachment
import com.lambrk.splitease.data.json
import com.lambrk.splitease.data.local.LocalAccount
import com.lambrk.splitease.data.local.CachedSnapshot
import com.lambrk.splitease.data.local.PendingExpense
import com.lambrk.splitease.data.objects
import com.lambrk.splitease.data.offline.ExpenseSync
import com.lambrk.splitease.data.offline.OfflineStore
import com.lambrk.splitease.ui.errors.UserFacingError
import com.lambrk.splitease.ui.errors.validateInput
import com.lambrk.splitease.ui.navigation.AppDestination
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
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
import org.json.JSONException
import org.json.JSONObject

class SplitEaseViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val offline = OfflineStore(context)
    private var account: LocalAccount? = null
    private var outboxJob: Job? = null
    private var draftsJob: Job? = null
    private var savedDrafts by mutableStateOf<List<CachedSnapshot>>(emptyList())
    internal val groupDrafts: List<CachedSnapshot>
        get() {
            val groupId = selected?.optString("id") ?: return emptyList()
            return savedDrafts.filter { it.snapshotKey.startsWith("expense-draft:$groupId:") }
        }
    private var localExpenses by mutableStateOf<List<PendingExpense>>(emptyList())
    var showingSavedData by mutableStateOf(false)
        private set

    var groupDataAvailable by mutableStateOf(false)
        private set

    val pendingExpenseCount: Int
        get() = localExpenses.count { it.state == "PENDING" }

    val hasPendingGroupExpenses: Boolean
        get() {
            val groupId = selected?.optString("id") ?: return false
            return localExpenses.any { it.groupId == groupId && it.state != "SYNCED" }
        }

    private val api = ApiClient()
    private val sessionPreferences = SessionPreferences(context)
    private val pendingInvite = PendingGroupInvite(context)
    private val pendingFriendInvite = PendingGroupInvite(context, host = "friend")
    private val categoryStore = com.lambrk.splitease.data.CategoryStore(context)
    private val sessionMutex = Mutex()
    var restoringSession by mutableStateOf(true)
        private set

    var needsReconnect by mutableStateOf(false)
        private set

    init {
        api.onUnauthorized = {
            recoverSession(forceRefresh = true)
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

    var friends by mutableStateOf<List<JSONObject>>(emptyList())
        private set
    var friendsLoading by mutableStateOf(false)
        private set
    var friendsLoadFailed by mutableStateOf(false)
        private set
    var friendInvitePending by mutableStateOf(false)
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
                    if (user != null && !needsReconnect) {
                        if (pendingInvite.token() != null) joinPendingInvite()
                        friendInvitePending = pendingFriendInvite.token() != null
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    // An API rejection is not proof that the persisted Google identity is lost.
                    // Reconnection is set only by silent identity recovery itself.
                    Log.w("SplitEaseSession", "Operation failed: ${exception.javaClass.simpleName}")
                    if (exception is ReconnectionRequiredException) {
                        requireReconnect()
                    }
                    error = UserFacingError.message(exception)
                } finally {
                    busy = false
                }
            }
        }
    }

    fun receiveInvite(uri: Uri?) {
        if (pendingFriendInvite.capture(uri)) {
            message =
                if (user == null) "Sign in to accept your friend invitation."
                else "Opening your friend invitation…"
            if (user != null && !restoringSession) friendInvitePending = true
            return
        }
        if (!pendingInvite.capture(uri)) return
        message =
            if (user == null) "Sign in to join your friend's group."
            else "Opening your group invitation…"
        if (user != null && !restoringSession) {
            work { renew() }
        }
    }

    private suspend fun joinPendingInvite() {
        val token = pendingInvite.token() ?: return
        renew()
        val joined =
            try {
                JSONObject(api.request("groups/invite-links/join", "POST", json("token" to token)))
            } catch (failure: ApiException) {
                if (failure.status in setOf(400, 404, 409, 422)) {
                    pendingInvite.clear(token)
                    validateInput(false) {
                        "This invite link is invalid or expired. Ask for a new link."
                    }
                }
                throw failure
            }
        val groupId = joined.getString("groupId")
        val group = api.objectAt("groups/$groupId")
        selected = group
        destination = AppDestination.Groups
        loadHome()
        loadGroup(group)
        pendingInvite.clear(token)
        message = "You're in! Welcome to ${group.getString("name")}."
    }

    fun shareInvite(share: (String) -> Unit) = work {
        val group = selected ?: return@work
        renew()
        val link = JSONObject(api.request("groups/${group.getString("id")}/invite-links", "POST"))
        // The backend returns a link on the website; older servers only return the token.
        val url =
            link.optString("url").ifBlank { ApiClient.serverBase() + "/join#" + link.getString("token") }
        share("Join ${group.getString("name")} on SplitEase: $url")
    }

    fun refreshFriends() {
        val requestedUserId = userId
        if (requestedUserId.isEmpty() || friendsLoading) return
        friendsLoading = true
        friendsLoadFailed = false
        viewModelScope.launch {
            try {
                operation.withLock {
                    if (userId != requestedUserId) return@withLock
                    try {
                        renew()
                        friends = api.all("friends")
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        if (failure is ReconnectionRequiredException) requireReconnect()
                        friendsLoadFailed = true
                        Log.w("SplitEaseSession", "Friends refresh failed: ${failure.javaClass.simpleName}")
                    }
                }
            } finally {
                friendsLoading = false
            }
        }
    }

    fun inviteFriends(input: String, done: () -> Unit) = work {
        val emails = input.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
        validateInput(
            emails.size in 1..20 &&
                emails.all { android.util.Patterns.EMAIL_ADDRESS.matcher(it).matches() }
        ) {
            "Enter up to 20 valid email addresses."
        }
        renew()
        val result =
            JSONObject(api.request("friends/invitations", "POST", json("emails" to JSONArray(emails))))
        val count = result.getInt("invited")
        message = when (count) {
            0 -> "Enter someone else's email address."
            1 -> "Friend invitation queued."
            else -> "$count friend invitations queued."
        }
        if (count > 0) done()
    }

    fun shareFriendInvite(share: (String) -> Unit) = work {
        renew()
        val result = JSONObject(api.request("friends/invite-links", "POST"))
        share("Connect with me on SplitEase: ${result.getString("inviteUrl")}")
    }

    fun dismissFriendInvite() {
        pendingFriendInvite.token()?.let(pendingFriendInvite::clear)
        friendInvitePending = false
    }

    fun acceptPendingFriendInvite() = work {
        val token = pendingFriendInvite.token() ?: return@work
        renew()
        try {
            api.request("friends/invitations/accept", "POST", json("token" to token))
        } catch (failure: ApiException) {
            if (failure.status == 403) {
                pendingFriendInvite.clear(token)
                friendInvitePending = pendingFriendInvite.token() != null
                validateInput(false) {
                    "Open this invitation while signed in with the invited email address."
                }
            }
            if (failure.status == 404 || failure.status == 409 || failure.status == 422) {
                pendingFriendInvite.clear(token)
                friendInvitePending = pendingFriendInvite.token() != null
                validateInput(false) { "This friend invitation is invalid or expired." }
            }
            throw failure
        }
        pendingFriendInvite.clear(token)
        friendInvitePending = pendingFriendInvite.token() != null
        selected = null
        destination = AppDestination.Friends
        message = "You're connected on SplitEase."
        refreshFriends()
    }

    suspend fun refreshVisibleGroup(groupId: String) {
        val group = selected?.takeIf { it.optString("id") == groupId } ?: return
        if (user == null || needsReconnect || !OfflineStore.networkAvailable(context)) return
        refreshQuietly {
            renew()
            loadGroup(group, onlyIfSelected = true)
            val updatedGroup = selected
            if (
                updatedGroup?.optString("id") == groupId &&
                    updatedGroup.toString() != group.toString()
            ) {
                localMaintenance("save updated group") { saveHome() }
            }
        }
    }

    suspend fun refreshSavedHome() {
        if (
            user == null || selected != null || !showingSavedData || needsReconnect ||
                !OfflineStore.networkAvailable(context)
        ) return
        refreshQuietly {
            renew()
            loadHome()
        }
    }

    private suspend fun refreshQuietly(refresh: suspend () -> Unit) {
        if (!operation.tryLock()) return
        try {
            refresh()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: ReconnectionRequiredException) {
            requireReconnect()
        } catch (failure: Exception) {
            Log.w("SplitEaseSession", "Background refresh failed: ${failure.javaClass.simpleName}")
        } finally {
            operation.unlock()
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
                if (user != null && firebase?.uid != saved?.firebaseUid) {
                    requireReconnect()
                    return@work
                }
                if (!OfflineStore.networkAvailable(context)) {
                    showingSavedData = user != null
                    return@work
                }
                try {
                    GoogleLogin.restore(activity)?.let { exchange(it) }
                } catch (failure: IOException) {
                    if (user == null || failure is ApiException) throw failure
                    showingSavedData = true
                } catch (failure: FirebaseNetworkException) {
                    if (user == null) throw failure
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
        draftsJob?.cancel()
        savedDrafts = emptyList()
        draftsJob = viewModelScope.launch {
            offline.dao.observeExpenseDrafts(saved.accountKey)
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    Log.w("SplitEaseSession", "Drafts unavailable: ${failure.javaClass.simpleName}")
                }
                .collect { savedDrafts = it }
        }
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
                .collect { expenses ->
                    val previouslySynced = localExpenses
                        .filter { it.state == "SYNCED" }
                        .mapTo(mutableSetOf()) { it.operationId }
                    localExpenses = expenses
                    val group = selected
                    val groupId = group?.optString("id")
                    val groupChanged = expenses.any {
                        it.groupId == groupId && it.state == "SYNCED" &&
                            it.operationId !in previouslySynced
                    }
                    if (group != null && groupChanged) {
                        refreshSelectedGroup(group)
                    }
                }
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
            validateInput(OfflineStore.internetAvailable(context)) {
                "No internet connection. Reconnect to sign in."
            }
            val serverReady = try {
                api.checkSignInAvailability()
                true
            } catch (_: IOException) {
                false
            } catch (_: JSONException) {
                false
            }
            validateInput(serverReady) {
                if (OfflineStore.internetAvailable(context)) {
                    "The SplitEase server is unavailable. Try signing in again in a moment."
                } else {
                    "No internet connection. Reconnect to sign in."
                }
            }
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
                com.lambrk.splitease.notifications.NotificationTokenWorker.schedule(context)
            }
        }
    }

    // Firebase persists its identity securely; backend tokens stay in memory and are recovered
    // silently.
    private suspend fun renew() = recoverSession(forceRefresh = false)

    private suspend fun recoverSession(forceRefresh: Boolean) = sessionMutex.withLock {
        if (!forceRefresh && api.accessToken != null) return@withLock
        var session: JSONObject? = null
        val token = refreshToken
        if (token != null) {
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
                    firebase.getIdToken(false).await().token ?: throw ReconnectionRequiredException()
                } catch (failure: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                    throw ReconnectionRequiredException()
                } catch (failure: FirebaseNetworkException) {
                    throw IOException("Unable to reach Firebase", failure)
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

    private fun requireReconnect() {
        api.accessToken = null
        refreshToken = null
        needsReconnect = user != null
        showingSavedData = false
    }

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
                    if (failure is ApiException || failure is ReconnectionRequiredException) throw failure
                    showingSavedData = true
                    message = "Showing saved data. Your expenses are safe on this device."
                }
            } finally {
                refreshing = false
            }
        }
    }

    fun refreshSelectedGroup(showIndicator: Boolean = false) {
        if (busy) return
        val group = selected ?: return
        refreshSelectedGroup(group, showIndicator)
    }

    private fun refreshSelectedGroup(group: JSONObject, showIndicator: Boolean = false) {
        val accountKey = account?.accountKey
        work {
            if (
                account?.accountKey != accountKey ||
                    selected?.optString("id") != group.optString("id")
            ) return@work
            if (showIndicator) refreshing = true
            try {
                renew()
                loadGroup(group, onlyIfSelected = true)
            } catch (failure: IOException) {
                if (failure is ApiException || failure is ReconnectionRequiredException) throw failure
                showingSavedData = true
                message = "Showing saved data. Your expenses are safe on this device."
            } finally {
                if (showIndicator) refreshing = false
            }
        }
    }

    private suspend fun loadGroup(group: JSONObject, onlyIfSelected: Boolean = false) {
        val fetchedAfter = System.currentTimeMillis()
        val id = group.getString("id")
        val currency = group.getString("defaultCurrency")
        val newExpenses = api.all("expenses/group/$id")
        val newBalances =
            JSONArray(api.request("settlements/group/$id/balances?currency=$currency")).objects()
        val newSuggestions =
            JSONArray(api.request("settlements/group/$id/suggestions?currency=$currency")).objects()
        val newSettlements = api.all("settlements/group/$id")
        val newInvitations =
            if (group.getString("createdBy") == userId) api.all("groups/$id/invitations")
            else emptyList()
        val updatedGroup = api.objectAt("groups/$id")
        if (onlyIfSelected && selected?.optString("id") != id) return

        remoteExpenses = newExpenses
        balances = newBalances
        suggestions = newSuggestions
        settlements = newSettlements
        groupInvitations = newInvitations
        selected = updatedGroup
        groups = groups.map { if (it.optString("id") == id) updatedGroup else it }
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
            }
        }
        if (!OfflineStore.networkAvailable(context)) {
            showingSavedData = true
            return@work
        }
        try {
            renew()
            loadGroup(group, onlyIfSelected = true)
        } catch (failure: IOException) {
            if (failure is ApiException || failure is ReconnectionRequiredException) throw failure
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

    fun inviteGroupFriends(friendIds: Set<String>, done: () -> Unit) = work {
        val group = selected ?: return@work
        val groupId = group.getString("id")
        renew()
        val result = JSONObject(
            api.request(
                "groups/$groupId/invitations/friends",
                "POST",
                json("friendIds" to JSONArray(friendIds.sorted())),
            )
        )
        val count = result.getInt("invited")
        message = when (count) {
            0 -> "These friends are already members or have pending invitations."
            friendIds.size -> if (count == 1) "Invitation sent." else "$count invitations sent."
            else -> "Invited $count of ${friendIds.size} friends. The others are already members or invited."
        }
        done()
        loadGroup(group, onlyIfSelected = true)
    }

    fun invitationAction(invitation: JSONObject, accept: Boolean) = work {
        renew()
        val action = if (accept) "accept" else "decline"
        api.request("groups/invitations/${invitation.getString("id")}/$action", "POST")
        loadHome()
    }

    /**
     * Categories to suggest: ones the user typed before (newest first), then those already used
     * in this group's expenses, then a few common defaults, without repeats.
     */
    fun categorySuggestions(): List<String> {
        val used =
            expenses.mapNotNull {
                if (it.isNull("category")) null else it.optString("category").trim().ifEmpty { null }
            }
        val defaults =
            listOf(
                "Food",
                "Groceries",
                "Transport",
                "Travel",
                "Rent",
                "Utilities",
                "Entertainment",
                "Shopping",
                "Health",
                "Other",
            )
        val seen = mutableSetOf<String>()
        return (categoryStore.all(userId) + used + defaults).filter {
            seen.add(it.lowercase(Locale.ROOT))
        }
    }

    /** Reuses the spelling of an existing category, otherwise capitalizes the first letter. */
    private fun canonicalCategory(raw: String): String {
        val text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return ""
        return categorySuggestions().firstOrNull { it.equals(text, ignoreCase = true) }
            ?: text.replaceFirstChar { it.uppercase() }
    }

    private fun attachmentFiles(): AttachmentFiles =
        AttachmentFiles(context, (account ?: throw ApiException(401)).accountKey)

    internal suspend fun stageAttachment(uri: Uri): ExpenseAttachment = attachmentFiles().stage(uri)

    internal suspend fun openAttachment(attachment: ExpenseAttachment) =
        attachmentFiles().open(api, attachment)

    internal suspend fun discardAttachments(attachments: List<ExpenseAttachment>) {
        attachmentFiles().discard(attachments)
    }

    internal suspend fun expenseDraft(key: String): JSONObject? {
        val saved = account ?: return null
        return offline.cached(saved.accountKey, key)
    }

    internal fun saveExpenseDraft(key: String, draft: JSONObject, done: () -> Unit) = work {
        val saved = account ?: throw ApiException(401)
        val removed = ExpenseAttachment.fromExpense(offline.cached(saved.accountKey, key)) -
            ExpenseAttachment.fromExpense(draft).toSet()
        offline.cache(saved.accountKey, key, draft)
        localMaintenance("clear removed draft attachments") { attachmentFiles().discard(removed) }
        message = "Draft saved on this device."
        done()
    }

    internal fun saveExpense(
        description: String,
        amount: String,
        currency: String,
        category: String,
        notes: String,
        splitType: String,
        date: String,
        participantIds: Set<String>,
        sharesInput: Map<String, String>,
        attachments: List<ExpenseAttachment>,
        original: JSONObject?,
        draftKey: String,
        onShareTotalMismatch: (String) -> Unit,
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
        val currencyCode = currency.trim().uppercase(Locale.ROOT)
        validateInput(runCatching { java.util.Currency.getInstance(currencyCode) }.isSuccess) {
            "Choose a valid currency, such as INR, USD or EUR."
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
        members.forEach { member ->
            val memberId = member.getString("userId")
            if (memberId !in participantIds) return@forEach
            val value =
                if (splitType == "EQUAL") BigDecimal.ZERO
                else sharesInput[memberId]?.toBigDecimalOrNull()
            validateInput(value != null && value >= BigDecimal.ZERO) {
                "Enter a valid share for every member."
            }
            shares.put(json("userId" to memberId, "value" to value))
        }
        if (splitType != "EQUAL") {
            val sum =
                shares.objects().fold(BigDecimal.ZERO) { acc, item ->
                    acc + item.getString("value").toBigDecimal()
                }
            val expected = if (splitType == "PERCENTAGE") BigDecimal(100) else total
            if (sum.compareTo(expected) != 0) {
                val target = if (splitType == "PERCENTAGE") "100%" else expected.toPlainString()
                onShareTotalMismatch("Shares must add up to $target.")
                return@work
            }
        }
        val categoryName = canonicalCategory(category)
        // Remember it so it is offered next time, in every group.
        if (categoryName.isNotEmpty()) categoryStore.remember(userId, categoryName)
        val payload =
            json(
                "groupId" to group.getString("id"),
                "paidBy" to (original?.getString("paidBy") ?: userId),
                "description" to description.trim(),
                "amount" to total,
                "currency" to currencyCode,
                "expenseDate" to date,
                "splitType" to splitType,
                "shares" to shares,
                "category" to categoryName,
                "notes" to notes.trim(),
                "receipt" to original?.optJSONObject("receipt"),
                "attachments" to JSONArray(attachments.map { it.toJson() }),
                "attachmentIds" to JSONArray(attachments.map { it.id }),
            )
        val saved = account ?: throw ApiException(401)
        val removed = ExpenseAttachment.fromExpense(offline.cached(saved.accountKey, draftKey)) -
            attachments.toSet()
        if (original == null) {
            val expense = offline.queue(saved.accountKey, payload, draftKey)
            localMaintenance("clear removed draft attachments") { attachmentFiles().discard(removed) }
            done()
            var uploaded = false
            try {
                if (OfflineStore.networkAvailable(context)) {
                    renew()
                    uploaded = ExpenseSync.uploadNow(context, offline, expense, api)
                }
            } catch (failure: IOException) {
                if (failure is ApiException || failure is ReconnectionRequiredException) throw failure
                // The queued expense can be retried when the backend is reachable again.
            } finally {
                if (!uploaded) {
                    localMaintenance("schedule expense sync") {
                        ExpenseSync.schedule(context, saved.accountKey)
                    }
                }
            }
            message =
                if (uploaded) "Expense saved"
                else "Expense saved on this device. It will sync when connected."
        } else {
            validateInput(!original.getString("id").startsWith("local:")) {
                "Wait for this expense to sync before editing."
            }
            renew()
            val files = attachmentFiles()
            files.upload(api, group.getString("id"), attachments)
            api.request("expenses/${original.getString("id")}", "PUT", payload)
            localMaintenance("clear submitted draft") {
                offline.dao.deleteSnapshot(saved.accountKey, draftKey)
                files.discard(attachments + removed)
            }
            message = "Expense saved"
            done()
            loadGroup(group, onlyIfSelected = true)
        }
    }

    fun retryExpense(operationId: String) = work {
        val saved = account ?: return@work
        offline.dao.retry(saved.accountKey, operationId)
        ExpenseSync.schedule(context, saved.accountKey)
        message = "We'll retry when a connection is available."
    }

    internal fun deleteExpense(
        expense: JSONObject,
        draftKey: String,
        attachments: List<ExpenseAttachment>,
        done: () -> Unit,
    ) = work {
        renew()
        api.request("expenses/${expense.getString("id")}", "DELETE")
        account?.let { saved ->
            localMaintenance("clear deleted expense draft") {
                val draft = ExpenseAttachment.fromExpense(offline.cached(saved.accountKey, draftKey))
                offline.dao.deleteSnapshot(saved.accountKey, draftKey)
                attachmentFiles().discard(attachments + draft)
            }
            localExpenses
                .filter {
                    it.state == "SYNCED" &&
                        it.serverJson?.let(::JSONObject)?.optString("id") == expense.getString("id")
                }
                .forEach { offline.dao.removeSynced(saved.accountKey, it.operationId) }
        }
        done()
        selected?.let { loadGroup(it) }
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
        when (item.optString("type")) {
            "GROUP_INVITATION" -> {
                selected = null
                destination = AppDestination.Groups
            }
            "FRIEND_ADDED" -> {
                selected = null
                destination = AppDestination.Friends
            }
            else -> groups
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

    internal suspend fun availablePlans(): List<JSONObject> =
        JSONArray(api.request("subscriptions/plans")).objects()

    fun markAllNotificationsRead() = work {
        renew()
        api.request("notifications/read-all", "PATCH")
        notifications = notifications.map {
            if (it.optBoolean("read")) it else JSONObject(it.toString()).put("read", true)
        }
        localMaintenance("save notification read status") { saveHome() }
    }

    fun updateDisplayName(input: String) = work {
        val name = input.trim()
        validateInput(
            name.length in 2..100 &&
                name.none { Character.isISOControl(it) || it == '\u2028' || it == '\u2029' }
        ) { "Enter a display name between 2 and 100 characters on one line." }
        renew()
        val updated = JSONObject(api.request("users/me", "PATCH", json("displayName" to name)))
        user = updated
        account?.let { saved ->
            localMaintenance("save updated profile") {
                offline.dao.saveAccount(saved.copy(userJson = updated.toString()))
            }
        }
        message = "Display name updated."
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

    /** Emails a pending invitation again and extends its expiry. */
    fun resendInvitation(invitation: JSONObject) = work {
        renew()
        api.request("groups/invitations/${invitation.getString("id")}/resend", "POST")
        selected?.let { loadGroup(it) }
        message = "Invitation sent again"
    }

    fun logout(activity: Activity) = work {
        sessionPreferences.signedOut()
        try {
            if (OfflineStore.internetAvailable(context)) {
                if (preferences?.optBoolean("pushEnabled") == true) {
                    api.request(
                        "notifications/preferences",
                        "PUT",
                        json(
                            "inboxEnabled" to (preferences?.optBoolean("inboxEnabled") ?: true),
                            "pushEnabled" to false,
                            "pushToken" to null,
                        ),
                        timeoutSeconds = 8,
                    )
                }
                refreshToken?.let {
                    api.request("auth/logout", "POST", json("refreshToken" to it), timeoutSeconds = 8)
                }
            } else {
                message = "Signed out on this device. Server sign-out was skipped because there’s no internet connection."
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            message = "Signed out on this device. SplitEase couldn’t confirm sign-out with the server."
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
        draftsJob?.cancel()
        draftsJob = null
        savedDrafts = emptyList()
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
        friends = emptyList()
        friendsLoadFailed = false
        friendInvitePending = false
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
