# SplitEase Android status

Updated 2026-10-25. DONE means source implementation and structural checks only.

| Status | Work |
|---|---|
| DONE | Native Compose navigation, Material 3 Expressive theme, motion, light/dark palettes, and vector icons |
| DONE | Credential Manager Google sign-in, Firebase authentication, backend session exchange, refresh and logout |
| DONE | Groups, owner invitations and revocation, invitation acceptance and decline |
| DONE | Expenses, participants, dates, equal/exact/percentage splits, editing, deletion and audit history |
| DONE | Group balances, payment suggestions, recording confirmation, settlement history |
| DONE | Activity inbox, read state, inbox preference, profile, entitlement display |
| DONE | Pagination traversal, loading/error/empty states, and setup documentation |
| DONE | Split UI into feature screens, group sections, form sheets, named navigation, reusable components, and display utilities |
| PENDING CONFIGURATION | Firebase Android registration, fingerprints, Google provider and google-services.json |
| PENDING CONFIGURATION | HTTPS gateway address, server Firebase credentials and identity migration |
| UNVERIFIED | Dependency resolution, compilation, device layout, real authentication and API workflows |

Receipt upload requires a backend upload API. Push delivery and Play Billing require separate provider integration. They are not represented as completed features.

2026-10-25: Refactored screen files and reviewed package paths, unique declarations, named navigation and internal imports. Kotlin parsing/formatting checked with ktfmt; compilation and runtime behavior remain unverified.

- 2026-10-25: Enabled HTTP API access and Android cleartext traffic. Preserved the configured LAN host at `192.168.0.135:9890` and normalized optional `/api/v1/` suffixes to avoid duplicate API paths. Source checks only; no build or launch performed.

- 2026-10-25: Replaced raw SDK/network/server messages with a central user-facing error mapper. Sign-in cancellation is silent; app-authored validation remains readable. API error response text and status numbers are no longer displayed to users. No app build or launch performed.

- 2026-10-25: Replaced Groups, Group Details and Activity refresh buttons with a reusable Material pull-to-refresh list. Added refresh-specific state, prevented duplicate requests while busy, and removed the duplicate top loading bar during pull refresh. Source checks only; no build/launch performed.

- 2026-10-25: DONE (source checks): Added Room account/snapshot/expense-outbox storage, offline restoration and cached group access, queue-first expense creation, stable idempotency keys, account/server-isolated WorkManager sync with retry and periodic recovery, pending/failed UI, and saved-balance labeling. Added six Room instrumentation test sources for stable keys, failed/synced states, account isolation, snapshot cleanup and sign-out preservation; compilation, generated schema verification, device restart persistence and live network recovery remain unverified.

- 2026-10-25: Fixed repeated login redirects: HTTP 401 preserves Home/Room data, protected requests recover/retry once with the same key, refresh rejection falls back to Firebase, startup shows session loading, and persisted manual sign-out controls cache restoration. Added Profile reconnection and three instrumentation test sources. Source checks only; no build/device run.

- 2026-10-25: Successful backend login now clears stale reconnection state before local cache/scheduler maintenance. Local maintenance failures cannot prevent Home API loading. Ordinary API 401 responses no longer classify the Google identity as lost; missing, mismatched or invalid Firebase identities request reconnection. Sanitized developer diagnostics contain stage and exception class only. Source formatting checked; no build or runtime test performed.

- 2026-10-25: Fixed Room identity-hash crash with database version 2 and an explicit 1-to-2 migration for both released version-1 layouts (adds missing expense_outbox.syncedAt without deleting data). Expense observation now handles storage errors. Added migration test sources for both layouts; no build or runtime tests performed.
