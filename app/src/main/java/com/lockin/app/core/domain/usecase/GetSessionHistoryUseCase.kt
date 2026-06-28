package com.lockin.app.core.domain.usecase

import com.lockin.app.core.domain.model.Session
import com.lockin.app.core.domain.repository.SessionRepository
import com.lockin.app.core.security.EncryptedPrefsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case to retrieve the entire history of focus sessions.
 * Streams focus sessions sorted by start time.
 */
class GetSessionHistoryUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val encryptedPrefsManager: EncryptedPrefsManager
) {

    /**
     * Streams the user's focus session history.
     *
     * @return A Flow emitting a list of all logged Sessions.
     */
    operator fun invoke(): Flow<List<Session>> {
        return sessionRepository.getAllSessionsFlow().map { sessions ->
            val userId = encryptedPrefsManager.getUserId()
            if (userId != null) {
                sessions.filter { it.userId == userId }
            } else {
                sessions
            }
        }
    }
}
