package com.browntowndev.enclave.core.agent

import java.io.File

object RustToolParserTestLibrary {
    private const val AGENT_CORE_COMPONENT = "agent_core"
    private const val LIBRARY_OVERRIDE_PROPERTY = "uniffi.component.$AGENT_CORE_COMPONENT.libraryOverride"
    private const val JNA_LIBRARY_PATH_PROPERTY = "jna.library.path"

    fun loadAgentCoreLibrary() {
        // Honour environments that pre-configure the library location.
        if (System.getProperty(LIBRARY_OVERRIDE_PROPERTY) != null) {
            return
        }

        val hostLibrary = locateHostLibrary()

        // The generated UniFFI binding reads `LIBRARY_OVERRIDE_PROPERTY` and feeds
        // its value straight to `Native.load(...)`. An absolute path makes JNA open
        // that exact file; we additionally point `jna.library.path` at its parent
        // directory so the dynamic linker can resolve the .so's own dependencies.
        System.setProperty(JNA_LIBRARY_PATH_PROPERTY, hostLibrary.parent)
        System.setProperty(LIBRARY_OVERRIDE_PROPERTY, hostLibrary.path)
    }

    /**
     * Resolves the newest built host (x86-64) `libagent_core.so`.
     *
     * Cargo writes the real cdylib to `target/debug/deps/` and copies it to
     * `target/debug/`. That copy can lag behind a partial rebuild and miss symbols
     * the freshly generated binding expects, so we prefer whichever candidate was
     * built most recently rather than hard-coding a single (possibly stale) path.
     */
    private fun locateHostLibrary(): File {
        val targetDir = locateProjectRoot()
            .resolve("third_party/agent_core/target")
            .absoluteFile
        val candidates = listOf(
            targetDir.resolve("debug/deps/libagent_core.so"),
            targetDir.resolve("debug/libagent_core.so"),
        ).filter(File::isFile)
        val hostLibrary = candidates.maxByOrNull(File::lastModified)
        require(hostLibrary != null) {
            "Missing host agent_core library under ${targetDir.path}; run " +
                "`cargo build --manifest-path third_party/agent_core/Cargo.toml` first."
        }
        return hostLibrary.absoluteFile
    }

    private fun locateProjectRoot(): File {
        val userDir =
            checkNotNull(System.getProperty("user.dir")) {
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
