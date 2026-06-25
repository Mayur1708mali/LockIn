---
name: vpn-service
description: Android VpnService implementation patterns for LockIn. Load when building or modifying LockInVpnService, PacketFilter, SessionWatchdog, or anything related to the VPN lock feature.
---

# VPN Service Skill

## Core Setup
```kotlin
class LockInVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        establishVpn()
        return START_STICKY  // restart if killed
    }

    private fun establishVpn() {
        val builder = Builder()
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)       // intercept all IPv4
            .addRoute("::", 0)             // intercept all IPv6
            .addDnsServer("8.8.8.8")
            .setSession("LockIn")
            .setBlocking(true)

        // Allowlist apps — they bypass VPN entirely
        allowlistManager.getAllowedPackages().forEach { pkg ->
            try {
                builder.addDisallowedApplication(pkg)
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.w("Allowlist package not found: $pkg")
                // Skip missing packages silently
            }
        }

        vpnInterface = try {
            builder.establish()
        } catch (e: Exception) {
            Timber.e(e, "Failed to establish VPN")
            stopSelf()
            null
        }
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }
}
```

## Manifest Registration
```xml
<service
    android:name=".service.LockInVpnService"
    android:permission="android.permission.BIND_VPN_SERVICE"
    android:foregroundServiceType="specialUse">
    <intent-filter>
        <action android:name="android.net.VpnService" />
    </intent-filter>
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="VPN lock for digital detox sessions" />
</service>
```

## Foreground Notification
```kotlin
private fun buildNotification(): Notification {
    val channel = NotificationChannel(
        "lockin_session",
        "Active Session",
        NotificationManager.IMPORTANCE_LOW  // silent but persistent
    )
    // Always show remaining time in notification body
    // Use accent red color: Color.parseColor("#FF3B30")
}
```

## Watchdog (JobScheduler)
```kotlin
// Runs every 30 seconds to verify VPN is alive
class SessionWatchdog : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        // 1. Check if session is ACTIVE in Room
        // 2. Check if VPN service is running
        // 3. If session active but VPN dead → restart VPN, log VPN_GAP
        // 4. If session active → log HEARTBEAT
        return false  // synchronous
    }
}
```

## Start/Stop VPN
```kotlin
// Start
val intent = Intent(context, LockInVpnService::class.java)
    .setAction(ACTION_START)
    .putExtra(EXTRA_SESSION_ID, sessionId)
context.startForegroundService(intent)

// Stop
val intent = Intent(context, LockInVpnService::class.java)
    .setAction(ACTION_STOP)
context.startService(intent)
```

## Critical Rules
- Never use a remote VPN server — local TUN interface only
- Always call `establish()` in try/catch — returns null on failure
- Always close `ParcelFileDescriptor` in `onDestroy()`
- Always run as foreground service — background VPN services get killed
- Log every VPN_GAP event to Room via `LogSessionEventUseCase`
- VPN must be stopped before session is marked COMPLETED or BROKEN
