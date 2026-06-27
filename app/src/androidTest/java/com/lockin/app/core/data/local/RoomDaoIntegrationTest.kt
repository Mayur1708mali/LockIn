/*
 * File: app/src/androidTest/java/com/lockin/app/core/data/local/RoomDaoIntegrationTest.kt
 * Purpose: Android instrumented integration tests for Room database DAOs.
 */

package com.lockin.app.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lockin.app.core.data.local.dao.AllowedAppDao
import com.lockin.app.core.data.local.dao.SessionDao
import com.lockin.app.core.data.local.dao.SessionEventDao
import com.lockin.app.core.data.local.dao.WalletDao
import com.lockin.app.core.data.local.dao.WalletTransactionDao
import com.lockin.app.core.data.local.entity.AllowedAppEntity
import com.lockin.app.core.data.local.entity.SessionEntity
import com.lockin.app.core.data.local.entity.SessionEventEntity
import com.lockin.app.core.data.local.entity.WalletEntity
import com.lockin.app.core.data.local.entity.WalletTransactionEntity
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.core.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RoomDaoIntegrationTest {

    private lateinit var db: LockInDatabase
    private lateinit var walletDao: WalletDao
    private lateinit var sessionDao: SessionDao
    private lateinit var sessionEventDao: SessionEventDao
    private lateinit var walletTransactionDao: WalletTransactionDao
    private lateinit var allowedAppDao: AllowedAppDao

    private val userId = "integration_test_user"

    @Before
    fun createDb() {
        // Build a temporary in-memory database instance on the test context
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LockInDatabase::class.java
        ).allowMainThreadQueries().build()

        walletDao = db.walletDao()
        sessionDao = db.sessionDao()
        sessionEventDao = db.sessionEventDao()
        walletTransactionDao = db.walletTransactionDao()
        allowedAppDao = db.allowedAppDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testWalletDaoOperations() = runBlocking {
        val wallet = WalletEntity(
            userId = userId,
            availableBalance = 50000, // ₹500
            heldBalance = 0,
            totalDeposited = 50000,
            totalPenaltiesPaid = 0,
            autoTopUpEnabled = true,
            autoTopUpThresholdPaise = 10000,
            autoTopUpAmountPaise = 20000,
            lastUpdated = System.currentTimeMillis()
        )

        // Insert wallet
        walletDao.insertOrUpdateWallet(wallet)

        // Retrieve wallet
        val dbWallet = walletDao.getWallet(userId)
        assertNotNull(dbWallet)
        assertEquals(50000, dbWallet?.availableBalance)

        // Update balances
        walletDao.updateAvailableBalance(userId, 30000, System.currentTimeMillis())
        walletDao.updateHeldBalance(userId, 20000, System.currentTimeMillis())

        val updatedWallet = walletDao.getWalletFlow(userId).first()
        assertEquals(30000, updatedWallet?.availableBalance)
        assertEquals(20000, updatedWallet?.heldBalance)
    }

    @Test
    fun testSessionDaoOperations() = runBlocking {
        val session = SessionEntity(
            sessionId = "session_999",
            userId = userId,
            status = SessionStatus.ACTIVE,
            startTime = System.currentTimeMillis(),
            targetEndTime = System.currentTimeMillis() + 600000,
            actualEndTime = null,
            penaltyAmount = 10000,
            walletTxHoldId = "tx_hold_999",
            allowlistVersion = 1
        )

        // Insert session
        sessionDao.insertSession(session)

        // Verify active session exists
        var activeSession = sessionDao.getActiveSession()
        assertNotNull(activeSession)
        assertEquals("session_999", activeSession?.sessionId)

        // Update session state
        val completedSession = session.copy(
            status = SessionStatus.COMPLETED,
            actualEndTime = System.currentTimeMillis()
        )
        sessionDao.updateSession(completedSession)

        // Verify active session is now null
        activeSession = sessionDao.getActiveSession()
        assertNull(activeSession)

        // Retrieve by ID
        val dbSession = sessionDao.getSessionById("session_999")
        assertEquals(SessionStatus.COMPLETED, dbSession?.status)
    }

    @Test
    fun testSessionEventDaoOperations() = runBlocking {
        val event = SessionEventEntity(
            eventId = "evt_001",
            sessionId = "session_999",
            eventType = "HEARTBEAT",
            timestamp = System.currentTimeMillis(),
            metadata = "VPN running healthy"
        )

        // Insert event
        sessionEventDao.insertEvent(event)

        // Fetch events
        val events = sessionEventDao.getEventsForSession("session_999")
        assertEquals(1, events.size)
        assertEquals("evt_001", events[0].eventId)
        assertEquals("HEARTBEAT", events[0].eventType)
    }

    @Test
    fun testWalletTransactionDaoOperations() = runBlocking {
        val tx1 = WalletTransactionEntity(
            txId = "tx_001",
            userId = userId,
            type = TransactionType.DEPOSIT,
            amount = 50000,
            direction = "CREDIT",
            sessionId = null,
            description = "Manual credit",
            timestamp = System.currentTimeMillis() - 10000
        )
        val tx2 = WalletTransactionEntity(
            txId = "tx_002",
            userId = userId,
            type = TransactionType.AUTO_TOPUP,
            amount = 20000,
            direction = "CREDIT",
            sessionId = null,
            description = "Auto topup",
            timestamp = System.currentTimeMillis()
        )

        // Insert transactions
        walletTransactionDao.insertTransaction(tx1)
        walletTransactionDao.insertTransaction(tx2)

        // Fetch all transactions flow
        val list = walletTransactionDao.getAllTransactionsFlow().first()
        assertEquals(2, list.size)
        assertEquals("tx_002", list[0].txId) // sorted by timestamp desc

        // Count auto-topups since timestamp
        val count = walletTransactionDao.getTransactionCountByTypeSince(
            TransactionType.AUTO_TOPUP,
            System.currentTimeMillis() - 5000
        )
        assertEquals(1, count)
    }

    @Test
    fun testAllowedAppDaoOperations() = runBlocking {
        val app = AllowedAppEntity(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            addedAt = System.currentTimeMillis()
        )

        // Insert allowed app
        allowedAppDao.insertAllowedApp(app)

        // Fetch allowed apps
        val list = allowedAppDao.getAllAllowedApps()
        assertEquals(1, list.size)
        assertEquals("com.whatsapp", list[0].packageName)

        // Delete allowed app
        allowedAppDao.deleteAllowedApp("com.whatsapp")
        val emptyList = allowedAppDao.getAllAllowedApps()
        assertTrue(emptyList.isEmpty())
    }
}
