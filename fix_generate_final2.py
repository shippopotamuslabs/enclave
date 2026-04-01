import re

file_path = "./core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/GenerateChatResponseUseCaseTest.kt"
with open(file_path, "r") as f:
    content = f.read()

lines = content.split('\n')
while lines and not lines[-1].strip():
    lines.pop()
if lines[-1].strip() == "}":
    pass
else:
    lines.append("}")

with open(file_path, "w") as f:
    f.write('\n'.join(lines) + '\n')
