package com.browntowndev.enclave.feature.inference.di

import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.enclave.feature.inference.ActiveChatTurnStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ActiveChatTurnModule {
    @Binds
    @Singleton
    abstract fun bindActiveChatTurnSnapshotPort(store: ActiveChatTurnStore): ActiveChatTurnSnapshotPort
}
