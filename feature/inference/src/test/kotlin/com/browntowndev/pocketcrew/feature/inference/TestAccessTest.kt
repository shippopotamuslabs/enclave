package com.browntowndev.pocketcrew.feature.inference
import com.openai.models.responses.ResponseCreateParams
import org.junit.jupiter.api.Test

class TestAccessTest {
    @Test
    fun runTest() {
        val p = ResponseCreateParams.builder().model("test").build()
        println("BUILDER METHODS:")
        p.toBuilder().javaClass.methods.forEach { println(it.name) }
        println("PARAMS METHODS:")
        p.javaClass.methods.forEach { println(it.name) }
    }
}
