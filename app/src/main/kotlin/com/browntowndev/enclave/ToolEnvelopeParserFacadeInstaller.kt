package com.browntowndev.enclave

import com.browntowndev.enclave.domain.port.inference.ToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.ToolEnvelopeParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolEnvelopeParserFacadeInstaller
    @Inject
    constructor(
        private val toolEnvelopeParserPort: ToolEnvelopeParserPort,
    ) {
        fun install() {
            ToolEnvelopeParser.installPort(toolEnvelopeParserPort)
        }
    }
