package com.browntowndev.pocketcrew.feature.inference

import io.mockk.mockk
import org.junit.jupiter.api.Test

class ListMethodsTest {
    @Test
    fun listMethods() {
        val manager =
            ConversationManagerImpl(
                context = mockk(relaxed = true),
                localModelRepository = mockk(relaxed = true),
                activeModelProvider = mockk(relaxed = true),
                loggingPort = mockk(relaxed = true),
                toolExecutor = mockk(relaxed = true),
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
