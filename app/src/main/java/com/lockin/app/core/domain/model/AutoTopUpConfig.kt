package com.lockin.app.core.domain.model

/**
 * Pure Kotlin domain model representing the configurations for silent automatic wallet top-up payments.
 */
data class AutoTopUpConfig(
    val autoTopUpEnabled: Boolean,
    val autoTopUpThresholdPaise: Int,
    val autoTopUpAmountPaise: Int,
    val savedPaymentMethodLabel: String?
)
