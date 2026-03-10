package com.browntowndev.pocketcrew.inference.worker

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InferenceWorkerModule {

    @Provides
    @Singleton
    fun provideInferenceNotificationManager(
        @ApplicationContext context: Context,
        notificationManager: android.app.NotificationManager
    ): InferenceNotificationManager {
        return InferenceNotificationManager(context, notificationManager)
    }
}
