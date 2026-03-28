import re
import sys

files = [
    './core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/download/InitializeModelsUseCaseTest.kt',
    './core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/download/ModelDownloadOrchestratorImplTest.kt',
    './core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/ModelDownloadOrchestratorTest.kt'
]

for filepath in files:
    with open(filepath, 'r') as f:
        content = f.read()

    if "import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule" not in content:
        content = content.replace("import kotlinx.coroutines.Dispatchers\n", "")
        content = content.replace("import kotlinx.coroutines.test.resetMain\n", "")
        content = content.replace("import kotlinx.coroutines.test.setMain\n", "")

        # Add imports
        import_match = re.search(r'^import .+', content, re.MULTILINE)
        if import_match:
            content = content[:import_match.start()] + "import org.junit.jupiter.api.extension.ExtendWith\nimport com.browntowndev.pocketcrew.core.testing.MainDispatcherRule\n" + content[import_match.start():]

        # Add @ExtendWith(MainDispatcherRule::class)
        content = re.sub(r'class \w+Test \{', r'@ExtendWith(MainDispatcherRule::class)\n\g<0>', content)

        # Remove setMain and resetMain
        content = re.sub(r'\s*Dispatchers\.setMain\(testDispatcher\)', '', content)
        content = re.sub(r'\s*Dispatchers\.resetMain\(\)', '', content)

    with open(filepath, 'w') as f:
        f.write(content)
