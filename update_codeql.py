def update_workflow(file_path):
    with open(file_path, 'r') as file:
        lines = file.readlines()

    out_lines = []

    for line in lines:
        if "name: Analyze (java-kotlin)" in line:
            out_lines.append(line)
            out_lines.append("    env:\n")
            out_lines.append("      FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: true\n")
            out_lines.append("      CODEQL_ACTION_FILE_COVERAGE_ON_PRS: true\n")
            continue

        if "github/codeql-action/init@v3" in line:
            out_lines.append(line.replace("v3", "v4"))
            continue

        if "languages: 'java-kotlin'" in line:
            out_lines.append(line)
            out_lines.append("        build-mode: 'manual'\n")
            continue

        if "./gradlew assembleDebug --no-daemon --parallel --build-cache -Pandroid.builder.sdk.download=true" in line:
            out_lines.append(line.replace("--build-cache", "clean --no-build-cache"))
            continue

        if "github/codeql-action/analyze@v3" in line:
            out_lines.append(line.replace("v3", "v4"))
            continue

        out_lines.append(line)

    with open(file_path, 'w') as file:
        file.writelines(out_lines)

update_workflow(".github/workflows/codeql.yml")
