# Family Android release channel

This branch is the ongoing family source of the Spanish learning app.

- Source: `family-android-v2`
- Family APK: `family-builds/SpanishLearning.apk`
- Family updater: `family-updater-builds/SpanishLearningUpdater.apk`
- Former external APK: `phone-builds/SpanishLearning.apk` (keep frozen)
- Former external updater: `updater-builds/SpanishLearningUpdater.apk` (keep frozen)

The two Android updater implementations use the family APK feed.
The publisher scripts produce family release branches only.
The default-branch Android workflow allows publishing only when the PR head is `family-android-v2`.
CI is triggered by a PR from family-android-v2 to the conflict-free family-release-base branch. Do not merge the release PR: it is only a CI trigger.

**Not private:** All branches of this repository are public. This arrangement stops automatic updates for users pinned to the legacy channel, but does not prevent a person from finding public releases or accessing public content.

**Compatibility:** Family APK keeps the same Android package and signing certificate as existing installs. Install a newer signed family APK over the current app, never uninstall or clear app storage.

**Not yet migrated:** live-content and public vocab/story synchronization remain shared. Their access isolation requires separate work before declaring the external install completely offline.

## Family-only profile selector

From family build 371 onward the visible profiles are **Papa, Mama, Dima**. Pimen remains mapped as a legacy profile for backwards-compatible database naming and isolated storage, but is not listed in family UI, not copied from Papa at startup, and not iterated by family vocabulary synchronization. The old version 369 and its local SQLite data on Pimen's device are not modified.
