package com.browntowndev.enclave.core.data.download

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class ModelConfigJsonCatalogTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `SCN-JSON-001 shipped catalog declares asset and preset labels`() {
        val configFile = resolveRepoRootModelConfig()
        val root = json.parseToJsonElement(configFile.readText()).jsonObject
        val assets = root["assets"]!!.jsonArray

        val gemmaAsset =
            assets.single {
                it.jsonObject["sha256"]!!.jsonPrimitive.content ==
                    "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0"
            }.jsonObject
        assertEquals("Gemma 4 E4B", gemmaAsset["displayName"]!!.jsonPrimitive.content)
        assertPresetDisplayName(gemmaAsset, "550e8400-e29b-41d4-a716-446655440006", "Fast")
        assertPresetDisplayName(gemmaAsset, "550e8400-e29b-41d4-a716-446655440007", "Thinking")
        assertPresetDisplayName(gemmaAsset, "550e8400-e29b-41d4-a716-446655440005", "Final Synthesis")

        val miniCpmAsset =
            assets.single {
                it.jsonObject["sha256"]!!.jsonPrimitive.content ==
                    "1cfadbb76a841c6a448870dda3f2f46209ae6d552a848ee4ab3205de76b5adfb"
            }.jsonObject
        assertEquals("MiniCPM-V-4.6 1.3B", miniCpmAsset["displayName"]!!.jsonPrimitive.content)
        assertPresetDisplayName(miniCpmAsset, "550e8400-e29b-41d4-a716-446655440002", "Analytic Drafter")
    }

    private fun assertPresetDisplayName(
        asset: JsonObject,
        configId: String,
        expected: String,
    ) {
        val configs = asset["configurations"]!!.jsonArray
        val config =
            configs.single {
                it.jsonObject["configId"]!!.jsonPrimitive.content == configId
            }.jsonObject
        assertEquals(expected, config["displayName"]!!.jsonPrimitive.content)
    }

    private fun resolveRepoRootModelConfig(): File {
        val moduleDir = File(System.getProperty("user.dir"))
        val candidates =
            listOf(
                File(moduleDir, "model_config.json"),
                File(moduleDir.parentFile?.parentFile, "model_config.json"),
                File(moduleDir.parentFile, "model_config.json"),
            )
        return candidates.first { it.exists() }
    }
}
