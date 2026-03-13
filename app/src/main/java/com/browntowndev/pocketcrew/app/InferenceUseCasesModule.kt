package com.browntowndev.pocketcrew.app

import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InferenceUseCasesModule {
    @Binds
    @Singleton
    abstract fun bindInferenceLockManager(impl: InferenceLockManagerImpl): InferenceLockManager
}
