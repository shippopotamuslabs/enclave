package com.browntowndev.pocketcrew.feature.inference.di

import com.browntowndev.pocketcrew.domain.port.repository.ApiModelCatalogPort
import com.browntowndev.pocketcrew.feature.inference.repository.ApiModelCatalogRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ApiModelCatalogModule {
    @Binds
    @Singleton
    abstract fun bindApiModelCatalogRepository(impl: ApiModelCatalogRepositoryImpl): ApiModelCatalogPort
}
