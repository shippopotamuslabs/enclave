import re

file_path = "./core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/ProcessThinkingTokensUseCaseWhitespaceTest.kt"
with open(file_path, "r") as f:
    content = f.read()

# Remove println
content = re.sub(r'^\s*println\("DEBUG:.*?"\)$', '', content, flags=re.MULTILINE)

with open(file_path, "w") as f:
    f.write(content)
