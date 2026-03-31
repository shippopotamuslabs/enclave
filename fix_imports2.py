import re

file_path = "feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/LlamaChatSessionManagerTest.kt"
with open(file_path, "r") as f:
    content = f.read()

imports_replacements = {
    "import ChatMessage": "import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage",
    "import Role": "import com.browntowndev.pocketcrew.domain.model.chat.Role",
    "import GenerationEvent": "import com.browntowndev.pocketcrew.domain.model.inference.GenerationEvent",
    "import LlamaModelConfig": "import com.browntowndev.pocketcrew.domain.model.inference.LlamaModelConfig",
    "import coEvery": "import io.mockk.coEvery",
    "import coVerify": "import io.mockk.coVerify",
    "import every": "import io.mockk.every",
    "import verify": "import io.mockk.verify",
    "import flowOf": "import kotlinx.coroutines.flow.flowOf",
    "import assertEquals": "import org.junit.jupiter.api.Assertions.assertEquals",
    "import Test": "import org.junit.jupiter.api.Test",
}

for old, new in imports_replacements.items():
    content = content.replace(old, new)

with open(file_path, "w") as f:
    f.write(content)
