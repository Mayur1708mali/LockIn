package com.lockin.app.core.domain.usecase

import com.lockin.app.core.domain.model.Session
import com.lockin.app.core.domain.repository.SessionRepository
import javax.inject.Inject

/**
 * Use case to retrieve the currently active focus session, if any exists.
 */
class GetActiveSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {

    /**
     * Checks for and returns the active focus session.
     *
     * @return The active focus Session, or null if none exists.
     */
    suspend operator fun invoke(): Session? {
        return sessionRepository.getActiveSession()
    }
}
