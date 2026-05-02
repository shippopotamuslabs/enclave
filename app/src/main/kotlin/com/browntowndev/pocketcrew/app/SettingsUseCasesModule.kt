package com.browntowndev.pocketcrew.app

import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsApiProviderUseCasesImpl
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAssignmentUseCasesImpl
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsDeletionUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsDeletionUseCasesImpl
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsLocalModelUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsLocalModelUseCasesImpl
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsMediaUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsMediaUseCasesImpl
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsPreferencesUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsPreferencesUseCasesImpl
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsTtsUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsTtsUseCasesImpl
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
    abstract fun bindSettingsPreferencesUseCases(impl: SettingsPreferencesUseCasesImpl): SettingsPreferencesUseCases

    @Binds
    @Singleton
    abstract fun bindSettingsLocalModelUseCases(impl: SettingsLocalModelUseCasesImpl): SettingsLocalModelUseCases

    @Binds
    @Singleton
    abstract fun bindSettingsApiProviderUseCases(impl: SettingsApiProviderUseCasesImpl): SettingsApiProviderUseCases

    @Binds
    @Singleton
    abstract fun bindSettingsAssignmentUseCases(impl: SettingsAssignmentUseCasesImpl): SettingsAssignmentUseCases

    @Binds
    @Singleton
    abstract fun bindSettingsDeletionUseCases(impl: SettingsDeletionUseCasesImpl): SettingsDeletionUseCases

    @Binds
    @Singleton
    abstract fun bindSettingsTtsUseCases(impl: SettingsTtsUseCasesImpl): SettingsTtsUseCases

    @Binds
    @Singleton
    abstract fun bindSettingsMediaUseCases(impl: SettingsMediaUseCasesImpl): SettingsMediaUseCases

    @Binds
    @Singleton
    abstract fun bindSettingsUseCases(impl: SettingsUseCasesImpl): SettingsUseCases
}
