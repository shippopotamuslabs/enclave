package com.browntowndev.pocketcrew.feature.inference.di

import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.pocketcrew.feature.inference.ActiveChatTurnStore
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
    abstract fun bindActiveChatTurnSnapshotPort(
        store: ActiveChatTurnStore,
    ): ActiveChatTurnSnapshotPort
}
