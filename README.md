# File manager

A native Android file manager built with **Kotlin, Jetpack Compose and Material 3**. Application ID: `com.nexa.filemanager`. Android 8.0+ (API 26), compile/target API 35. No WebView, transfer server, analytics, or network permission.

## Get the APK

1. Push this project to GitHub and open **Actions → Build Android APK**.
2. Run the workflow, or let a push/pull request trigger it automatically.
3. When the run succeeds, download **file-manager-debug-apk** from its **Artifacts** section.
4. Unzip the artifact and install `app-debug.apk` on an Android device. Android may ask you to allow installation from that download source.
5. In the app, tap **Allow file access**, enable **All files access**, then return to the app. On Android 8–10, grant the legacy storage permissions instead.

**සිංහල:** GitHub Actions build එක සාර්ථක වූ පසු `file-manager-debug-apk` artifact එක download කර unzip කරන්න. එහි ඇති `app-debug.apk` එක Android phone එකට install කරන්න.

The workflow also runs JVM unit tests, Android lint and an R8-optimized release compilation. Release output is unsigned; distribution outside debug/testing requires your own release signing configuration. No signing secrets are included.

> **Verification status:** the Android SDK and Google Maven dependencies are not available in the coding sandbox. An APK has **not** been built or device-tested here. Kotlin 1.9.23/Java 17 standalone checks passed for file rules (43 assertions) and vault cryptography (13 checks). Full dependency resolution, Compose compilation, lint, R8 and device behavior must be confirmed by the included GitHub Actions workflow and the device checklist in [TESTING.md](TESTING.md). This is not a claim of a verified production release.

## Build locally

Install Android Studio, **JDK 17**, Android SDK **35** and build tools **34.0.0**. Set `ANDROID_HOME`, or set `sdk.dir` in an untracked `local.properties` file.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace
./gradlew assembleRelease --stacktrace
```

On Windows, use `gradlew.bat`. Gradle **8.7** and its wrapper are checked in. AGP **8.3.2**, Kotlin **1.9.23**, Compose compiler **1.5.13**, Compose BOM **2024.04.01**, and Java **17** are pinned. AGP's expected warning for compile SDK 35 is explicitly acknowledged; upgrading AGP later should be a deliberate, separately verified change.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Interface

- OLED black and clean daylight palettes; theme and hidden-file preferences persist.
- Bento dashboard, violet/cyan/pink accents, rounded translucent-gradient cards.
- Animated Canvas storage ring, gently animated vector branding, fade/vertical-slide page transitions.
- Home, Browse, Tools, Favorites and Vault navigation.
- Folder-scoped, case-insensitive search, breadcrumbs and name/size/recent sorting.
- Long-press for multi-select; use the overflow menu for per-file actions.
- Clipboard tray stays visible while navigating to a paste destination.
- Lazy lists use stable paths/IDs. Scanning, filesystem work, hashing, decoding, filtering and crypto run off the UI thread. Visible directories use a lifecycle-aware, debounced `FileObserver`.

## Modules and how to use them

| Module | Behavior |
| --- | --- |
| Permission engine | Android 11+ app-specific all-files settings with a fallback; Android 13+ granular media permission request; Android 14+ selected-visual-media permission. The banner reflects access after returning from Settings. |
| Copy / cut / paste | Long-press, select files, Copy or Move, browse to a destination and use the floating clipboard tray. Cross-volume transfers use a temporary staging copy. Conflicts stop with an explanation; existing files are not deliberately overwritten. |
| Rename / folders | Overflow → Rename; Browse → New folder. Single-component names are validated, including traversal and control-character rejection. |
| Search / navigation | Search the current folder or scanned collection. Tap breadcrumbs to navigate; long-press items to select them. |
| Collections | Scan accessible storage for Images, Videos, Audio, Documents, APKs, Archives and Downloads. Counts appear after indexing. Scans can be stopped. |
| Storage | `StatFs` total/used/available values for shared internal storage and detected removable volumes. Refreshes on resume, folder changes and completed operations. Tap a card to browse that volume. |
| Junk | Review `.tmp`, `.log`, `.bak`, `.old`, `.thumb*` and empty files. Recycle candidates with confirmation, or select individual files. **These are candidates, not guaranteed disposable files.** |
| Duplicates | Group by exact size, compare the first 64 KB with SHA-256, then verify whole-file SHA-256. Review paths; recycling extras keeps the first member of each group. Zero-byte files are handled by Junk instead. |
| Large files | Accessible files **larger than 25 MiB**, in descending size order. |
| Private vault | Select files → Vault → create/unlock a 6–12 digit PIN. Files move into `filesDir/safe_vault` as encrypted `.vault` files. Strong biometrics can unlock an already-configured vault. Restore decrypts to the original parent or shared internal storage if that parent is unavailable. |
| Hidden files | Top-bar eye toggle; dot-prefixed items are excluded by default. Enable it to review hidden junk candidates. |
| Archives | Create ZIPs from the Browse selection. ZIP, 7z and RAR overflow menus offer extraction to a new sibling folder. ZIP is implemented with platform streams; 7z uses Commons Compress/XZ; RAR uses Junrar. |
| App manager | Tools → Apps. Search installed packages, include/exclude system apps, view base + split APK byte sizes, back up the **base APK only** to `/APK_Backups`, or launch Android's uninstall confirmation. |
| System explorer | Tools → System explorer. Read-only `/`, `/system`, `/vendor`, `/data`, `/proc` navigation with readability and visible item counts. No `su` command or privilege escalation. |
| Cloud hub | Google Drive, OneDrive and Dropbox entries open Android's `ACTION_OPEN_DOCUMENT` picker. Choose a provider from its menu; the selected document becomes an app-private local copy. Open copies with Imported documents. |
| Viewers / editor | Downsampled image preview; UTF-8 monospace editor for text/code files up to 2 MiB with atomic Save and unsaved-edit confirmation. Audio/video and other formats use Android's app chooser via `FileProvider`. |
| Themes | Instant runtime theme toggle, persisted across launches. |
| Recycle bin | Delete moves items and original-path metadata into `filesDir/recycle_bin`. Restore never intentionally replaces an existing destination. Empty trash is permanent and requires confirmation. |
| Bookmarks / properties | Bookmark any file/folder via its menu. Properties show full path, exact/formatted bytes, last-modified time and read/write/hidden flags. Folder byte values are **not recursive totals**. |

## Safety and platform boundaries

- All-files access does **not** grant access to other apps' private data, Android's restricted `Android/data` / `Android/obb` directories, or protected system partitions. Unreadable folders are reported/skipped. Mutation methods independently restrict protected/system paths.
- Without all-files access, granular media permissions do not provide unrestricted folder management. This app does not promise a complete media library under selected-photo-only access.
- SD card operations depend on the volume and Android/OEM access rules. A readable card may not be writable; failures are surfaced rather than bypassed.
- Vault content uses **AES-256-GCM with separately authenticated 64 KiB records**, unique random nonces, and authenticated file identity, size and record ordering. This bounds crypto memory use. PIN verification uses salted PBKDF2-HMAC-SHA256 (210,000 iterations), constant-time comparison and a persisted one-minute lockout after five failures. Encryption keys remain in Android Keystore. PIN/biometric authentication gates the app UI, not a hardware-auth-bound key.
- The vault locks when the app backgrounds or the user leaves its screen. Vault screenshots are blocked. There is no PIN recovery backdoor. **Clearing app data/uninstalling destroys the vault key, vault files, imported documents and trash.** Maintain independent backups.
- Originals are removed only after a successful encrypted write. Restoration authenticates every record before publishing the staged output. Temporary plaintext files may exist during restore inside the selected destination; this is not forensic secure erasure.
- Archive extraction rejects directory traversal, absolute/backslash paths, duplicate entries, excessive entry counts and excessive decompressed size. It writes to a new staging directory, then publishes the result. The size budget is the lesser of 8 GiB or half the available destination space; the entry cap is 100,000.
- ZIP creation and ordinary, unencrypted ZIP/7z/RAR4 extraction are supported. **RAR5, multipart and password-protected archives are not promised**; use Open with for an external archive app. TAR/GZ files are categorized, but extraction is not implemented for those formats.
- Cloud access is **SAF import**, not a direct OAuth mount, background sync or cloud upload. Provider apps must expose an Android DocumentsProvider. Provider availability depends on the installed app; no provider IDs are hard-coded.
- App-manager sizes exclude private app data. Base APK backups alone cannot reinstall many split-package apps. System packages generally cannot be uninstalled. `QUERY_ALL_PACKAGES` and `MANAGE_EXTERNAL_STORAGE` require policy justification if publishing to Google Play.
- Multi-item operations can partially succeed before a later item fails. Already-completed items remain safely transferred/recycled; the app reports the error and refreshes. No destructive operation is advertised as a filesystem-wide transaction.
- No FTP, LAN, SMB, wireless transfer, Internet permission or embedded cloud credentials.

## Project layout

```text
.
├── .github/workflows/build_apk.yml
├── .gitignore
├── README.md
├── TESTING.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/nexa/filemanager/
        │   │   ├── MainActivity.kt
        │   │   ├── FileViewModel.kt
        │   │   ├── data/
        │   │   │   ├── FileRules.kt
        │   │   │   ├── FileRepository.kt
        │   │   │   ├── VaultCodec.kt
        │   │   │   └── VaultRepository.kt
        │   │   └── ui/
        │   │       ├── Theme.kt
        │   │       ├── Components.kt
        │   │       ├── FileManagerApp.kt
        │   │       └── Screens.kt
        │   └── res/
        │       ├── drawable/ic_launcher_background.xml
        │       ├── drawable/ic_launcher_foreground.xml
        │       ├── mipmap-anydpi-v26/ic_launcher.xml
        │       ├── mipmap-anydpi-v26/ic_launcher_round.xml
        │       ├── values/strings.xml
        │       ├── values/themes.xml
        │       └── xml/file_paths.xml
        └── test/java/com/nexa/filemanager/data/
            ├── FileRulesTest.kt
            └── VaultCodecTest.kt
```
