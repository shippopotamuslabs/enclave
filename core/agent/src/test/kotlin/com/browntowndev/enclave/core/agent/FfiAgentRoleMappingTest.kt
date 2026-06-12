package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.model.chat.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FfiAgentRoleMappingTest {
    @Test
    fun `compact summary maps to user role`() {
        assertEquals(Role.USER, FfiAgentRole.COMPACT_SUMMARY.toDomainRole())
    }

    @Test
    fun `tool message maps to assistant role`() {
        assertEquals(Role.ASSISTANT, FfiAgentRole.TOOL.toDomainRole())
    }
}
