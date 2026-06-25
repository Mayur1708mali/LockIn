# LockIn — Master Task List (Antigravity CLI)
> Use `/nexttask` in Antigravity CLI to work through tasks automatically, one by one.
> Mark tasks done by changing `[ ]` to `[x]`, or let the agent do it via `/nexttask`.

---

## PHASE 0 — Antigravity CLI Setup
> Configure tooling before writing any app code.

- [ ] **0.1** Install Antigravity CLI: `curl -fsSL https://antigravity.google/install.sh | bash` then verify with `agy --version`
- [ ] **0.2** Authenticate: `agy auth login` with your Google account
- [ ] **0.3** Install Android plugin: open Antigravity → Settings → Customizations → Build With Google Plugins → install Android bundle (installs Android CLI + skills automatically)
- [ ] **0.4** Verify Android CLI installed: `android --version`
- [ ] **0.5** Create Android project via Android CLI: `android create --package com.lockin.app --min-sdk 26 --name LockIn`
- [ ] **0.6** Run `agy inspect` in project root — verify `AGENTS.md` is loaded, skills are detected, no errors
- [ ] **0.7** Add MCP — Firebase: `/mcp add firebase` inside Antigravity session (for FCM management)
- [ ] **0.8** Add MCP — GitHub: `/mcp add github` (for version control and PR management)
- [ ] **0.9** Add MCP — Android CLI MCP: already bundled with Android plugin, verify via `agy inspect`
- [ ] **0.10** Verify all 3 MCP servers show in `agy inspect` output under "MCP servers connected"
- [ ] **0.11** Set up GitHub repo, push initial project: `git init && git remote add origin <repo-url> && git push -u origin main`
- [ ] **0.12** Run first Antigravity agent session: type "Summarize the LockIn project from AGENTS.md" — verify it reads skills correctly

---

## PHASE 1 — Project Foundation
> Gradle, dependencies, folder structure, base configuration.

- [ ] **1.1** Set up `build.gradle` (app level) — add all dependencies:
  - Jetpack Compose BOM + Material 3
  - Hilt + KSP
  - Room + KSP
  - Retrofit + OkHttp
  - Kotlin Coroutines + Flow
  - Timber
  - RootBeer (`com.scottyab:rootbeer-lib`)
  - Razorpay Android SDK (`com.razorpay:checkout`)
  - Firebase BOM + FCM
  - Jetpack Navigation Compose
  - AndroidX Biometric
  - AndroidX Security Crypto
  - Lifecycle (`collectAsStateWithLifecycle`)
- [ ] **1.2** Set up `build.gradle` (project level) — Hilt classpath, KSP plugin, Google services plugin
- [ ] **1.3** Add build flavors to `build.gradle` — `debug` uses Razorpay test key, `release` uses live key (from `local.properties`)
- [ ] **1.4** Create Firebase project at `console.firebase.google.com`, add Android app (`com.lockin.app`), download `google-services.json` to `/app`
- [ ] **1.5** Create full folder structure under `com.lockin.app/` matching AGENTS.md project structure
- [ ] **1.6** Create `LockInApp.kt` — Application class with `@HiltAndroidApp`, Timber init (debug only), root detection call on launch
- [ ] **1.7** Add all permissions to `AndroidManifest.xml`
- [ ] **1.8** Register `LockInVpnService` in `AndroidManifest.xml` with VPN intent filter and foreground service type
- [ ] **1.9** Register `AutoTopUpService`, `SessionWatchdog`, and `BootReceiver` in `AndroidManifest.xml`
- [ ] **1.10** Add `local.properties` entries for `RAZORPAY_KEY_TEST` and `RAZORPAY_KEY_LIVE` — add `local.properties` to `.gitignore`

---

## PHASE 2 — Design System
> Visual foundation. Every screen uses these tokens. Build before any UI screens.

- [ ] **2.1** Create `ui/theme/Color.kt` — all color tokens from AGENTS.md design system
- [ ] **2.2** Create `ui/theme/Type.kt` — typography scale, monospace font for `LabelSmall`
- [ ] **2.3** Create `ui/theme/Shape.kt` — 4dp buttons, 8dp cards, 0dp bottom sheets
- [ ] **2.4** Create `ui/theme/Theme.kt` — `LockInTheme` composable, dark mode only, applies color/type/shape
- [ ] **2.5** Create `ui/components/LockInButton.kt` — full-width, 4dp radius, uppercase, primary (red fill) + secondary (outline) variants
- [ ] **2.6** Create `ui/components/LockInTextField.kt` — dark-styled text input used for BREAK confirmation
- [ ] **2.7** Create `ui/components/WalletBadge.kt` — compact balance display, optional "· Auto" suffix when auto top-up is on
- [ ] **2.8** Create `ui/components/SectionHeader.kt` — monospace label + bold title, used across all screens
- [ ] **2.9** Create `ui/components/EmptyState.kt` — icon + message + CTA button
- [ ] **2.10** Create `ui/components/LoadingOverlay.kt` — full-screen loading with minimal spinner

---

## PHASE 3 — Data Layer
> Room DB, entities, DAOs, repositories. No UI yet.

- [ ] **3.1** Create `core/data/local/entity/SessionEntity.kt`
- [ ] **3.2** Create `core/data/local/entity/SessionEventEntity.kt`
- [ ] **3.3** Create `core/data/local/entity/WalletEntity.kt` — include `autoTopUpEnabled`, threshold, amount fields
- [ ] **3.4** Create `core/data/local/entity/WalletTransactionEntity.kt`
- [ ] **3.5** Create `core/data/local/converter/TypeConverters.kt` — `SessionStatus` and `TransactionType` enums
- [ ] **3.6** Create `core/data/local/dao/SessionDao.kt` — insert, update, getById, getAll, getActive (returns `Flow`)
- [ ] **3.7** Create `core/data/local/dao/SessionEventDao.kt` — insert (append-only), getBySessionId
- [ ] **3.8** Create `core/data/local/dao/WalletDao.kt` — getWallet, updateAvailableBalance, updateHeldBalance
- [ ] **3.9** Create `core/data/local/dao/WalletTransactionDao.kt` — insert, getAll as `Flow`, getBySessionId
- [ ] **3.10** Create `core/data/local/LockInDatabase.kt` — Room DB, all entities registered, version 1
- [ ] **3.11** Create `core/domain/model/` — pure Kotlin domain models: `Session`, `SessionEvent`, `Wallet`, `WalletTransaction`, `AutoTopUpConfig`
- [ ] **3.12** Create `core/domain/model/SessionStatus.kt` enum and `TransactionType.kt` enum
- [ ] **3.13** Create mapper extension functions: `SessionEntity.toDomain()`, `Session.toEntity()` (and same for all entities)
- [ ] **3.14** Create `core/domain/repository/SessionRepository.kt` interface
- [ ] **3.15** Create `core/domain/repository/WalletRepository.kt` interface
- [ ] **3.16** Create `core/data/repository/SessionRepositoryImpl.kt`
- [ ] **3.17** Create `core/data/repository/WalletRepositoryImpl.kt`
- [ ] **3.18** Create `di/DatabaseModule.kt` — Hilt module providing Room DB, all DAOs
- [ ] **3.19** Create `di/RepositoryModule.kt` — Hilt module binding interfaces to implementations

---

## PHASE 4 — Domain Layer (Use Cases)

- [ ] **4.1** Create `StartSessionUseCase.kt` — validate `availableBalance ≥ penalty`, move funds to held, create session
- [ ] **4.2** Create `CompleteSessionUseCase.kt` — move held → available, update status COMPLETED, increment streak
- [ ] **4.3** Create `BreakSessionUseCase.kt` — deduct held as penalty, update status BROKEN, reset streak
- [ ] **4.4** Create `GetActiveSessionUseCase.kt` — return active session or null
- [ ] **4.5** Create `GetWalletUseCase.kt` — return wallet as `Flow<Wallet>`
- [ ] **4.6** Create `DepositToWalletUseCase.kt` — add funds after Razorpay success, log `DEPOSIT` transaction
- [ ] **4.7** Create `WithdrawFromWalletUseCase.kt` — validate `availableBalance ≥ 5000 paise`, initiate withdrawal, log `WITHDRAWAL`
- [ ] **4.8** Create `AutoTopUpUseCase.kt` — check balance < threshold, check daily cap < 3, charge token, log `AUTO_TOPUP`
- [ ] **4.9** Create `LogSessionEventUseCase.kt` — append event to audit log (HEARTBEAT, VPN_GAP, etc.)
- [ ] **4.10** Create `GetSessionHistoryUseCase.kt` — return all sessions ordered by date
- [ ] **4.11** Create `GetTransactionHistoryUseCase.kt` — return wallet transactions as `Flow`
- [ ] **4.12** Create `GetStreakUseCase.kt` — compute current streak from session history
- [ ] **4.13** Create `SaveAutoTopUpConfigUseCase.kt` — persist Razorpay token + config to `EncryptedSharedPreferences`
- [ ] **4.14** Create `GetAutoTopUpConfigUseCase.kt` — read config from `EncryptedSharedPreferences`

---

## PHASE 5 — Security Layer

- [ ] **5.1** Create `core/security/EncryptedPrefsManager.kt` — wrapper for `EncryptedSharedPreferences`, methods: `saveToken()`, `getToken()`, `saveUserId()`, `getUserId()`, `saveAutoTopUpConfig()`, `getAutoTopUpConfig()`
- [ ] **5.2** Create `core/security/BiometricHelper.kt` — wraps `BiometricPrompt`, returns `Flow<BiometricResult>` (Success / Failure / Error)
- [ ] **5.3** Create `core/security/RootDetectionManager.kt` — wraps RootBeer, returns `RootStatus` enum
- [ ] **5.4** Integrate root detection in `LockInApp.kt` — store result in `EncryptedPrefsManager`, surface warning on home screen
- [ ] **5.5** Create `di/SecurityModule.kt` — Hilt module providing `EncryptedPrefsManager` and `BiometricHelper`

---

## PHASE 6 — Navigation

- [ ] **6.1** Create `navigation/Routes.kt` — sealed class with all route constants: `Onboarding`, `Home`, `ActiveSession`, `BreakGate`, `SessionComplete`, `Wallet`, `History`, `Settings`
- [ ] **6.2** Create `navigation/LockInNavGraph.kt` — `NavHost` with all composable destinations
- [ ] **6.3** Add launch logic — if no wallet balance → Onboarding; if active session exists → ActiveSession; else → Home
- [ ] **6.4** Create `navigation/BottomNavBar.kt` — 3 tabs: Home, History, Wallet
- [ ] **6.5** Disable back navigation during active session and break gate — intercept with `BackHandler`

---

## PHASE 7 — Onboarding Screens

- [ ] **7.1** Create `OnboardingViewModel.kt` — tracks step (1–7), VPN permission state, deposit state, auto top-up config
- [ ] **7.2** Create `OnboardingScreen.kt` — step container with dot progress indicator, animated transitions between steps
- [ ] **7.3** Build **Step 1** — Concept: "Lock your internet. Put money on the line. Break early, you pay." Full-screen, single CTA
- [ ] **7.4** Build **Step 2** — Wallet explainer: how deposits, sessions, and withdrawals work
- [ ] **7.5** Build **Step 3** — VPN permission: explain local-only, no data leaves device, handle `VpnService.prepare()` result
- [ ] **7.6** Build **Step 4** — Notification permission: `POST_NOTIFICATIONS` request, soft skip allowed
- [ ] **7.7** Build **Step 5** — First deposit: Razorpay checkout, preset amounts (₹100/₹200/₹500), cannot proceed with ₹0, save payment token on success
- [ ] **7.8** Build **Step 6** — Auto Top-Up setup: toggle (ON by default), threshold picker, amount picker, pre-filled payment method from Step 5
- [ ] **7.9** Build **Step 7** — Ready: show wallet balance, "Start Your First Session" → navigate to Home
- [ ] **7.10** Persist onboarding completion in `EncryptedPrefsManager` — never show again once complete

---

## PHASE 8 — Home Screen

- [ ] **8.1** Create `HomeViewModel.kt` — exposes wallet `Flow`, streak count, auto top-up status, session config state
- [ ] **8.2** Create `HomeScreen.kt` — wallet badge top, duration picker, penalty picker, allowlist preview row, Lock In button, streak card
- [ ] **8.3** Build `DurationPicker.kt` — horizontal scrollable preset chips (30m/1h/2h/4h/8h) + custom time input dialog
- [ ] **8.4** Build `PenaltyPicker.kt` — preset chips (₹50/₹100/₹200/₹500) + custom input, cap at wallet balance
- [ ] **8.5** Build `AllowlistPreviewRow.kt` — top 3 app icons + count, tappable → Settings
- [ ] **8.6** Build `StreakCard.kt` — streak number, label, 7-day bar (filled = completed, empty = broken/missed)
- [ ] **8.7** Handle insufficient balance + Auto Top-Up OFF → inline warning + "Add Money" shortcut
- [ ] **8.8** Handle insufficient balance + Auto Top-Up ON → show "Topping up…" state → proceed on success
- [ ] **8.9** Show amber root-detection banner if rooted (non-blocking)
- [ ] **8.10** Wire "Lock In" button → `StartSessionUseCase` → on success navigate to `ActiveSession`

---

## PHASE 9 — VPN Service

- [ ] **9.1** Create `service/LockInVpnService.kt` — extends `VpnService`, foreground service, notification channel setup
- [ ] **9.2** Implement VPN builder — `addAddress`, `addRoute` (IPv4 + IPv6), `addDnsServer`, `setBlocking(true)`
- [ ] **9.3** Implement `addDisallowedApplication()` loop with try/catch per package (skip missing packages)
- [ ] **9.4** Implement `establish()` with try/catch, store `ParcelFileDescriptor`
- [ ] **9.5** Implement foreground notification — persistent, shows countdown "LockIn active · 1h 23m remaining", accent red
- [ ] **9.6** Implement clean VPN stop — close `ParcelFileDescriptor`, stop foreground
- [ ] **9.7** Create `service/AllowlistManager.kt` — merges default allowlist + user additions from Room
- [ ] **9.8** Create `service/SessionWatchdog.kt` — `JobService`, checks VPN alive every 30s, restarts if dead, logs events
- [ ] **9.9** Implement heartbeat emission — log `HEARTBEAT` event every 30s during active session
- [ ] **9.10** Create `BootReceiver.kt` — `BroadcastReceiver`, restarts watchdog after device reboot if session was active
- [ ] **9.11** Manual test: VPN on → Chrome blocked, GPay works. Force-kill service → auto-restart within 5s.

---

## PHASE 10 — Active Session Screen

- [ ] **10.1** Create `SessionViewModel.kt` — countdown `StateFlow<Long>` (remaining ms), session state, penalty amount
- [ ] **10.2** Implement coroutine countdown timer — ticks every second, triggers `CompleteSessionUseCase` at 0
- [ ] **10.3** Create `ActiveSessionScreen.kt` — full-screen, large countdown center, penalty label below, "End Early" button pushed below fold
- [ ] **10.4** Build `CountdownTimer.kt` — `DisplayLarge` monospace text `HH:MM:SS`, subtle pulse in last 60 seconds
- [ ] **10.5** Push "End Early" below fold using `Spacer` in `LazyColumn`
- [ ] **10.6** On timer = 0 → `CompleteSessionUseCase` → stop VPN → navigate to `SessionComplete`
- [ ] **10.7** Handle foreground return mid-session — reattach to VPN service, resume countdown from correct remaining time
- [ ] **10.8** Keep screen awake during session — `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON`
- [ ] **10.9** Intercept back press during session — show "You're locked in" snackbar, do not navigate back

---

## PHASE 11 — Break-Early Friction Gate

- [ ] **11.1** Create `BreakGateViewModel.kt` — 3-step state machine, 10-second countdown for Step 1
- [ ] **11.2** Create `BreakGateScreen.kt` — container rendering correct step from ViewModel state
- [ ] **11.3** Build **Step 1 — Warning**: penalty amount (large red), time remaining, 10s countdown before "Continue" activates
- [ ] **11.4** Build **Step 2 — Biometric**: trigger `BiometricPrompt` on entry, success → Step 3, failure/cancel → back to Step 1
- [ ] **11.5** Build **Step 3 — Typed Confirmation**: exact penalty shown, `LockInTextField` requiring "BREAK" exactly, red confirm button
- [ ] **11.6** On confirm → `BreakSessionUseCase` → stop VPN → navigate to `SessionComplete(BROKEN)`
- [ ] **11.7** Handle payment failure in Step 3 — show error, keep session active, return to `ActiveSession`

---

## PHASE 12 — Session Completion Screen

- [ ] **12.1** Create `SessionCompleteViewModel.kt` — receives `SessionStatus`, fetches updated wallet balance
- [ ] **12.2** Create `SessionCompleteScreen.kt` — two layouts: COMPLETED and BROKEN
- [ ] **12.3** Build **COMPLETED state**: subtle confetti, "₹200 back in your wallet" (green), streak update, "Lock In Again" + "Withdraw to Bank" buttons
- [ ] **12.4** Build **BROKEN state**: "Session Ended Early" (amber), "₹200 penalty charged" (red), remaining balance, "Try Again" button
- [ ] **12.5** "Lock In Again" → Home, pre-fill same duration and penalty
- [ ] **12.6** "Withdraw to Bank" → Wallet screen, open withdrawal sheet automatically

---

## PHASE 13 — Wallet Screen

- [ ] **13.1** Create `WalletViewModel.kt` — wallet state Flow, transaction history Flow, withdrawal state
- [ ] **13.2** Create `WalletScreen.kt` — balance section, action buttons, transaction list
- [ ] **13.3** Build `BalanceSection.kt` — large available balance, held balance row (visible only during active session)
- [ ] **13.4** Build `AddMoneySheet.kt` — bottom sheet, presets (₹100/₹200/₹500/₹1000) + custom, Razorpay checkout on CTA
- [ ] **13.5** Build `WithdrawSheet.kt` — available balance, destination label, biometric confirm, processing time disclaimer, min ₹50
- [ ] **13.6** Build `TransactionList.kt` — grouped by date, icon by type, amount (green credit / red debit), timestamp
- [ ] **13.7** Empty state: "No transactions yet. Start your first session." with Home CTA

---

## PHASE 14 — Payment Integration (Razorpay)

- [ ] **14.1** Create `core/data/payment/RazorpayManager.kt` — wraps SDK, exposes `deposit()`, `chargeToken()`, `initiateWithdrawal()` as suspend functions
- [ ] **14.2** Implement `deposit()` — opens checkout, handles success/failure callbacks
- [ ] **14.3** Implement token saving on first deposit — save `razorpayPaymentId` to `EncryptedPrefsManager`
- [ ] **14.4** Implement `chargeToken()` — calls backend which charges Razorpay server-side (never charge from app directly)
- [ ] **14.5** Implement `initiateWithdrawal()` — calls backend withdrawal endpoint
- [ ] **14.6** Create `di/PaymentModule.kt` — Hilt module providing `RazorpayManager`
- [ ] **14.7** Test deposit with `success@razorpay` test UPI, failure with `failure@razorpay`
- [ ] **14.8** Test that live keys are NOT present in debug builds

---

## PHASE 15 — Auto Top-Up Service

- [ ] **15.1** Create `service/AutoTopUpService.kt` — `WorkManager` periodic worker
- [ ] **15.2** Implement balance check — `availableBalance < threshold` AND `enabled` AND `dailyCount < 3` AND no active session
- [ ] **15.3** Implement daily cap reset — compare `lastTopUpDate` to today, reset `dailyTopUpCount` at midnight
- [ ] **15.4** Implement silent charge → `DepositToWalletUseCase` with type `AUTO_TOPUP` on success
- [ ] **15.5** On success → notification "Wallet topped up · ₹200 added automatically"
- [ ] **15.6** On failure → notification "Auto top-up failed. Add money manually." + temporarily disable auto top-up
- [ ] **15.7** On daily cap → notification "Daily top-up limit reached."
- [ ] **15.8** Gate: never trigger during active session — check session state first
- [ ] **15.9** Enqueue worker in `LockInApp.kt` with 30-minute periodic interval

---

## PHASE 16 — Settings Screen

- [ ] **16.1** Create `SettingsViewModel.kt` — allowlist state, auto top-up config, payment method label
- [ ] **16.2** Create `SettingsScreen.kt` — sections: Allowlist, Auto Top-Up, Payment Method, Account
- [ ] **16.3** Build `AllowlistSection.kt` — default apps (non-removable), user apps (removable), "Add App" (locked during session)
- [ ] **16.4** Build `AddAppSheet.kt` — searchable installed app list, max 3 custom, counter "1/3"
- [ ] **16.5** Build `AutoTopUpSection.kt` — toggle, threshold picker, amount picker, saved method display
- [ ] **16.6** Build `PaymentMethodSection.kt` — saved method label, "Change Method" button
- [ ] **16.7** Lock all settings during active session — global amber banner "Settings locked during active session"

---

## PHASE 17 — Session History Screen

- [ ] **17.1** Create `HistoryViewModel.kt` — session history Flow, summary stats
- [ ] **17.2** Create `HistoryScreen.kt` — stats card at top, session list below
- [ ] **17.3** Build `HistorySummaryCard.kt` — total sessions, completion rate %, longest streak, total time locked in
- [ ] **17.4** Build `SessionHistoryList.kt` — grouped by week, row: date/time, duration, status badge (green/red), penalty
- [ ] **17.5** Build `SessionDetailSheet.kt` — full event timeline on row tap (HEARTBEAT, VPN_GAP, BREAK_ATTEMPT with timestamps)
- [ ] **17.6** Empty state: "No sessions yet. Start your first LockIn." with Home CTA

---

## PHASE 18 — Notifications (FCM)

- [ ] **18.1** Create `core/notification/NotificationChannels.kt` — channels: `SESSION` (high), `WALLET` (default), `SYSTEM` (low)
- [ ] **18.2** Create `core/notification/LockInNotificationManager.kt` — one function per notification type
- [ ] **18.3** Create `core/notification/LockInFirebaseService.kt` — extends `FirebaseMessagingService`, routes push to `LockInNotificationManager`
- [ ] **18.4** Register `LockInFirebaseService` in `AndroidManifest.xml`
- [ ] **18.5** Implement FCM token refresh — save to backend via `UserApi.saveFcmToken()` in `onNewToken()`
- [ ] **18.6** Test all 9 notification types (session start, halfway, 15-min, complete, VPN gap, break, auto top-up success, auto top-up failure, daily cap)

---

## PHASE 19 — Backend API Client (Android side)

- [ ] **19.1** Create `core/data/remote/api/SessionApi.kt` — Retrofit: `createSession()`, `updateSession()`, `heartbeat()`, `getSession()`
- [ ] **19.2** Create `core/data/remote/api/WalletApi.kt` — `getWallet()`, `deposit()`, `withdraw()`, `autoTopUp()`
- [ ] **19.3** Create `core/data/remote/api/UserApi.kt` — `registerDevice()`, `saveFcmToken()`, `deleteAccount()`
- [ ] **19.4** Create `core/data/remote/dto/` — request/response DTOs for all endpoints
- [ ] **19.5** Create `core/data/remote/interceptor/AuthInterceptor.kt` — attaches JWT header
- [ ] **19.6** Create `core/data/remote/interceptor/LoggingInterceptor.kt` — debug builds only
- [ ] **19.7** Create `di/NetworkModule.kt` — Hilt module providing `OkHttpClient`, `Retrofit`, all API interfaces
- [ ] **19.8** Add `BASE_URL` to `local.properties`, inject via `BuildConfig`
- [ ] **19.9** Add certificate pinning to `OkHttpClient` for release build flavor

---

## PHASE 20 — Backend Services (Server)

- [ ] **20.1** Set up backend project (Node.js + Express recommended)
- [ ] **20.2** Set up PostgreSQL — tables: `users`, `sessions`, `session_events`, `wallets`, `wallet_transactions`
- [ ] **20.3** Set up Redis — live session state + daily auto top-up counter per user
- [ ] **20.4** Implement `POST /sessions` — create session, validate wallet balance, move to held
- [ ] **20.5** Implement `PATCH /sessions/:id` — update status COMPLETED/BROKEN, settle wallet funds
- [ ] **20.6** Implement `POST /sessions/:id/heartbeat` — log heartbeat, update last-seen
- [ ] **20.7** Implement `GET /wallet` — return wallet state
- [ ] **20.8** Implement `POST /wallet/deposit` — verify Razorpay webhook, credit wallet
- [ ] **20.9** Implement `POST /wallet/withdraw` — validate balance, initiate Razorpay refund
- [ ] **20.10** Implement `POST /wallet/auto-topup` — server-side Razorpay token charge, enforce daily cap, credit wallet
- [ ] **20.11** Implement Razorpay webhook handler — verify HMAC signature on all incoming webhooks
- [ ] **20.12** Implement `POST /users/fcm-token` — save/update FCM token
- [ ] **20.13** Implement missed heartbeat cron — every 5 min, find sessions with no heartbeat > 10 min, mark BROKEN, settle penalty
- [ ] **20.14** Implement FCM push triggers — halfway, 15-min warning, completion, auto top-up events
- [ ] **20.15** Set up JWT auth — issue on device registration, verify on every request
- [ ] **20.16** Deploy backend to Railway or Render (MVP), or AWS ECS (production)
- [ ] **20.17** Set all secrets as environment variables — never hardcode anywhere

---

## PHASE 21 — Testing & QA

- [ ] **21.1** Unit tests for all use cases (mock repositories)
- [ ] **21.2** Unit tests for `SessionViewModel`, `WalletViewModel`, `BreakGateViewModel`
- [ ] **21.3** Integration tests for all Room DAOs
- [ ] **21.4** Compose UI tests for onboarding flow
- [ ] **21.5** Compose UI test for break gate — verify all 3 steps enforced, no skipping possible
- [ ] **21.6** Manual: full session end-to-end on real device — start → complete → wallet credited
- [ ] **21.7** Manual: break session early — all 3 steps, penalty charged, wallet debited
- [ ] **21.8** Manual: force-kill VPN service mid-session → verify restart within 5 seconds
- [ ] **21.9** Manual: uninstall app mid-session → verify backend settles penalty via missed heartbeat
- [ ] **21.10** Manual: reduce wallet below threshold → verify auto top-up fires silently
- [ ] **21.11** Manual: all 9 notifications appear and deep link correctly
- [ ] **21.12** Test on minimum SDK device (Android 8.0 / API 26)
- [ ] **21.13** Test on Samsung and Xiaomi — verify VPN not killed by battery optimization; prompt whitelist if needed

---

## PHASE 22 — Pre-Launch

- [ ] **22.1** Switch Razorpay test keys to live keys in release build flavor
- [ ] **22.2** Enable certificate pinning in release `OkHttpClient`
- [ ] **22.3** Remove all bare `TODO` comments or confirm each has ticket reference format `// TODO(LOCK-XXX):`
- [ ] **22.4** Set `debuggable false` in release build config
- [ ] **22.5** Enable R8/ProGuard — add rules for Razorpay SDK, Retrofit, Room, Hilt
- [ ] **22.6** Set up signing keystore, store credentials in CI secrets (not in git)
- [ ] **22.7** Write privacy policy — covers VPN (local only), wallet data, Razorpay handling
- [ ] **22.8** Create Play Store listing — screenshots, description, content rating
- [ ] **22.9** Submit for review — declare VPN usage in Play Store data safety form
- [ ] **22.10** Set up Firebase Crashlytics for production crash reporting
- [ ] **22.11** Set up uptime monitoring for backend (UptimeRobot or Datadog)

---

## SUMMARY

| Phase | Tasks | Area |
|---|---|---|
| 0 | 12 | Antigravity CLI + MCP Setup |
| 1 | 10 | Project Foundation |
| 2 | 10 | Design System |
| 3 | 19 | Data Layer |
| 4 | 14 | Domain / Use Cases |
| 5 | 5 | Security |
| 6 | 5 | Navigation |
| 7 | 10 | Onboarding |
| 8 | 10 | Home Screen |
| 9 | 11 | VPN Service |
| 10 | 9 | Active Session Screen |
| 11 | 7 | Break-Early Gate |
| 12 | 6 | Session Complete Screen |
| 13 | 7 | Wallet Screen |
| 14 | 8 | Razorpay Integration |
| 15 | 9 | Auto Top-Up Service |
| 16 | 7 | Settings Screen |
| 17 | 6 | History Screen |
| 18 | 6 | Notifications |
| 19 | 9 | Backend API Client |
| 20 | 17 | Backend Services |
| 21 | 13 | Testing & QA |
| 22 | 11 | Pre-Launch |
| **Total** | **230** | |
