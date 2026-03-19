#!/bin/bash

BASE_DIR="golden-test-examples"
RAW_BASE="https://raw.githubusercontent.com/android/nowinandroid/main"

echo "📂 Initializing Explicit Golden Sync..."

sync_file() {
    local url=$1
    local dest=$2
    mkdir -p "$(dirname "$dest")"
    
    status_code=$(curl -s -L -w "%{http_code}" -o "$dest" "$url")
    if [ "$status_code" -eq 200 ] && [ $(stat -c%s "$dest") -gt 500 ]; then
        echo "✅ Synced: $dest"
    else
        echo "❌ FAILED (Status $status_code): $url"
        rm -f "$dest"
    fi
}

# --- FOR YOU ---
sync_file "$RAW_BASE/feature/foryou/impl/src/androidTest/kotlin/com/google/samples/apps/nowinandroid/feature/foryou/impl/ForYouScreenTest.kt" \
    "$BASE_DIR/foryou/ui_instrumented.kt"
sync_file "$RAW_BASE/feature/foryou/impl/src/test/kotlin/com/google/samples/apps/nowinandroid/feature/foryou/impl/ForYouViewModelTest.kt" \
    "$BASE_DIR/foryou/logic_test.kt"

# --- SETTINGS ---
sync_file "$RAW_BASE/feature/settings/impl/src/androidTest/kotlin/com/google/samples/apps/nowinandroid/feature/settings/impl/SettingsDialogTest.kt" \
    "$BASE_DIR/settings/ui_instrumented.kt"
sync_file "$RAW_BASE/feature/settings/impl/src/test/kotlin/com/google/samples/apps/nowinandroid/feature/settings/impl/SettingsViewModelTest.kt" \
    "$BASE_DIR/settings/logic_test.kt"

# --- INTERESTS ---
# Note: This uses the specific package path you linked (dropping the .feature prefix in the logic test)
sync_file "$RAW_BASE/feature/interests/impl/src/androidTest/kotlin/com/google/samples/apps/nowinandroid/feature/interests/impl/InterestsScreenTest.kt" \
    "$BASE_DIR/interests/ui_instrumented.kt"
sync_file "$RAW_BASE/feature/interests/impl/src/test/kotlin/com/google/samples/apps/nowinandroid/interests/impl/InterestsViewModelTest.kt" \
    "$BASE_DIR/interests/logic_test.kt"

# --- SEARCH ---
sync_file "$RAW_BASE/feature/search/impl/src/androidTest/kotlin/com/google/samples/apps/nowinandroid/feature/search/impl/SearchScreenTest.kt" \
    "$BASE_DIR/search/ui_instrumented.kt"
sync_file "$RAW_BASE/feature/search/impl/src/test/kotlin/com/google/samples/apps/nowinandroid/feature/search/impl/SearchViewModelTest.kt" \
    "$BASE_DIR/search/logic_test.kt"

# --- TOPIC ---
sync_file "$RAW_BASE/feature/topic/impl/src/androidTest/kotlin/com/google/samples/apps/nowinandroid/feature/topic/impl/TopicScreenTest.kt" \
    "$BASE_DIR/topic/ui_instrumented.kt"
sync_file "$RAW_BASE/feature/topic/impl/src/test/kotlin/com/google/samples/apps/nowinandroid/feature/topic/impl/TopicViewModelTest.kt" \
    "$BASE_DIR/topic/logic_test.kt"

# --- BOOKMARKS ---
sync_file "$RAW_BASE/feature/bookmarks/impl/src/androidTest/kotlin/com/google/samples/apps/nowinandroid/feature/bookmarks/impl/BookmarksScreenTest.kt" \
    "$BASE_DIR/bookmarks/ui_instrumented.kt"
sync_file "$RAW_BASE/feature/bookmarks/impl/src/test/kotlin/com/google/samples/apps/nowinandroid/feature/bookmarks/impl/BookmarksViewModelTest.kt" \
    "$BASE_DIR/bookmarks/logic_test.kt"

# --- CORE FILES (Anti-Reward-Hack Dependencies) ---
sync_file "$RAW_BASE/core/data/src/test/kotlin/com/google/samples/apps/nowinandroid/core/data/repository/OfflineFirstUserDataRepositoryTest.kt" \
    "$BASE_DIR/data/OfflineFirstUserDataRepositoryTest.kt"
sync_file "$RAW_BASE/core/testing/src/main/kotlin/com/google/samples/apps/nowinandroid/core/testing/util/MainDispatcherRule.kt" \
    "$BASE_DIR/utils/MainDispatcherRule.kt"

echo "---------------------------------------"
echo "🌟 Sync Complete."
ls -R "$BASE_DIR" | grep ".kt"
