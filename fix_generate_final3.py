import re

file_path = "./core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/GenerateChatResponseUseCaseTest.kt"
with open(file_path, "r") as f:
    content = f.read()

# add a closing brace to the end of the file since it says "Syntax error: Missing '}'"
content = content.strip() + "\n}\n"

with open(file_path, "w") as f:
    f.write(content)
