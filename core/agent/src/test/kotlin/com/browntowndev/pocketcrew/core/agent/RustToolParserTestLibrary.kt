package com.browntowndev.pocketcrew.core.agent

import java.io.File

object RustToolParserTestLibrary {
    private const val AGENT_CORE_COMPONENT = "agent_core"
    private const val LIBRARY_OVERRIDE_PROPERTY = "uniffi.component.$AGENT_CORE_COMPONENT.libraryOverride"

    fun loadAgentCoreLibrary() {
        if (System.getProperty(LIBRARY_OVERRIDE_PROPERTY) != null) {
            return
        }

        val hostLibrary = locateProjectRoot()
            .resolve("third_party/agent_core/target/debug/libagent_core.so")
            .absoluteFile
        require(hostLibrary.isFile) {
            "Missing host agent_core library at ${hostLibrary.path}; run " +
                "`cargo build --manifest-path third_party/agent_core/Cargo.toml` first."
        }
        System.setProperty(LIBRARY_OVERRIDE_PROPERTY, hostLibrary.path)
    }

    private fun locateProjectRoot(): File {
        val userDir = checkNotNull(System.getProperty("user.dir")) {
            "Missing user.dir system property"
        }
        var candidate = File(userDir).absoluteFile
        while (true) {
            if (candidate.resolve("third_party/agent_core/Cargo.toml").isFile) {
                return candidate
            }
            candidate = candidate.parentFile ?: break
        }
        error("Could not locate project root from $userDir")
    }
}
