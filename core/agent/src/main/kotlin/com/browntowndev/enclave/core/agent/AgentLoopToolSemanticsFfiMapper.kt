package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.model.inference.AgentLoopArtifactToolSemantics
import com.browntowndev.enclave.domain.model.inference.AgentLoopToolDisclosureSemantics
import com.browntowndev.enclave.domain.model.inference.AgentLoopToolGreedyExtensionSemantics
import com.browntowndev.enclave.domain.model.inference.AgentLoopToolSemanticsConfig

internal fun AgentLoopToolSemanticsConfig.toFfi(): FfiToolSemanticsConfig =
    FfiToolSemanticsConfig(
        disclosure = disclosure?.toFfi(),
        artifact = artifact?.toFfi(),
        greedyExtensionTools = greedyExtensionTools.map { it.toFfi() },
    )

private fun AgentLoopToolDisclosureSemantics.toFfi(): FfiToolDisclosureSemantics =
    FfiToolDisclosureSemantics(
        toolName = toolName,
        requestFieldName = requestFieldName,
        alwaysCallable = alwaysCallable,
    )

private fun AgentLoopArtifactToolSemantics.toFfi(): FfiArtifactToolSemantics =
    FfiArtifactToolSemantics(
        startToolName = startToolName,
        appendToolName = appendToolName,
        idField = idField,
        appendBlocksField = appendBlocksField,
        completionField = completionField,
    )

private fun AgentLoopToolGreedyExtensionSemantics.toFfi(): FfiToolGreedyExtensionSemantics =
    FfiToolGreedyExtensionSemantics(
        toolName = toolName,
        extraTurns = extraTurns,
    )
