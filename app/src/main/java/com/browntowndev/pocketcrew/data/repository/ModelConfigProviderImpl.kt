package com.browntowndev.pocketcrew.data.repository

import android.content.Context
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data layer implementation of ModelConfigProvider.
 *
 * Provides access to configuration constants and the models directory,
 * allowing the domain layer to remain independent of data layer specifics.
 * Handles Android-specific Context internally to construct the models directory path.
 *
 * @param context Android Context for accessing external files directory
 * @param modelConfig The data model configuration object
 */
@Singleton
class ModelConfigProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelConfig: ModelConfig
) : ModelConfigProvider {

    override val modelsDirectory: File
        get() = File(context.getExternalFilesDir(null), modelConfig.MODELS_DIR)
}
