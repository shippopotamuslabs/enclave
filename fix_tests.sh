#!/bin/bash
find . -type f -name "*Test.kt" | while read file; do
    # Remove tests testing removed methods in LocalModelRepositoryImplTest
    if [[ "$file" == *"LocalModelRepositoryImplTest.kt"* ]]; then
        sed -i '/fun `activateLocalModel/,/^    }/d' "$file"
        sed -i '/fun `setDefaultLocalConfig/,/^    }/d' "$file"
        sed -i '/fun `getRegisteredSelection/,/^    }/d' "$file"
        sed -i '/fun `deleteModel/,/^    }/d' "$file"
    fi
    
    if [[ "$file" == *"LocalModelRepositoryTest.kt"* ]]; then
        sed -i '/fun `setDefaultLocalConfig/,/^    }/d' "$file"
    fi
done
