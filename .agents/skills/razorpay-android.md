---
name: razorpay-android
description: Razorpay Android SDK integration patterns for LockIn. Load when implementing any payment, deposit, token charge, or withdrawal functionality.
---

# Razorpay Android Skill

## SDK Setup
```kotlin
// build.gradle (app)
implementation 'com.razorpay:checkout:1.6.33'
```

## Checkout (Manual Deposit)
```kotlin
val checkout = Checkout()
checkout.setKeyID(BuildConfig.RAZORPAY_KEY_ID) // from BuildConfig, never hardcoded

val options = JSONObject().apply {
    put("name", "LockIn")
    put("description", "Wallet Top-Up")
    put("currency", "INR")
    put("amount", amountInPaise)  // always in paise
    put("prefill", JSONObject().apply {
        put("contact", userPhone)
    })
}
checkout.open(activity, options)
```

## Callbacks
```kotlin
// Activity must implement PaymentResultListener
override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
    // Save razorpayPaymentId as token in EncryptedSharedPreferences
    // Call DepositToWalletUseCase
}

override fun onPaymentError(errorCode: Int, errorDescription: String?, paymentData: PaymentData?) {
    // Show user-friendly error
    // Do NOT modify wallet balance
}
```

## Token Storage (Auto Top-Up)
```kotlin
// CORRECT — EncryptedSharedPreferences only
encryptedPrefs.edit().putString("razorpay_token", paymentId).apply()

// WRONG — never in Room, never in plain SharedPreferences
// walletDao.saveToken(paymentId)  ← NEVER DO THIS
```

## Amount Rules
- Always work in paise internally (₹1 = 100 paise)
- Convert to rupees only for display: `amountInPaise / 100`
- Minimum deposit: ₹50 = 5000 paise
- Minimum withdrawal: ₹50 = 5000 paise

## Build Flavors (Keys)
```kotlin
// build.gradle
buildTypes {
    debug {
        buildConfigField "String", "RAZORPAY_KEY_ID", '"rzp_test_xxxx"'
    }
    release {
        buildConfigField "String", "RAZORPAY_KEY_ID", '"rzp_live_xxxx"'
    }
}
// Never commit live keys to git — use local.properties
```

## Error Codes
- `0` — Network error
- `1` — Invalid options
- `2` — Payment cancelled by user → do not show error, silently handle
- `3` — Payment failed → show error with retry option
