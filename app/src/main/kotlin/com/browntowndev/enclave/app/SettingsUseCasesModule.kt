package com.browntowndev.enclave.app

import com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCasesImpl
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCasesImpl
import com.browntowndev.enclave.domain.usecase.settings.SettingsAuthUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsAuthUseCasesImpl
import com.browntowndev.enclave.domain.usecase.settings.SettingsDataControlsUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsDataControlsUseCasesImpl
import com.browntowndev.enclave.domain.usecase.settings.SettingsDeletionUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsDeletionUseCasesImpl
import com.browntowndev.enclave.domain.usecase.settings.SettingsLocalModelUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsLocalModelUseCasesImpl
import com.browntowndev.enclave.domain.usecase.settings.SettingsMediaUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsMediaUseCasesImpl
import com.browntowndev.enclave.domain.usecase.settings.SettingsMemoriesUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsMemoriesUseCasesImpl
import com.browntowndev.enclave.domain.usecase.settings.SettingsPreferencesUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsPreferencesUseCasesImpl
import com.browntowndev.enclave.domain.usecase.settings.SettingsLiveVoiceUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsLiveVoiceUseCasesImpl
import com.browntowndev.enclave.domain.usecase.settings.SettingsTtsUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsTtsUseCasesImpl
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCasesImpl
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
    abstract fun bindSettingsLiveVoiceUseCases(impl: SettingsLiveVoiceUseCasesImpl): SettingsLiveVoiceUseCases

    @Binds
    @Singleton
    abstract fun bindSettingsMediaUseCases(impl: SettingsMediaUseCasesImpl): SettingsMediaUseCases

    @Binds
    @Singleton
    abstract fun bindSettingsAuthUseCases(impl: SettingsAuthUseCasesImpl): SettingsAuthUseCases

    @Binds
    @Singleton
    abstract fun bindSettingsMemoriesUseCases(impl: SettingsMemoriesUseCasesImpl): SettingsMemoriesUseCases

    @Binds
    @Singleton
    abstract fun bindSettingsDataControlsUseCases(impl: SettingsDataControlsUseCasesImpl): SettingsDataControlsUseCases

    @Binds
    @Singleton
    abstract fun bindSettingsUseCases(impl: SettingsUseCasesImpl): SettingsUseCases
}
