# LockIn — Antigravity CLI Project Instructions

You are an expert Android developer building **LockIn**, a digital detox app. Write production-quality, well-structured Kotlin code. Follow every instruction here precisely and consistently across all files.

---

## App Overview

**Name:** LockIn
**Tagline:** Your focus, on the line.
**Platform:** Android (API 26+ / Android 8.0 minimum)
**Language:** Kotlin
**Purpose:** A hard-lock digital detox app. The user deposits money into an in-app LockIn Wallet. When starting a session, the penalty amount is deducted from the wallet balance and held. If the user completes the session, the held amount is returned to the wallet — NOT automatically refunded to their bank. The user can choose to either use the wallet balance for future sessions or manually request a bank refund. If the user breaks early, the penalty amount is permanently deducted. The app activates a local VPN that blocks all internet traffic except whitelisted payment apps. Auto Top-Up: when wallet balance falls below a configurable threshold, the app automatically charges the user's saved Razorpay payment method silently in the background.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture | MVVM + Clean Architecture |
| VPN | Android VpnService API |
| Local DB | Room |
| DI | Hilt |
| Async | Kotlin Coroutines + Flow |
| Payments | Razorpay Android SDK |
| Notifications | Firebase Cloud Messaging (FCM) |
| Navigation | Jetpack Navigation Compose |
| Security | Android Keystore, EncryptedSharedPreferences |
| Backend sync | Retrofit + OkHttp (REST) |

---

## Project Structure

```
com.lockin.app/
├── core/
│   ├── data/
│   │   ├── local/          # Room DB, DAOs, entities
│   │   ├── remote/         # Retrofit API interfaces, DTOs
│   │   └── repository/     # Repository implementations
│   ├── domain/
│   │   ├── model/          # Domain models
│   │   ├── repository/     # Repository interfaces
│   │   └── usecase/        # Use cases
│   └── util/               # Extensions, constants, helpers
├── feature/
│   ├── onboarding/
│   ├── home/
│   ├── session/
│   ├── history/
│   ├── settings/
│   ├── wallet/
│   └── payment/
├── service/
│   ├── LockInVpnService.kt
│   ├── PacketFilter.kt
│   ├── SessionWatchdog.kt
│   └── AutoTopUpService.kt
├── di/
├── navigation/
└── LockInApp.kt
```

---

## Design System

**Mode:** Dark only. Never generate light mode.
**Style:** Minimalist, high-contrast, no decorative elements.

### Colors
```
Background     = #0D0D0D
Surface        = #1C1C1E
SurfaceVariant = #2C2C2E
Outline        = #48484A
OnSurface      = #F5F5F7
OnSurfaceMuted = #8E8E93
Accent         = #FF3B30   // red — stakes, urgency
AccentAmber    = #FF9500   // warnings
AccentGreen    = #34C759   // success
AccentBlue     = #0A84FF   // info
```

### Design Rules
- No gradients. Flat colors only.
- No rounded corners > 8dp on cards. 4dp on buttons.
- No illustrations. Material Icons only.
- 8dp grid padding (use 16dp, 24dp, 32dp).
- Full-width uppercase buttons, 4dp radius.
- All destructive actions use Accent red.
- Empty states always include a CTA.
- Error messages state what went wrong AND what to do.

---

## Core Features

### VPN Lock
- Extends Android `VpnService`
- Local TUN interface — no remote server
- `addDisallowedApplication()` for allowlisted apps
- Default allowlist: `com.google.android.apps.nbu.paisa.user`, `com.phonepe.app`, `net.one97.communications`, `in.org.npci.upiapp`, `com.android.emergency`
- Run as foreground service with persistent countdown notification
- Auto-restart within 5s if killed, log `VPN_GAP` event

### Session Lifecycle
`PENDING → ACTIVE → COMPLETED / BROKEN`
- Start: deduct penalty from `availableBalance` → `heldBalance`
- Complete: move `heldBalance` → `availableBalance`
- Break: `heldBalance` permanently consumed

### Break-Early Gate (3 steps, no skipping)
1. Warning + 10-second forced wait
2. BiometricPrompt (no PIN fallback)
3. User types "BREAK" exactly, then confirms

### Wallet
- `availableBalance` — spendable and withdrawable
- `heldBalance` — locked during active session
- Auto Top-Up: silent charge when `availableBalance < threshold`, max 3/day
- Manual withdrawal: biometric-gated, min ₹50, 3–5 day processing

---

## Data Models

```kotlin
enum class SessionStatus { PENDING, ACTIVE, COMPLETED, BROKEN }
enum class TransactionType { DEPOSIT, AUTO_TOPUP, SESSION_HOLD, SESSION_RELEASE, PENALTY, WITHDRAWAL }

@Entity data class Session(
    @PrimaryKey val sessionId: String,
    val userId: String,
    val status: SessionStatus,
    val startTime: Long,
    val targetEndTime: Long,
    val actualEndTime: Long?,
    val penaltyAmount: Int,   // paise
    val currency: String = "INR",
    val walletTxHoldId: String?,
    val allowlistVersion: Int,
    val platform: String = "android"
)

@Entity data class Wallet(
    @PrimaryKey val userId: String,
    val availableBalance: Int,
    val heldBalance: Int,
    val totalDeposited: Int,
    val totalPenaltiesPaid: Int,
    val autoTopUpEnabled: Boolean,
    val autoTopUpThresholdPaise: Int,
    val autoTopUpAmountPaise: Int,
    val lastUpdated: Long
)

@Entity data class WalletTransaction(
    @PrimaryKey val txId: String,
    val userId: String,
    val type: TransactionType,
    val amount: Int,
    val direction: String,   // "CREDIT" or "DEBIT"
    val sessionId: String?,
    val description: String,
    val timestamp: Long
)

@Entity data class SessionEvent(
    @PrimaryKey val eventId: String,
    val sessionId: String,
    val eventType: String,   // HEARTBEAT, VPN_GAP, BREAK_ATTEMPT, BREAK_CONFIRMED, COMPLETED
    val timestamp: Long,
    val metadata: String?
)
```

---

## Security Rules

- Sensitive data (tokens, user ID) → `EncryptedSharedPreferences` only
- Session state → Android Keystore
- No raw card data stored or logged anywhere
- Biometric required for break confirmation and withdrawal — no PIN fallback
- Root detection via RootBeer on launch — warn but don't block
- TLS + certificate pinning for all API calls (release builds)

---

## Coding Standards

- Single responsibility per file
- Sealed UI state: `Loading`, `Success(data)`, `Error(message)`
- ViewModels expose `StateFlow<UiState>` only — never mutable state
- ViewModels never access DAOs or APIs directly — repository only
- Use cases: single `operator fun invoke()` function
- No hardcoded strings — `strings.xml`
- No hardcoded colors — `MaterialTheme.colorScheme`
- Handle all coroutine exceptions explicitly
- Log with `Timber` — never `Log.d`
- TODOs: `// TODO(LOCK-123): description`

---

## AndroidManifest Permissions

```xml
<uses-permission android:name="android.permission.BIND_VPN_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
```

---

## What Not To Do

- Do not use XML layouts — Compose only
- Do not use LiveData — StateFlow/Flow only
- Do not store card details anywhere
- Do not auto-refund to bank on completion — credit wallet first
- Do not allow withdrawal of `heldBalance`
- Do not allow session start if `availableBalance` < `penaltyAmount`
- Do not trigger Auto Top-Up during active session
- Do not allow > 3 Auto Top-Ups per day
- Do not store Razorpay token in Room — EncryptedSharedPreferences only
- Do not allow allowlist edits during active session
- Do not skip the 3-step break gate
- Do not use hardcoded colors or strings
- Do not use remote VPN server — local TUN only
- Do not use deprecated Android APIs

---

## Code Generation Rules

1. State the file path at the top of every response
2. Generate complete files — never partial
3. After each file, list what needs wiring next
4. Ask one specific question if a decision is needed
5. Comment every function with what it does and why
