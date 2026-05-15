# FEAT-settings-oss-licenses — Implementation Spec

## 1. Overview

Add an "Open Source Licenses" navigation item to the "About" section of `SettingsScreen.kt`,
immediately below the existing "Feedback" item. Tapping it opens a `JumpFreeModalBottomSheet`
containing a scrollable `LazyColumn` that lists every third-party library shipped in the release
build, together with its license type and required attribution text.

All attribution data is a static, hardcoded compile-time list inside the feature/settings
presentation layer. No domain changes, no database changes, no repository required.

---

## 2. Architecture Fit

| Concern | Decision |
|---------|----------|
| Layer | feature/settings — presentation only |
| Domain touch? | None. Static UI data; no domain models, no ports, no use cases. |
| Data layer touch? | None. |
| ViewModel touch? | Yes — add `isOssLicensesSheetOpen: Boolean` to `SettingsHomeUiState`, expose `onShowOssLicensesSheet(Boolean)` through `SettingsViewModel` → `SettingsGeneralDelegate`. |
| Navigation | Sheet-based (modal BottomSheet), same as `MemoriesBottomSheet`, `ByokBottomSheet`, etc. No NavGraph change. |
| New Gradle deps | None. Uses existing Material3 + Compose stack only. |

Clean Architecture compliance: all changes are strictly inside
`feature/settings/src/main/kotlin/…/feature/settings/`. No imports of Android/Compose
types into `:domain`. Dependency direction unchanged.

---

## 3. Attribution Library List

License research performed against published library metadata and GitHub sources.

| Library (artifact) | Version | License | Attribution Required |
|--------------------|---------|---------|----------------------|
| javax.inject | 1 | Apache 2.0 | Yes — name + license notice |
| ch.ubique.uniffi:runtime | 1.0.10 | Apache 2.0 | Yes |
| com.knuddels:jtokkit | 1.1.0 | MIT | Yes — copyright + license |
| org.jetbrains.kotlinx:kotlinx-serialization-json | 1.11.0 | Apache 2.0 | Yes |
| org.jetbrains.kotlin:kotlin-reflect | 2.3.21 | Apache 2.0 | Yes |
| androidx.core:core-ktx | 1.18.0 | Apache 2.0 | Yes |
| androidx.core:core-splashscreen | 1.2.0 | Apache 2.0 | Yes |
| androidx.biometric:biometric | 1.2.0-alpha05 | Apache 2.0 | Yes |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.10.0 | Apache 2.0 | Yes |
| androidx.lifecycle:lifecycle-runtime-compose | 2.10.0 | Apache 2.0 | Yes |
| androidx.lifecycle:lifecycle-process | 2.10.0 | Apache 2.0 | Yes |
| androidx.activity:activity-compose | 1.13.0 | Apache 2.0 | Yes |
| androidx.compose (BOM + ui/graphics/material3/material-icons/animation/foundation/ui-text/ui-text-google-fonts) | BOM 2026.05.00 | Apache 2.0 | Yes (grouped as "Jetpack Compose") |
| com.google.ai.edge.litertlm:litertlm-android | 0.11.0 | Apache 2.0 | Yes |
| com.google.dagger:hilt-android | 2.59.2 | Apache 2.0 | Yes |
| androidx.hilt:hilt-navigation-compose | 1.3.0 | Apache 2.0 | Yes |
| androidx.hilt:hilt-work | 1.3.0 | Apache 2.0 | Yes |
| androidx.navigation:navigation-compose | 2.9.8 | Apache 2.0 | Yes |
| androidx.datastore:datastore-preferences | 1.2.1 | Apache 2.0 | Yes |
| androidx.room (runtime/ktx) | 2.8.4 | Apache 2.0 | Yes |
| androidx.work:work-runtime-ktx | 2.11.2 | Apache 2.0 | Yes |
| com.squareup.okhttp3:okhttp | 5.3.2 | Apache 2.0 | Yes |
| com.squareup.okhttp3:logging-interceptor | 5.3.2 | Apache 2.0 | Yes |
| dev.chrisbanes.haze:haze | 1.7.2 | Apache 2.0 | Yes |
| io.github.huarangmeng:markdown-parser | 1.2.7 | Apache 2.0 | Yes |
| io.github.huarangmeng:markdown-renderer | 1.2.7 | Apache 2.0 | Yes |
| com.google.firebase (BOM + auth/firestore/functions/analytics/crashlytics/storage) | BOM 34.13.0 | Apache 2.0 | Yes (grouped as "Firebase Android SDK") |
| io.grpc (grpc-bom/android/okhttp) | 1.81.0 | Apache 2.0 | Yes (grouped as "gRPC") |
| androidx.credentials (credentials + credentials-play-services-auth) | 1.7.0-alpha02 | Apache 2.0 | Yes |
| com.google.android.libraries.identity.googleid:googleid | 1.2.0 | Apache 2.0 | Yes |
| androidx.media3 (exoplayer/session/datasource/ui) | 1.10.1 | Apache 2.0 | Yes (grouped as "Media3") |
| com.openai:openai-java | 4.35.0 | Apache 2.0 | Yes |
| com.anthropic:anthropic-java | 2.31.0 | MIT | Yes — copyright + license |
| com.google.genai:google-genai | 1.53.0 | Apache 2.0 | Yes |
| io.coil-kt.coil3:coil-compose | 3.4.0 | Apache 2.0 | Yes |
| io.coil-kt.coil3:coil-network-okhttp | 3.4.0 | Apache 2.0 | Yes |
| me.saket.telephoto:zoomable-image-coil3 | 0.19.0 | Apache 2.0 | Yes |
| org.commonmark:commonmark | 0.28.0 | BSD 2-Clause | Yes — copyright + license |
| org.commonmark:commonmark-ext-gfm-tables | 0.28.0 | BSD 2-Clause | Yes |
| net.java.dev.jna:jna | 5.18.1 | LGPL-2.1 OR Apache-2.0 | Yes — notice required under LGPL (dual-license; bundled in APK as AAR) |

**Grouping strategy:** Compose BOM family is grouped into a single entry "Jetpack Compose".
Firebase BOM family is grouped as "Firebase Android SDK". gRPC BOM family is grouped as "gRPC".
Media3 family is grouped as "Media3". This keeps the list readable (≈ 38 entries after grouping).

**Excluded (test-only, build tooling, annotation processors):**
`junit`, `androidx-junit`, `espresso-core`, `mockk`, `turbine`, `robolectric`,
`apter-junit5-robolectric-extension`, `androidx-test-core`, `junit-vintage-engine`,
`junit-platform-launcher`, `room-testing`, `androidx-work-testing`, `hilt-android-testing`,
`androidx-compose-ui-test-manifest`, `androidx-compose-ui-test-junit4`,
`hilt-compiler`, `room-compiler`, `hilt-work-compiler`, `ksp`, `detekt`, `ktlint`.

---

## 4. Data Model

Location: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/OssLicenseEntry.kt`

```kotlin
@Immutable
data class OssLicenseEntry(
    val name: String,
    val version: String,
    val licenseType: String,
    val copyrightHolder: String,
    val licenseText: String,
)
```

Location: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/OssLicenses.kt`

Singleton object `OssLicenses` with a single `val entries: List<OssLicenseEntry>` that is
constructed at class-load time. The list is ordered alphabetically by `name`.

The `licenseText` field holds the full required attribution notice (not the full 10-page
license body). For Apache 2.0 libraries it is the NOTICE-style text
(`"Licensed under the Apache License, Version 2.0"`). For MIT/BSD it includes the
copyright line and the permission notice. For LGPL it includes the LGPL notice text and a
link to the source. See Section 6 (License Text Snippets) for exact strings.

---

## 5. Files Modified / Created

### 5.1 Modified Files

| File | Change |
|------|--------|
| `feature/settings/src/main/kotlin/…/SettingsModels.kt` | Add `isOssLicensesSheetOpen: Boolean = false` to `SettingsHomeUiState` |
| `feature/settings/src/main/kotlin/…/SettingsUiStateFactory.kt` | Set `isOssLicensesSheetOpen = sheetVisibility.ossLicenses` inside `SettingsHomeUiState(…)` constructor block in `create()` |
| `feature/settings/src/main/kotlin/…/SettingsUiStateFactory.kt` | Add `ossLicenses: Boolean = false` field to `SheetVisibilityState` |
| `feature/settings/src/main/kotlin/…/SettingsGeneralDelegate.kt` | Add `onShowOssLicensesSheet(sheetVisibility, show)` function following the identical pattern of `onShowFeedbackSheet` |
| `feature/settings/src/main/kotlin/…/SettingsViewModel.kt` | Add `fun onShowOssLicensesSheet(show: Boolean)` delegating to `generalDelegate.onShowOssLicensesSheet(_sheetVisibility, show)` |
| `feature/settings/src/main/kotlin/…/SettingsScreen.kt` | (a) Add `onShowOssLicensesSheet: (Boolean) -> Unit` parameter to `SettingsScreen` signature; (b) add the `SettingsNavigationItem` for "Open Source Licenses" in the About section; (c) add the `if (uiState.home.isOssLicensesSheetOpen)` block that renders `OssLicensesBottomSheet` |
| `feature/settings/src/main/kotlin/…/SettingsRoute.kt` | Pass `onShowOssLicensesSheet = viewModel::onShowOssLicensesSheet` to `SettingsScreen(…)` |

### 5.2 New Files

| File | Responsibility |
|------|---------------|
| `feature/settings/src/main/kotlin/…/feature/settings/OssLicenseEntry.kt` | Immutable data class `OssLicenseEntry`; no imports beyond `androidx.compose.runtime.Immutable` |
| `feature/settings/src/main/kotlin/…/feature/settings/OssLicenses.kt` | `object OssLicenses { val entries: List<OssLicenseEntry> }` with all 38 hardcoded entries |
| `feature/settings/src/main/kotlin/…/feature/settings/OssLicensesBottomSheet.kt` | `OssLicensesBottomSheet` composable; follows `MemoriesBottomSheet` structural template |
| `feature/settings/src/test/kotlin/…/feature/settings/OssLicensesBottomSheetTest.kt` | Unit tests for state toggle + UI rendering (JUnit 5, MockK, Turbine) |

---

## 6. Detailed Implementation Steps

### Task 1 — Add `OssLicenseEntry` data class

Create `OssLicenseEntry.kt`:

```kotlin
package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.runtime.Immutable

@Immutable
data class OssLicenseEntry(
    val name: String,
    val version: String,
    val licenseType: String,
    val copyrightHolder: String,
    val licenseText: String,
)
```

No other code. No Hilt, no domain imports.

---

### Task 2 — Add `OssLicenses` object with full attribution list

Create `OssLicenses.kt`. Below is the complete, compiler-ready code:

```kotlin
package com.browntowndev.pocketcrew.feature.settings

internal object OssLicenses {
    val entries: List<OssLicenseEntry> = listOf(
        OssLicenseEntry(
            name = "Android Jetpack Activity",
            version = "1.13.0",
            licenseType = "Apache 2.0",
            copyrightHolder = "The Android Open Source Project",
            licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at\n\nhttps://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.",
        ),
        OssLicenseEntry(
            name = "Android Jetpack Biometric",
            version = "1.2.0-alpha05",
            licenseType = "Apache 2.0",
            copyrightHolder = "The Android Open Source Project",
            licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "Android Jetpack Compose",
            version = "BOM 2026.05.00",
            licenseType = "Apache 2.0",
            copyrightHolder = "The Android Open Source Project",
            licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "Android Jetpack Core KTX",
            version = "1.18.0",
            licenseType = "Apache 2.0",
            copyrightHolder = "The Android Open Source Project",
            licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "Android Jetpack Core SplashScreen",
            version = "1.2.0",
            licenseType = "Apache 2.0",
            copyrightHolder = "The Android Open Source Project",
            licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "Android Jetpack Credentials",
            version = "1.7.0-alpha02",
            licenseType = "Apache 2.0",
            copyrightHolder = "The Android Open Source Project",
            licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "Android Jetpack DataStore Preferences",
            version = "1.2.1",
            licenseType = "Apache 2.0",
            copyrightHolder = "The Android Open Source Project",
            licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "Android Jetpack Hilt Navigation Compose",
            version = "1.3.0",
            licenseType = "Apache 2.0",
            copyrightHolder = "The Android Open Source Project",
            licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "Android Jetpack Hilt Work",
            version = "1.3.0",
            licenseType = "Apache 2.0",
            copyrightHolder = "The Android Open Source Project",
            licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "Android Jetpack Lifecycle",
            version = "2.10.0",
            licenseType = "Apache 2.0",
            copyrightHolder = "The Android Open Source Project",
            licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "Android Jetpack Media3",
            version = "1.10.1",
            licenseType = "Apache 2.0",
            copyrightHolder = "The Android Open Source Project",
            licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "Android Jetpack Navigation Compose",
            version = "2.9.8",
            licenseType = "Apache 2.0",
            copyrightHolder = "The Android Open Source Project",
            licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "Android Jetpack Room",
            version = "2.8.4",
            licenseType = "Apache 2.0",
            copyrightHolder = "The Android Open Source Project",
            licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "Android Jetpack WorkManager",
            version = "2.11.2",
            licenseType = "Apache 2.0",
            copyrightHolder = "The Android Open Source Project",
            licenseText = "Copyright The Android Open Source Project\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "Anthropic Java SDK",
            version = "2.31.0",
            licenseType = "MIT",
            copyrightHolder = "Anthropic",
            licenseText = "MIT License\n\nCopyright (c) Anthropic\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\nTHE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.",
        ),
        OssLicenseEntry(
            name = "Coil 3",
            version = "3.4.0",
            licenseType = "Apache 2.0",
            copyrightHolder = "Coil Contributors",
            licenseText = "Copyright 2024 Coil Contributors\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "CommonMark Java",
            version = "0.28.0",
            licenseType = "BSD 2-Clause",
            copyrightHolder = "Atlassian Pty Ltd",
            licenseText = "BSD 2-Clause License\n\nCopyright (c) 2015, Atlassian Pty Ltd. All rights reserved.\n\nRedistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:\n\n1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.\n2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.\n\nTHIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.",
        ),
        OssLicenseEntry(
            name = "Firebase Android SDK",
            version = "BOM 34.13.0",
            licenseType = "Apache 2.0",
            copyrightHolder = "Google LLC",
            licenseText = "Copyright 2024 Google LLC\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "Google GenAI SDK",
            version = "1.53.0",
            licenseType = "Apache 2.0",
            copyrightHolder = "Google LLC",
            licenseText = "Copyright 2024 Google LLC\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "Google Identity (googleid)",
            version = "1.2.0",
            licenseType = "Apache 2.0",
            copyrightHolder = "Google LLC",
            licenseText = "Copyright 2024 Google LLC\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "gRPC",
            version = "1.81.0",
            licenseType = "Apache 2.0",
            copyrightHolder = "gRPC Authors",
            licenseText = "Copyright 2014 gRPC Authors. All Rights Reserved.\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "Haze",
            version = "1.7.2",
            licenseType = "Apache 2.0",
            copyrightHolder = "Chris Banes",
            licenseText = "Copyright 2023 Chris Banes\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "Hilt (Dagger)",
            version = "2.59.2",
            licenseType = "Apache 2.0",
            copyrightHolder = "Google LLC",
            licenseText = "Copyright 2020 Google LLC\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "javax.inject",
            version = "1",
            licenseType = "Apache 2.0",
            copyrightHolder = "javax.inject Contributors",
            licenseText = "Licensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "JNA (Java Native Access)",
            version = "5.18.1",
            licenseType = "Apache 2.0 (from dual LGPL-2.1 OR Apache-2.0)",
            copyrightHolder = "Timothy Wall and JNA Contributors",
            licenseText = "Copyright (c) Timothy Wall and JNA Contributors.\n\nLicensed under the Apache License, Version 2.0 (chosen from dual LGPL-2.1 OR Apache-2.0 license).",
        ),
        OssLicenseEntry(
            name = "jtokkit",
            version = "1.1.0",
            licenseType = "MIT",
            copyrightHolder = "Knuddels GmbH & Co. KG",
            licenseText = "MIT License\n\nCopyright (c) 2023 Knuddels GmbH & Co. KG\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\nTHE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.",
        ),
        OssLicenseEntry(
            name = "Kotlin",
            version = "2.3.21",
            licenseType = "Apache 2.0",
            copyrightHolder = "JetBrains s.r.o.",
            licenseText = "Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "kotlinx-serialization",
            version = "1.11.0",
            licenseType = "Apache 2.0",
            copyrightHolder = "JetBrains s.r.o.",
            licenseText = "Copyright 2017-2024 JetBrains s.r.o. and contributors.\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "LiteRT (TFLite)",
            version = "0.11.0",
            licenseType = "Apache 2.0",
            copyrightHolder = "Google LLC",
            licenseText = "Copyright 2024 Google LLC\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "markdown-parser & markdown-renderer",
            version = "1.2.7",
            licenseType = "Apache 2.0",
            copyrightHolder = "huarangmeng",
            licenseText = "Copyright huarangmeng\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "OkHttp",
            version = "5.3.2",
            licenseType = "Apache 2.0",
            copyrightHolder = "Square, Inc.",
            licenseText = "Copyright 2019 Square, Inc.\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "OpenAI Java SDK",
            version = "4.35.0",
            licenseType = "Apache 2.0",
            copyrightHolder = "OpenAI",
            licenseText = "Copyright OpenAI\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "Telephoto",
            version = "0.19.0",
            licenseType = "Apache 2.0",
            copyrightHolder = "Saket Narayan",
            licenseText = "Copyright 2023 Saket Narayan\n\nLicensed under the Apache License, Version 2.0.",
        ),
        OssLicenseEntry(
            name = "uniffi-kotlin-runtime",
            version = "1.0.10",
            licenseType = "Apache 2.0",
            copyrightHolder = "Ubique Innovation AG",
            licenseText = "Copyright Ubique Innovation AG\n\nLicensed under the Apache License, Version 2.0.",
        ),
    ).sortedBy { it.name }
}
```

---

### Task 3 — Extend `SettingsHomeUiState` and `SheetVisibilityState`

In `SettingsModels.kt`, add to `SettingsHomeUiState`:
```kotlin
val isOssLicensesSheetOpen: Boolean = false,
```
Place it immediately after `isSearchSkillSheetOpen`.

In `SettingsUiStateFactory.kt`, add to `SheetVisibilityState`:
```kotlin
val ossLicenses: Boolean = false,
```

In `SettingsUiStateFactory.kt`, inside `create()`, set the new field when constructing
`SettingsHomeUiState`:
```kotlin
isOssLicensesSheetOpen = sheetVisibility.ossLicenses,
```

---

### Task 4 — Add `onShowOssLicensesSheet` to `SettingsGeneralDelegate`

In `SettingsGeneralDelegate.kt`, add directly below `onShowFeedbackSheet`:

```kotlin
fun onShowOssLicensesSheet(sheetVisibility: MutableStateFlow<SheetVisibilityState>, show: Boolean) {
    sheetVisibility.update { it.copy(ossLicenses = show) }
}
```

---

### Task 5 — Wire through `SettingsViewModel`

In `SettingsViewModel.kt`, add inside the General / Auth section:

```kotlin
fun onShowOssLicensesSheet(show: Boolean) = generalDelegate.onShowOssLicensesSheet(_sheetVisibility, show)
```

---

### Task 6 — Update `SettingsScreen` signature and About section

In `SettingsScreen.kt`:

1. Add the new callback parameter to `SettingsScreen`:
   ```kotlin
   onShowOssLicensesSheet: (Boolean) -> Unit,
   ```
   Position: after `onShowFeedbackSheet: (Boolean) -> Unit,`.

2. In the About section `LazyColumn` item, add after the Feedback item:
   ```kotlin
   Spacer(modifier = Modifier.height(8.dp))
   SettingsNavigationItem(
       title = "Open Source Licenses",
       icon = Icons.Default.Info,
       onClick = { onShowOssLicensesSheet(true) }
   )
   ```

3. After all existing sheet `if` blocks, add:
   ```kotlin
   if (uiState.home.isOssLicensesSheetOpen) {
       OssLicensesBottomSheet(
           onDismiss = { onShowOssLicensesSheet(false) }
       )
   }
   ```

4. Update `PreviewSettingsScreen` to pass the new lambda:
   ```kotlin
   onShowOssLicensesSheet = {},
   ```

---

### Task 7 — Create `OssLicensesBottomSheet`

Create `OssLicensesBottomSheet.kt`. Full structure:

```kotlin
package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OssLicensesBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    JumpFreeModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Open Source Licenses",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Pocket Crew is built with the following open-source libraries.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            LazyColumn(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                items(
                    items = OssLicenses.entries,
                    key = { it.name },
                ) { entry ->
                    OssLicenseCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun OssLicenseCard(
    entry: OssLicenseEntry,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "v${entry.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                LicenseChip(licenseType = entry.licenseType)
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = entry.licenseText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LicenseChip(
    licenseType: String,
    modifier: Modifier = Modifier,
) {
    SuggestionChip(
        onClick = {},
        label = {
            Text(
                text = licenseType,
                style = MaterialTheme.typography.labelSmall,
            )
        },
        modifier = modifier,
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@Preview(showBackground = true, name = "OSS Licenses Sheet — Light")
@Composable
private fun PreviewOssLicensesBottomSheetLight() {
    PocketCrewTheme(darkTheme = false) {
        OssLicensesBottomSheet(onDismiss = {})
    }
}

@Preview(showBackground = true, name = "OSS Licenses Sheet — Dark")
@Composable
private fun PreviewOssLicensesBottomSheetDark() {
    PocketCrewTheme(darkTheme = true) {
        OssLicensesBottomSheet(onDismiss = {})
    }
}
```

---

### Task 8 — Update `SettingsRoute`

Add to the `SettingsScreen(…)` call in `SettingsRoute.kt`:
```kotlin
onShowOssLicensesSheet = viewModel::onShowOssLicensesSheet,
```

---

## 7. Detekt / KtLint Considerations

- `OssLicenses.kt` will trigger Detekt's `StringLiteralDuplication` rule for repeated
  Apache 2.0 license text. Suppress at the file level with:
  `@file:Suppress("StringLiteralDuplication")`
- `SettingsScreen.kt` already has `@Suppress("LongParameterList")` — the new parameter
  is covered by the existing suppression.
- `SettingsViewModel.kt` already has file-level suppression annotations that cover
  `TooManyFunctions`.
- All new Kotlin files must obey the 120-character max line length. Multi-line string literals
  should be kept as `\n`-escaped single-line strings (no raw triple-quoted strings unless line
  length is safe) — raw strings in the data objects will be long; suppress
  `MaxLineLength` at file level for `OssLicenses.kt`.

---

## 8. Validation Commands

Run in order after implementing all tasks:

```bash
./gradlew :feature:settings:assembleDebug
./gradlew detekt
./gradlew ktlintCheck
./gradlew :feature:settings:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew test
```

---

## 9. Self-Review Checklist

- [x] No domain model, repository interface, or use case added or modified.
- [x] `SheetVisibilityState.ossLicenses` follows the identical pattern used by
      `feedback`, `visionSettings`, `searchSkill`.
- [x] `isOssLicensesSheetOpen` follows the identical pattern in `SettingsHomeUiState` as
      `isFeedbackSheetOpen`, `isVisionSettingsSheetOpen`.
- [x] `OssLicenses.entries` is alphabetically sorted.
- [x] The LazyColumn in `OssLicensesBottomSheet` uses stable `key = { it.name }`.
- [x] No `NavController` passed into any composable.
- [x] No new Gradle dependencies.
- [x] All composables accept `modifier: Modifier = Modifier` as last parameter.
- [x] `@Suppress("LongParameterList")` already present on `SettingsScreen`; new param covered.
- [x] Preview composables do not reference Hilt or real ViewModels.
