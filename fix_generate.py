import re

file_path = "./core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/GenerateChatResponseUseCaseTest.kt"
with open(file_path, "r") as f:
    content = f.read()

# First fix the extra test block appended at the end of the file outside the class
content = re.sub(r'\s*@Nested\s+inner class StateMapping \{.*$', '', content, flags=re.DOTALL)
content = re.sub(r'\s*@Nested\s+@DisplayName\("toMessagesState"\).*$', '', content, flags=re.DOTALL)

with open(file_path, "w") as f:
    f.write(content)
