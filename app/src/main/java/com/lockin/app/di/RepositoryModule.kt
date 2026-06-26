package com.lockin.app.di

import com.lockin.app.core.data.repository.SessionRepositoryImpl
import com.lockin.app.core.data.repository.WalletRepositoryImpl
import com.lockin.app.core.domain.repository.SessionRepository
import com.lockin.app.core.domain.repository.WalletRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency Injection module for repository bindings.
 * Binds domain repository interfaces to data layer implementation classes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Binds the SessionRepository interface to the SessionRepositoryImpl concrete class.
     */
    @Binds
    @Singleton
    abstract fun bindSessionRepository(
        impl: SessionRepositoryImpl
    ): SessionRepository

    /**
     * Binds the WalletRepository interface to the WalletRepositoryImpl concrete class.
     */
    @Binds
    @Singleton
    abstract fun bindWalletRepository(
        impl: WalletRepositoryImpl
    ): WalletRepository
}
