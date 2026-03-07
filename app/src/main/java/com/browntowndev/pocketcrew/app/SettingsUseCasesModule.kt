package com.browntowndev.pocketcrew.app

import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCasesImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsUseCasesModule {
    @Binds
    @Singleton
    abstract fun bindSettingsUseCases(impl: SettingsUseCasesImpl): SettingsUseCases
}
