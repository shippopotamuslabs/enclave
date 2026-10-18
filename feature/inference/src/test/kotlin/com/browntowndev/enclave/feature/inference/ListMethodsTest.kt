package com.browntowndev.enclave.feature.inference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeToolExecutor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ListMethodsTest {
    @Test
    fun listMethods() {
        val manager =
            ConversationManagerImpl(
                context = ApplicationProvider.getApplicationContext<Context>(),
                localModelRepository = FakeLocalModelRepository(),
                activeModelProvider = FakeActiveModelProvider(),
                loggingPort = FakeLoggingPort(),
                toolExecutor = FakeToolExecutor(),
                embeddingEnginePort = FakeEmbeddingEngine(),
            )

        manager.javaClass.getDeclaredClasses().forEach { innerClass ->
            println("Inner Class: ${innerClass.simpleName}")
            println("  Constructors:")
            innerClass.getDeclaredConstructors().forEach { ctor ->
                println("    ${ctor.name}(${ctor.parameterTypes.joinToString { it.simpleName }})")
            }
            println("  Methods:")
            innerClass.getDeclaredMethods().forEach { method ->
                println("    ${method.name}(${method.parameterTypes.joinToString { it.simpleName }})")
            }
        }
    }
}
