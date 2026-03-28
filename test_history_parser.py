with open('./feature/history/src/test/kotlin/com/browntowndev/pocketcrew/feature/history/HistoryViewModelTest.kt', 'r') as f:
    text = f.read()

import re

# Match each `@Test` or `@ParameterizedTest` block
# And extract the code
def parse_and_modify(content):
    lines = content.split('\n')
    out = []

    inside = False
    brace = 0
    buffer = []

    for line in lines:
        if ('@Test' in line or '@ParameterizedTest' in line) and not inside:
            inside = True
            brace = 0
            buffer = [line]
            continue

        if inside:
            buffer.append(line)
            brace += line.count('{') - line.count('}')

            if brace == 0 and '{' in '\n'.join(buffer):
                # We have a full test method
                method_text = '\n'.join(buffer)

                # Check for backgroundScope line
                bgscope = 'backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }'
                bgscope_spaces = '        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }\n'
                bgscope_spaces_12 = '            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }\n'

                if bgscope in method_text:
                    method_text = method_text.replace(bgscope_spaces_12, '')
                    method_text = method_text.replace(bgscope_spaces, '')

                    method_lines = method_text.split('\n')

                    # Find createViewModel
                    create_idx = -1
                    for i, l in enumerate(method_lines):
                        if 'viewModel = createViewModel(' in l:
                            create_idx = i
                            break

                    if create_idx != -1:
                        # Find the closing brace for the test block
                        # It is exactly the LAST line in method_lines since we matched brackets correctly!

                        setup_part = method_lines[:create_idx+1]
                        action_part = method_lines[create_idx+1:-1]
                        end_part = method_lines[-1]

                        # Replace state accesses
                        action_text = '\n'.join(action_part)
                        action_text = action_text.replace('val state = viewModel.uiState.value', 'val state = expectMostRecentItem()')

                        # Fix C4 if needed
                        if 'try {' in action_text and '} finally {' in action_text:
                            action_text = action_text.replace('try {', 'try {\n            viewModel.uiState.test {')
                            action_text = action_text.replace('} finally {', '} } finally {')
                            method_text = '\n'.join(setup_part) + '\n' + action_text + '\n' + end_part
                        else:
                            # Re-split and indent
                            action_lines = action_text.split('\n')
                            indented_action = ['    ' + l if l.strip() else l for l in action_lines]
                            method_text = '\n'.join(setup_part) + '\n        viewModel.uiState.test {\n' + '\n'.join(indented_action) + '\n        }\n' + end_part

                out.append(method_text)
                inside = False
                continue
        else:
            out.append(line)

    return '\n'.join(out)

new_content = parse_and_modify(text)
if 'import app.cash.turbine.test' not in new_content:
    new_content = new_content.replace('import org.junit.jupiter.api.Test', 'import app.cash.turbine.test\nimport org.junit.jupiter.api.Test')
    new_content = new_content.replace('import kotlinx.coroutines.flow.collect\n', '')

with open('./feature/history/src/test/kotlin/com/browntowndev/pocketcrew/feature/history/HistoryViewModelTest.kt', 'w') as f:
    f.write(new_content)
