## 2025-04-01 - [Jetpack Compose Accessibility: Redundant announcements]
**Learning:** In Jetpack Compose, while interactive elements generally require a `contentDescription`, setting `contentDescription = null` on an `Icon` is correct and preferred when it is immediately accompanied by a descriptive `Text` component.
**Action:** Always verify if an Icon is accompanied by text before adding a `contentDescription`. If text exists, leave `contentDescription` as null to avoid redundant screen reader announcements.
