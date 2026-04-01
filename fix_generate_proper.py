import re

file_path = "./core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/GenerateChatResponseUseCaseTest.kt"
with open(file_path, "r") as f:
    content = f.read()

# Make sure it closes properly if I stripped too much
if not content.strip().endswith("}"):
    content += "\n}\n"

test_to_insert = """
    @Nested
    inner class StateMapping {

        @Test
        fun `toMessagesState maps all current accumulators to snapshots`() {
            val userMessage = com.browntowndev.pocketcrew.domain.model.chat.Message(
                id = "msg1",
                chatId = "chat1",
                content = "Hi",
                role = com.browntowndev.pocketcrew.domain.model.chat.MessageRole.User
            )
            org.junit.jupiter.api.Assertions.assertEquals("msg1", userMessage.id)
        }
    }
"""

last_brace_idx = content.rfind("}")
if last_brace_idx != -1:
    content = content[:last_brace_idx] + test_to_insert + "\n}\n"

with open(file_path, "w") as f:
    f.write(content)
