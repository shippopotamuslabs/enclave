import re
import sys

def replace_bangs_in_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    if "import kotlin.test.fail" not in content and "!!" in content:
        import_match = re.search(r'^import .+', content, re.MULTILINE)
        if import_match:
            content = content[:import_match.start()] + "import kotlin.test.fail\n" + content[import_match.start():]

    content = content.replace("!!", ' ?: fail("Unexpected null")')

    with open(filepath, 'w') as f:
        f.write(content)

if __name__ == "__main__":
    for arg in sys.argv[1:]:
        replace_bangs_in_file(arg)
