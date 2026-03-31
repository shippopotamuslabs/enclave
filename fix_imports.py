import re

file_path = "feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/LlamaChatSessionManagerTest.kt"
with open(file_path, "r") as f:
    content = f.read()

# Add imports
imports = """
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationEvent
import com.browntowndev.pocketcrew.domain.model.inference.LlamaModelConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
"""

content = content.replace("import org.junit.jupiter.api.BeforeEach\n", "import org.junit.jupiter.api.BeforeEach\n" + imports)

# Remove fully qualified names
content = content.replace("org.junit.jupiter.api.Test", "Test")
content = content.replace("io.mockk.coEvery", "coEvery")
content = content.replace("io.mockk.coVerify", "coVerify")
content = content.replace("io.mockk.every", "every")
content = content.replace("io.mockk.verify", "verify")
content = content.replace("org.junit.jupiter.api.Assertions.assertEquals", "assertEquals")
content = content.replace("com.browntowndev.pocketcrew.domain.model.inference.LlamaModelConfig", "LlamaModelConfig")
content = content.replace("com.browntowndev.pocketcrew.domain.model.chat.ChatMessage", "ChatMessage")
content = content.replace("com.browntowndev.pocketcrew.domain.model.chat.Role", "Role")
content = content.replace("com.browntowndev.pocketcrew.domain.model.inference.GenerationEvent", "GenerationEvent")
content = content.replace("kotlinx.coroutines.flow.flowOf", "flowOf")

with open(file_path, "w") as f:
    f.write(content)
