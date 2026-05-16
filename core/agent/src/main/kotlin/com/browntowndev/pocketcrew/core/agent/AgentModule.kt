package com.browntowndev.pocketcrew.core.agent

import com.browntowndev.pocketcrew.domain.port.inference.AgentLoopPort
import com.browntowndev.pocketcrew.domain.port.inference.AgentPolicyPort
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
}
