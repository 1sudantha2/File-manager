# Verification and device acceptance checklist

## Automated checks

GitHub Actions runs:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace
./gradlew assembleRelease --stacktrace
```

Committed JVM tests:
- `FileRulesTest`: safe names (including Sinhala/Unicode), traversal protection, categorization, binary size formatting and junk-candidate identification.
- `VaultCodecTest`: empty/small/multi-record round trips; ciphertext tampering, wrong keys, wrong file identities, truncation, trailing data, incorrect sizes and fresh nonces.

**Sandbox checks performed:** Kotlin 1.9.23 + Java 17 standalone execution of 43 file-rule assertions and 13 vault-codec checks passed. XML/YAML and wrapper integrity are checked separately. These standalone checks are not the full Gradle/JUnit suite. The Android build, lint, R8 and UI/device behavior remain unverified until CI/device execution; no APK is supplied as already built.

## Required device matrix

Run on API 26, 29, 30, 33, 34 and 35. Include an Android 14/15 device with selected-photo access and, if possible, one physical device with a removable SD card. Emulators cannot validate real SD cards, OEM restrictions or hardware biometric behavior reliably.

Use disposable copies of data, not the only copy of an important file.

## Permissions

- Fresh install: permission banner visible; deny permissions without crashing.
- Grant legacy read/write on API 26–29. Browse/create/copy/recycle in shared storage.
- API 30+: open the app-specific All files access settings; enable, return, verify banner disappears and browsing refreshes.
- Disable All files access outside the app and return. Banner returns; failures stay readable.
- API 33+: deny and separately grant each image/video/audio permission.
- API 34+: test selected visual media only; do not expect full folder management.
- Browse Android/data, Android/obb, /data and /proc. Inaccessible paths should show a restriction or be skipped, never crash or claim root access.

## File workflow

- Create a disposable folder, subfolder, empty file, Unicode-named file and a hidden file.
- Create folder and rename. Reject `..`, separators, blank names, existing names and control characters.
- Search with mixed case; verify folder-scoped results, collection filters, stable scrolling, sorting and breadcrumbs.
- Multi-select copy and move within one volume and across volumes; inspect bytes, content and source removal.
- Try pasting into a descendant and into a conflicting destination. Nothing should be overwritten.
- If a batch fails partway, confirm already-completed items are retained and remaining originals are preserved.
- Create/modify/delete a file using another app while Browse is visible. The debounced folder observer should refresh the list.
- Bookmark both files and folders; restart the app and open Favorites. Inspect a missing bookmark and properties.
- Check properties paths, flags, timestamps and exact bytes; directory sizes are intentionally non-recursive.

## Scanning and cleanup

- Index storage; counts exclude inaccessible app-data directories. Stop a scan and start another.
- Add `.tmp`, `.log`, `.bak`, `.old`, `.thumb*`, empty files and normal documents.
- Review Junk with hidden files on/off. Recycle candidates only after confirmation.
- Create files of equal size but different contents and files differing only after the first 64 KB. They must not be reported as duplicates.
- Copy an identical file into multiple folders. Only verified copies form a group, and recycling extras keeps one.
- Create files below, exactly at and above 25 MiB. Only the last should appear in Large files, sorted largest first.
- Check internal and SD storage totals against system values (available space can differ from raw free blocks). Recheck after transfer and trash operations.

## Trash and vault

- Recycle nested folders and files. Restart the app, restore and verify contents and original paths.
- Restore into an existing-name conflict. Original and trashed copies must both remain.
- Empty trash: cancel once, confirm once, verify permanent removal.
- Set matching and mismatching PINs; test short PIN rejection. Five bad attempts should trigger a persisted one-minute lockout.
- Protect an empty file, small document, multi-MB file and large video. Check original removal only after encryption succeeds.
- Lock/unlock with PIN and enrolled strong biometrics; test biometric cancellation/unavailability.
- Background the app, rotate/recreate the activity, navigate away and return. Vault must lock on background/leaving its screen. Screenshots of the vault should be blocked.
- Restore protected files; verify byte equality and no overwrite on name conflicts.
- On a disposable debug installation, corrupt an encrypted record and attempt restore. No final plaintext file should be published, and the vault copy should remain.
- Document that uninstalling/clearing app data removes vault, key, imports and trash; test only with disposable content.

## Archives, viewers, apps and cloud

- ZIP a file selection and nested/empty folders, then extract and compare contents.
- Test ordinary ZIP, RAR4 and 7z. Test unsupported/password/multipart/RAR5 cases for understandable errors and external Open with fallback.
- Test traversal (`../`), duplicate-name and high-expansion archives. No path may escape the extraction destination; failed staging should be cleaned up.
- Preview large images without full-size decoding. Test unsupported/corrupt images.
- Edit supported UTF-8 text/code and save. Back out with unsaved changes and confirm discard. Reject files above 2 MiB.
- Launch audio/video and unsupported files via Android's chooser. Test when no compatible player is installed.
- In Apps, search user/system packages, inspect sizes, back up base APKs, and cancel/confirm a system uninstall prompt. Confirm split APK limitations are visible.
- Install/sign into a cloud app exposing DocumentsProvider. Pick one document from Android's picker, then open Imported documents. Test cancellation, provider absence, offline failure and remote access revocation.
- Confirm local edits do not claim to sync back to cloud providers.

## Presentation and release

- Deep dark/daylight themes, hidden-file settings and bookmarks survive relaunch.
- Verify small screen, landscape, large font size, keyboard insets, gesture and three-button navigation on API 35 edge-to-edge.
- Check launcher icon in round and adaptive launchers; inspect vector branding and storage ring animation.
- Inspect accessibility descriptions and touch targets with TalkBack.
- Run both debug and minified release builds. Install the debug artifact; sign a release APK yourself before release installation testing.
- Monitor memory and responsiveness while scrolling/scanning large directories, hashing duplicates and protecting a large file. No network server should be started.
