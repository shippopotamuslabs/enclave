package com.browntowndev.pocketcrew.core.ui.error

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ErrorModule {

    @Binds
    @Singleton
    abstract fun bindViewModelErrorHandler(
        impl: ViewModelErrorHandlerImpl
    ): ViewModelErrorHandler
}
