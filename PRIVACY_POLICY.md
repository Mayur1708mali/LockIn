# Privacy Policy — LockIn Digital Detox

**Last Updated:** June 27, 2026

LockIn ("we", "our", or "us") is dedicated to protecting your digital privacy while helping you manage your focus. This Privacy Policy describes how the LockIn Android application and its associated backend services collect, use, store, and safeguard your information.

---

## 1. Local VPN Service (`VpnService` API)

LockIn utilizes Android's native `VpnService` API to establish a local Virtual Private Network (VPN) tunnel on your device.

*   **Local-Only Routing**: The VPN service operates **entirely locally** on your device. It runs a local TUN interface that intercepts outgoing network traffic to block access during focus sessions.
*   **No Remote Routing**: **None** of your network traffic is routed through external proxy servers or remote hosting services.
*   **Zero Logs or Interception**: LockIn **does not** inspect, collect, store, decrypt, or transmit any network contents, websites visited, or application usage data passing through the local tunnel.
*   **Allowlist Exception**: LockIn allowlists specific system and payment applications (e.g., Google Pay, PhonePe, Paytm, NPCI BHIM, and Emergency services) to ensure you can complete auto top-ups and make critical calls during lock sessions.

---

## 2. Wallet & Payment Processing

LockIn charges financial stakes to enforce focus. All payments are processed through Razorpay's secure SDK.

*   **Zero Card Storage**: LockIn **never** collects, logs, or stores your credit/debit card numbers, CVVs, or bank login credentials on our servers or inside local storage.
*   **Secure Tokens**: Payment credentials are tokenized directly by Razorpay. LockIn only stores an opaque, encrypted customer token (inside Android `EncryptedSharedPreferences`) to execute silent auto-topup charges in the background when your wallet drops below your configured threshold.
*   **Refund Processing**: Withdrawals are processed as standard refunds targeting your latest recorded deposit gateway transaction.

---

## 3. Permissions Requested

LockIn requests the following permissions to support core detox features:
*   `BIND_VPN_SERVICE`: Needed to establish the local TUN connection.
*   `USE_BIOMETRIC`: Required to gate early session termination and wallet withdrawal commands. No PIN fallback is allowed.
*   `RECEIVE_BOOT_COMPLETED`: Restarts the focus watchdog service if your phone rebooted during an active session.
*   `POST_NOTIFICATIONS`: Delivers persistent countdown updates and status notices.
*   `QUERY_ALL_PACKAGES`: Used to locate installed payment applications to keep them whitelisted.

---

## 4. Security Practices

We implement industry-standard controls to protect your device:
*   **Keystore Protection**: Active focus sessions and lifecycle markers are guarded by Android's hardware-backed Keystore system.
*   **Encrypted Storage**: Sensitive data (auth tokens, Razorpay customer tokens, user identifiers) is kept strictly inside `EncryptedSharedPreferences`.
*   **Secure API Communication**: All backend REST communications are encrypted using HTTPS (TLS 1.3) with Certificate Pinning enabled in release builds to block Man-in-the-Middle (MitM) attacks.

---

## 5. Contact Us

If you have any questions regarding this privacy policy or our local VPN routing, contact us at `support@lockin.app`.
