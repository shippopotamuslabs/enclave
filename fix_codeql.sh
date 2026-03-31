sed -i 's/uses: github\/codeql-action\/init@v3/uses: github\/codeql-action\/init@v4/g' .github/workflows/codeql.yml
sed -i 's/uses: github\/codeql-action\/analyze@v3/uses: github\/codeql-action\/analyze@v4/g' .github/workflows/codeql.yml
sed -i '/languages: \x27java-kotlin\x27/a\        build-mode: \x27manual\x27' .github/workflows/codeql.yml
sed -i 's/--build-cache/--no-build-cache/g' .github/workflows/codeql.yml
sed -i 's/\.\/gradlew assembleDebug/.\/gradlew clean assembleDebug/g' .github/workflows/codeql.yml
sed -i 's/    steps:/    steps:\n    - name: Set global env\n      run: echo "FORCE_JAVASCRIPT_ACTIONS_TO_NODE24=true" >> $GITHUB_ENV \&\& echo "CODEQL_ACTION_FILE_COVERAGE_ON_PRS=true" >> $GITHUB_ENV/g' .github/workflows/codeql.yml
