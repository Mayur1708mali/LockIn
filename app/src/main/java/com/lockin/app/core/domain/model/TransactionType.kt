package com.lockin.app.core.domain.model

/**
 * Represents the type of wallet transaction for auditing and tracking.
 */
enum class TransactionType {
    /**
     * Manual deposit of money via Razorpay.
     */
    DEPOSIT,

    /**
     * Automatic top-up payment charged silently from saved method.
     */
    AUTO_TOPUP,

    /**
     * Deducting penalty amount from available balance to held balance when starting a session.
     */
    SESSION_HOLD,

    /**
     * Returning held balance to available balance upon successful session completion.
     */
    SESSION_RELEASE,

    /**
     * Permanently losing the held balance upon breaking a session.
     */
    PENALTY,

    /**
     * Manual withdrawal of money to a bank account.
     */
    WITHDRAWAL
}
