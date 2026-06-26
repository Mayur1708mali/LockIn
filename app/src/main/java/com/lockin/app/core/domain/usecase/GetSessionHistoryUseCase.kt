package com.lockin.app.core.domain.usecase

import com.lockin.app.core.domain.model.Session
import com.lockin.app.core.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case to retrieve the entire history of focus sessions.
 * Streams focus sessions sorted by start time.
 */
class GetSessionHistoryUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {

    /**
     * Streams the user's focus session history.
     *
     * @return A Flow emitting a list of all logged Sessions.
     */
    operator fun invoke(): Flow<List<Session>> {
        return sessionRepository.getAllSessionsFlow()
    }
}
