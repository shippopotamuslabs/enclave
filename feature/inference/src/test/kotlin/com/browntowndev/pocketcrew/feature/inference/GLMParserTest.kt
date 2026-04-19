package com.browntowndev.pocketcrew.feature.inference

import org.junit.jupiter.api.Test

class GLMParserTest {
    @Test
    fun testGlmParsing() {
        val text = """<tool_call>tavily_web_search<arg_key>query</arg_key><arg_value>"GLM-5.1" API providers DeepInfra Fireworks pricing</arg_value></tool_call>"""
        val regex = Regex("""(?s)<tool_call>\s*([a-zA-Z0-9_]+)\s*<arg_key>([^<]+)</arg_key>\s*<arg_value>(.*?)</arg_value>\s*</tool_call>""")
        val match = regex.find(text)
        println("MATCHES: " + match?.groupValues)
    }
}
