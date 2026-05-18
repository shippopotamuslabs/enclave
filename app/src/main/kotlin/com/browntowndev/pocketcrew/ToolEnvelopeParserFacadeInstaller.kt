package com.browntowndev.pocketcrew

import com.browntowndev.pocketcrew.domain.port.inference.ToolEnvelopeParserPort
import com.browntowndev.pocketcrew.domain.util.ToolEnvelopeParser
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
