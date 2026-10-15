package com.browntowndev.enclave.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.github.detekt.parser.KtCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class EnclaveRulesTest {
    @Test
    fun provider_registersAllNineRules() {
        val ruleSet = EnclaveRuleSetProvider().instance(Config.empty)
        assertEquals(9, ruleSet.rules.size)
        assertEquals("enclave", ruleSet.id)
    }

    @Test
    fun pathFilters_enforceFeatureAndApp_exemptCoreUiAndTests() {
        assertTrue(EnclavePathFilters.isEnforced("/repo/feature/chat/src/main/Foo.kt"))
        assertTrue(EnclavePathFilters.isEnforced("/repo/app/src/main/Foo.kt"))
        assertTrue(!EnclavePathFilters.isEnforced("/repo/core/ui/src/main/Foo.kt"))
        assertTrue(!EnclavePathFilters.isEnforced("/repo/feature/chat/src/test/Foo.kt"))
    }

    @Test
    fun repositoryInViewModel_flagsRepositoryConstructorParam() {
        val dir = Files.createTempDirectory("enclave-detekt")
        val file = dir.resolve("feature/chat/src/main/kotlin/ChatViewModel.kt")
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            """
            class ChatViewModel(private val repo: ChatRepository)
            """.trimIndent(),
        )
        val ktFile = KtCompiler().compile(file.parent, file)
        val rule = ForbiddenRepositoryInViewModel(Config.empty)
        rule.visitFile(ktFile)
        assertEquals(1, rule.findings.size)
    }

    @Test
    fun repositoryInViewModel_allowsUseCaseConstructorParam() {
        val dir = Files.createTempDirectory("enclave-detekt")
        val file = dir.resolve("feature/chat/src/main/kotlin/ChatViewModel.kt")
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            """
            class ChatViewModel(private val send: SendMessageUseCase)
            """.trimIndent(),
        )
        val ktFile = KtCompiler().compile(file.parent, file)
        val rule = ForbiddenRepositoryInViewModel(Config.empty)
        rule.visitFile(ktFile)
        assertTrue(rule.findings.isEmpty())
    }
}
