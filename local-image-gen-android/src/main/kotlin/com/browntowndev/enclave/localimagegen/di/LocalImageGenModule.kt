package com.browntowndev.enclave.localimagegen.di

import android.content.Context
import com.browntowndev.enclave.localimagegen.NativeLocalImageGenRuntime
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalImageGenModule {
    @Provides
    @Singleton
    fun provideNativeLocalImageGenRuntime(
        @ApplicationContext context: Context,
    ): NativeLocalImageGenRuntime = NativeLocalImageGenRuntime(context)
}
