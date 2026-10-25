# SplitEase for Android

Native Kotlin and Jetpack Compose application for the SplitEase Spring Boot gateway. Uses Material 3 Expressive 1.5.0-beta02, expressive motion, mint/ink light and dark palettes, rounded surfaces, adaptive launcher artwork, and Material vector icons. The Expressive dependency is intentionally a beta; see the [Android release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3).

## Connect your project

1. Register `com.debarunlahiri.splitease` in your Firebase project. Register SHA-1 and SHA-256 fingerprints for each signing certificate and enable Google under Authentication → Sign-in method.
2. Download the matching `google-services.json` into `app/google-services.json`. It is ignored by Git. The Google Services Gradle plugin is applied when this file exists. Missing configuration leaves a clear sign-in error instead of a fake session.
3. Set the following Gradle properties in your user-level Gradle properties or project properties:

```properties
SPLITEASE_API_URL=http://192.168.0.135:9890/
# Optional override; otherwise read default_web_client_id from google-services.json.
SPLITEASE_GOOGLE_WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
```

HTTP and HTTPS addresses are supported. Android cleartext traffic is enabled. The default is `http://192.168.0.135:9890/`; use `http://10.0.2.2:9890/` for the Android emulator or your Mac's LAN address for a physical phone. Point the URL at the gateway root: the client adds `/api/v1/` and also accepts a configured URL already ending in `/api/v1/` without duplicating it.

4. On the identity service, configure `FIREBASE_PROJECT_ID` and Application Default Credentials (`GOOGLE_APPLICATION_CREDENTIALS` pointing to a server-only service-account file). Use the same Firebase project as Android. Apply identity migration `V3__firebase_identity.sql` through the existing Flyway process. Never put the server credential in the Android application.

Follow [Firebase's Android Google sign-in guide](https://firebase.google.com/docs/auth/android/google-signin) for provider registration. This app uses Credential Manager's explicit Google sign-in option, exchanges its credential with Firebase Authentication, and posts the resulting Firebase ID token to `POST /api/v1/auth/firebase`.

## Implemented workflows

- Google authentication, Firebase session restoration, backend access tokens and rotating refresh tokens, and logout. Backend tokens remain in memory, are refreshed before authenticated actions, and are cleared on unauthorized responses. Firebase manages its own persisted identity session; Android backup is disabled.
- Paginated group lists, creation, group details, email-based owner invitations, acceptance/decline, and owner revocation. The current backend identifies other members by UUID rather than names; the UI displays abbreviated member IDs and invitations use email addresses rather than account IDs.
- Expense creation, details, payer-only editing/deletion with confirmation, date, category, notes, participant selection, equal/exact/percentage splits, decimal validation, and change history.
- Group-currency balances, simplified payment suggestions, payment confirmation, and payment history. Recording a payment does not transfer money. The backend projects balances asynchronously, so refresh after event processing when needed.
- Notification inbox, read status, inbox preference, account details, and server entitlement display.
- Loading, error, empty states, refresh actions, bottom navigation, back handling, and keyboard-aware forms. Data is loaded from the gateway; no fabricated groups, money, or notifications are shown.

The backend returns calculated money shares even for percentage expenses. Existing percentage expenses therefore open as exact allocations, preserving their amounts; choose Percent and enter new percentages to change them.

## Source structure

- `auth/GoogleLogin.kt`: Credential Manager and Firebase authentication.
- `data/ApiClient.kt`: HTTP/HTTPS requests, bearer authentication, error handling, and shared `PageResponse` traversal.
- `ui/SplitEaseViewModel.kt`: retained session and API workflows, serialized mutations, and observable state.
- `ui/SplitEaseApp.kt`: app scaffold, transient form state, and action coordination.
- `ui/navigation/`: named destinations, screen switching, and bottom navigation.
- `ui/screens/auth/LoginScreen.kt`: Google sign-in welcome screen.
- `ui/screens/groups/GroupsScreen.kt`: groups and incoming invitations.
- `ui/screens/groups/GroupDetailScreen.kt`: group detail layout and tabs.
- `ui/screens/groups/GroupExpensesContent.kt`: group expense list.
- `ui/screens/groups/GroupBalancesContent.kt`: balances, suggestions, and payment history.
- `ui/screens/groups/GroupPeopleContent.kt`: members and owner invitation management.
- `ui/screens/activity/ActivityScreen.kt`: notification inbox.
- `ui/screens/profile/ProfileScreen.kt`: account, preferences, and entitlement.
- `ui/forms/`: separate create-group, invite-member, and expense-editor sheets.
- `ui/components/`: reusable fields, headings, cards, empty states, sheet wrapper, and payment confirmation.
- `ui/util/DisplayFormatters.kt`: currency, member labels, and optional text formatting.
- `ui/theme/Theme.kt`: light/dark Expressive theme and motion.

## Scope and verification

Kotlin files were parsed/formatted with ktfmt. XML and Maven descriptors were structurally parsed; API field names and paths were reviewed against the backend source. Neither project was built, launched, installed, migrated, or connected to Firebase or infrastructure. Dependency resolution, compilation, real Google sign-in, device appearance, and end-to-end API behavior remain unverified.

Provider setup remains external. Receipt file upload is not implemented because the backend currently accepts metadata only and has no upload/storage endpoint. Play Billing purchases, advertisements, account linking, and account deletion are not included. The app displays the existing subscription entitlement without presenting a nonfunctional purchase button. Push notifications are now supported through Firebase Cloud Messaging and Android permission handling; payment recording does not transfer money.

Authenticated writes now send `Idempotency-Key`. Pending keys are retained in memory for uncertain network/server outcomes and scoped by user; logout clears them. An explicit key can be supplied to `ApiClient.request` for a persisted action. Successful responses retire automatic keys, so intentional new submissions receive a new key. Persist an explicit action key for retries across process death.

User-facing errors use fixed audience-facing copy. SDK messages, HTTP codes, server error text, credential paths, IP addresses and exception details are not forwarded to UI banners. Credential Manager/Google sign-in cancellation is silently dismissed. Only explicit app-authored validation messages may pass through.

Google account selection opens only after the gateway/identity Firebase sign-in availability check succeeds (`GET /api/v1/auth/firebase/status`, eight-second timeout). Failures leave the chooser closed and use generic user-facing error messages. This verifies reachability and local server configuration, not external Google service availability.

Invitations now accept an email address, with email keyboard and validation. They can be created before the recipient registers and appear in their in-app inbox when they sign in with that email. This creates an in-app invitation; it does not send an email message. The profile/people instructions no longer ask users to share internal UUIDs. The backend requires group migration `V4__email_invitations.sql` and the updated `{ "email": "friend@example.com" }` request contract.

Groups, Group Details and Activity use Material pull-to-refresh gestures instead of refresh icon buttons. The list fills the available area so empty/short lists can be pulled. Refresh state is separate from other operations, resets on success/failure, and requests are ignored while an operation is already busy.

## Room offline expenses and API sync

The mobile app now has a persistent Room database (`splitease.db`), with account-scoped cached home/group snapshots and an expense outbox. New expenses are inserted into Room **before** scheduling network work. Cached groups include their known members, so a previously signed-in user can open a downloaded group and create expenses without connectivity. Local account restoration also requires the same Firebase identity to remain signed in on the device; first-time sign-in and first group download need the network.

WorkManager handles one-time network-constrained sync, exponential retry, and a 15-minute periodic recovery job for interrupted scheduling/process death. Android chooses actual execution timing; connectivity returning does not guarantee immediate background execution, and force-stopping the app suspends work until it is opened again. The foreground app reschedules sync on login/restoration and pull-to-refresh.

The worker gets a Firebase token for the same account, exchanges it for a backend JWT, calls `POST /api/v1/expenses`, and uses the Room operation UUID as `Idempotency-Key`. It marks the row synced only after receiving/validating the server result. A timeout or crash after a server commit retries the same operation/key. Temporary failures stay pending; rejected client/authorization writes are retained with a failed/sign-in-required state, rather than discarded or represented as server success. Failed rows offer a retry action. Sync-owned refresh-token sessions are logged out after an attempt.

Each queue/cache belongs to both the backend root address and backend user ID, with the expected Firebase UID recorded separately. A different signed-in user or server address cannot send another account's queue. Logout cancels its sync work and deactivates offline access, while preserving queued expenses for that identity's next successful login. No backend access/refresh tokens are stored in Room. Android backup remains disabled. Future Room schema changes require explicit migrations; destructive fallback is not enabled.

Group expense lists merge pending local rows with cached/downloaded server rows and show pending/failed state. Balances remain the last server-synced values and do not invent totals from unconfirmed writes. A never-downloaded balance is shown as unavailable. Profile, notification, preference and entitlement snapshots can be read from the last completed home refresh; individual group snapshots are saved after successful group loading.

Offline write support currently covers **new expense creation in existing cached groups**. Editing/deleting already-synced expenses, group creation, invitations, payment records, preferences and notification mutations still require a connection. Pending local expenses cannot be edited as if they had a server ID. Existing caches may be stale; server membership and validation rules remain authoritative at synchronization time. Failed expenses are retained for retry; a full edit/discard conflict-resolution workflow is separate work.

Added files are under `data/local/` (Room entities/DAO/database) and `data/offline/` (store, sync coordinator and worker). Room 2.8.4 uses KSP 2.3.4, and background sync uses WorkManager 2.11.2. The app requires rebuilding/reinstalling and dependency resolution. Room schema JSON is generated by KSP under `app/schemas` during compilation. No mobile build, installation, device run, Room instrumentation tests or end-to-end offline sync has been performed for this change.

References: [Android offline-first architecture](https://developer.android.com/topic/architecture/data-layer/offline-first), [WorkManager unique work](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work), and [Room](https://developer.android.com/training/data-storage/room).

## Invitation and expense notifications

Activity shows persistent invitation notifications and expense creation/edit/deletion notifications, with an unread badge. Expense notifications are sent to the payer and affected split participants; update/delete events include participants from both previous and current splits. Opening a notification marks it read and opens the relevant group; invitation notifications open the invitation list.

Enable **Push notifications** in Profile and grant Android notification permission when prompted. The app registers its FCM token with the existing preferences API, handles token changes via WorkManager, and displays data-only messages after checking they belong to the active Room/Firebase account. Notification IDs replace repeat deliveries instead of adding duplicate tray entries. Logout disables push when online; even if offline logout cannot update the server, this device rejects pushes for deactivated/different accounts. Android and Google Play services control background delivery, and a force-stopped app will not receive messages until reopened.

Rebuild/reinstall the app to include Firebase Messaging and the manifest service/permission/icon. Backend push delivery must be enabled and configured with Firebase Admin credentials; see the backend README. The existing backend supports one device token per user, so the most recently registered device receives push. Inbox notifications work without enabling push.

## Persistent signed-in Home and session recovery

Firebase manages its persisted Google identity; Room retains the last active account/profile and downloaded data. Startup waits for the local session check before showing Login, preventing a Login-screen flash. Cached Home remains available even when server/token refresh fails, and account recovery must match the saved Firebase/backend identity. The explicit sign-out marker is persisted separately per backend; a manually signed-out account is never automatically restored from cached data.

HTTP 401 no longer deactivates the account or clears Home. Expired/rejected refresh tokens fall back to a silent Firebase token exchange. Protected API requests can recover the session once and retry the same request/body/idempotency key. Authentication endpoints are excluded from automatic retry, and repeated 401s do not loop. If recovery requires user interaction, Home stays open and Profile offers Reconnect Google account; a different Google identity cannot silently replace the active account. Server authorization still controls all online actions, so revoked/disabled credentials do not gain access from the cache.

Only explicit Sign Out clears local account UI state and disables offline restoration. Passwords and backend bearer/refresh tokens are not stored in plain Room or SharedPreferences. Added instrumentation test sources for bounded request recovery, stable write retry keys and persisted manual sign-out. No build, device tests or live session-expiry test was run for this change.

- 2026-10-25: Successful backend login now clears stale reconnection state before local cache/scheduler maintenance. Local maintenance failures cannot prevent Home API loading. Ordinary API 401 responses no longer classify the Google identity as lost; missing, mismatched or invalid Firebase identities request reconnection. Sanitized developer diagnostics contain stage and exception class only. Source formatting checked; no build or runtime test performed.

- 2026-10-25: Fixed Room identity-hash crash with database version 2 and an explicit 1-to-2 migration for both released version-1 layouts (adds missing expense_outbox.syncedAt without deleting data). Expense observation now handles storage errors. Added migration test sources for both layouts; no build or runtime tests performed.

- Group context in notifications: new expense and settlement notification titles include the group name in the persisted inbox and push payload. Invitations already name the group. Group summaries use a protected internal endpoint; transient failures retain event retry behavior. Android Activity also resolves group names for older notifications from saved groups. Previously delivered tray notifications are unchanged. Source formatting checked; no build/run performed.
