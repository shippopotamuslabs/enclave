package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.port.inference.AgentLoopPort
import com.browntowndev.enclave.domain.port.inference.AgentPolicyPort
import com.browntowndev.enclave.domain.port.inference.ToolEnvelopeParserPort
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {
    @Binds
    @Singleton
    abstract fun bindAgentLoopPort(impl: RustAgentEngineAdapter): AgentLoopPort

    @Binds
    @Singleton
    abstract fun bindAgentPolicyPort(impl: LiteRtAgentPolicyBridge): AgentPolicyPort

    @Binds
    @Singleton
    abstract fun bindToolEnvelopeParserPort(impl: RustToolEnvelopeParser): ToolEnvelopeParserPort

    @Binds
    @Singleton
    abstract fun bindToolStreamGatePort(impl: RustToolStreamGate): ToolStreamGatePort
}
