# LockIn Backend Server — Developer Testing Guide

This guide details how to verify, configure, and mock backend integration endpoints locally during development.

---

## 1. Environment Configurations (`.env`)

Create a `.env` file inside the `server/` directory. For local development, copy from `.env.example`:

```env
PORT=3000
NODE_ENV=development

# Database connections
DATABASE_URL=postgres://postgres:postgres@localhost:5432/lockin
REDIS_URL=redis://127.0.0.1:6379

# JWT Cryptography
JWT_SECRET=super_secret_development_token_signing_key_123!
JWT_EXPIRES_IN=30d

# Razorpay Sandbox Credentials
RAZORPAY_KEY_ID=rzp_test_your_sandbox_key
RAZORPAY_KEY_SECRET=your_sandbox_secret
RAZORPAY_WEBHOOK_SECRET=your_webhook_signature_secret

# Firebase configurations (FCM)
FIREBASE_SERVICE_ACCOUNT_PATH=configs/firebase-service-account.json
```

---

## 2. Booting Services Locally

If you use Docker, run the core dependencies instantly:

```bash
# Start PostgreSQL and Redis instances
docker run --name lockin-db -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=lockin -p 5432:5432 -d postgres
docker run --name lockin-redis -p 6379:6379 -d redis
```

Boot the server:
```bash
npm install
npm run dev
```

---

## 3. Simulating Razorpay Payments

When testing deposits and withdrawals, you do not need real transactions. You can trigger/mock endpoints using `curl` or Postman.

### A. Mocking Manual Deposit Verification (`POST /wallet/deposit`)
When the client starts a manual deposit, it sends a Razorpay Payment ID.
To test, you can send:
* **Headers:** `Authorization: Bearer <JWT_TOKEN>`
* **Body:**
```json
{
  "razorpayPaymentId": "pay_test_deposit_001",
  "amountPaise": 50000
}
```

*Note: The server will verify this ID by making a GET request to Razorpay. During development, you can mock the response or use a real sandbox Payment ID.*

### B. Simulating Webhooks (`POST /wallet/webhook`)
Razorpay triggers webhooks when payments are captured. To simulate a webhook without signature failures:
1. Set `RAZORPAY_WEBHOOK_SECRET=mock_webhook_secret` in `.env`.
2. Generate the HMAC-SHA256 signature using the raw payload. For example, in node:
```javascript
const crypto = require('crypto');
const payload = JSON.stringify({
  event: "payment.captured",
  payload: {
    payment: {
      entity: {
        id: "pay_test_webhook_002",
        amount: 50000,
        currency: "INR",
        notes: { userId: "user_abc" }
      }
    }
  }
});
const signature = crypto.createHmac('sha256', 'mock_webhook_secret').update(payload).digest('hex');
console.log("X-Razorpay-Signature:", signature);
```
3. Dispatch the payload:
```bash
curl -X POST http://localhost:3000/wallet/webhook \
  -H "Content-Type: application/json" \
  -H "X-Razorpay-Signature: <computed_signature>" \
  -d '{
    "event": "payment.captured",
    "payload": {
      "payment": {
        "entity": {
          "id": "pay_test_webhook_002",
          "amount": 50000,
          "currency": "INR",
          "notes": { "userId": "user_abc" }
        }
      }
    }
  }'
```

---

## 4. Auditing Watchdog Cron Sweeps

The watchdog background tasks monitor focus sessions every minute.

### A. Active Session Temporal Alerts
The server scans for sessions in `ACTIVE` state:
- **Halfway Mark**: Once current time crosses 50% of session duration, a `SESSION_HALFWAY` FCM warning is triggered.
- **15-Min Warning**: Once current time is within 15 minutes of completion, a `SESSION_15MIN_WARNING` warning is triggered.

*In development, set small session durations (e.g. 5 minutes total) to verify these events trigger.*

### B. Missed Heartbeat Watchdog
Every 5 minutes, the server scans for `ACTIVE` sessions that have missed heartbeats for over 10 minutes:
- Clears active session in Redis cache.
- Marks session `BROKEN` in PostgreSQL.
- Consumes the held penalty stakes permanently.
- Logs `BREAK_CONFIRMED` event and `PENALTY` ledger record.
- Sends an FCM push to the device.

To force-test this:
1. Insert an active session into database.
2. Ensure no heartbeat events are logged in the last 10 minutes.
3. Observe console logs for the watchdog break confirmation print statements.

---

## 5. FCM Push Logs

If your Firebase service account file is not initialized in `configs/firebase-service-account.json`, the server defaults to **Mock Mode**.
You will see output directly in the server console:

```text
[FCM Mock Log] Send push to user user_abc (Token: fcm_token_xyz):
  Title: Halfway There!
  Body: You have completed 50% of your focus session. Stay strong!
  Data: { type: 'SESSION_HALFWAY', sessionId: 'session_123' }
```
Use these logs to verify that the correct notification payloads are being compiled and triggered at the appropriate times.
