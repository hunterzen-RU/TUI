# T-UI Console Launcher — Comprehensive Application State & Developer Guide

## 1. Overview & Key Capabilities
T-UI Console Launcher (`com.huntech.tui`, version `v6.15.15-beta`) is a modern Android terminal launcher with advanced command-line features, standalone package management, Unix-style pipe filtering, voice recognition, built-in macro automation, and native binary execution.

### Key Application Features:
- **Terminal Command Interface**: Full CLI user experience with input history, auto-completion, customizable colors, and themes (`matrix`, `dracula`, `catppuccin`, `nord`, `gruvbox`).
- **Package Management Engine**: Custom package index and Debian APT repository integration (`pkg install`, `apt install`).
- **Direct JNI Execution (`jniLibs`)**: Executes native ARM64 binaries placed in `app/src/main/jniLibs/arm64-v8a/` (`lib<name>_exec.so`) via `ProcessBuilder` without PRoot or ptrace overhead.
- **PRoot Execution Bridge**: Path translation mapping `/data/data/com.termux/files/usr` to internal app storage `$PREFIX` (`files/usr`) for Termux binaries requiring Termux path bindings.
- **PRoot Linux Distributions**: Support for installing, listing, logging into, and running Linux containers (`proot install alpine`, `proot login alpine`).
- **Inbuilt Macro Engine (`MacroEngine.kt`)**: Native command sequence recording, dynamic parameter substitution (`$1`, `$*`), and JSON persistence (`macros.json`).
- **Unix Piping Engine (`|`)**: Support for command chaining and stream filters (`grep`, `head`, `tail`, `sort`, `wc`, `upper`, `lower`).
- **Voice Recognition & Speech-to-Text**: Speech-to-text input via `VoiceRecognitionManager` and toolbar microphone UI button.
- **Haptic Feedback**: Physical tactile feedback on key presses, navigation, and command execution using `HapticFeedbackConstants`.
- **System Telemetry & Visualizer**: Hardware telemetry (`sys -graph`) for CPU, RAM, storage, battery level, and charging status.

---

## 2. Architecture & Core Systems

- **Language & Runtime**: Java 8 & Kotlin 1.9+, Jetpack Compose, Material Design 3, Coroutines, Android 14 (API 34) with `minSdk 26`.
- **Execution Architecture**:
  - **Command Dispatcher**: User commands are parsed by `MainManager.java` and routed through `CmdTrigger` implementations (`StandalonePackageTrigger`, `AppTrigger`, `RedirectCommand`, etc.).
  - **Terminal Output Engine**: Thread-safe `Tuils.sendOutput()` sends colored terminal output to `TerminalManager.java`.
  - **Binary Execution Subsystem**:
    - `TermuxSessionManager.kt`: Creates environment variables (`HOME`, `PREFIX`, `PATH`, `LD_LIBRARY_PATH`), executes commands via `/system/bin/sh` or direct `ProcessBuilder` (`executeNativeLibBinary`).
    - `AptPackageResolver.java`: Downloads, parses, and extracts Debian `.deb` packages with symlink handling and launcher shim generation.
    - `ProotDistroManager.java`: Manages rootfs containers and PRoot execution commands.

---

## 3. Project File Tree & Component Directory

```text
/
├── app/
│   ├── build.gradle                              (App Gradle build configuration: applicationId "com.huntech.tui", v6.15.15-beta, jniLibs sourceSets)
│   ├── proguard-rules.pro                        (ProGuard shrinking & obfuscation rules)
│   └── src/main/
│       ├── AndroidManifest.xml                  (App permissions, services, receivers, activity declarations)
│       ├── jniLibs/
│       │   └── arm64-v8a/                       (Native ARM64 binaries disguised as lib<name>_exec.so)
│       ├── res/                                 (Layouts, drawables, values, mipmaps, XML options)
│       └── java/ohi/andre/consolelauncher/
│           ├── LauncherActivity.java            (Main launcher Activity initialization & lifecycle)
│           ├── MainManager.java                 (Central command interpreter & dispatch triggers)
│           ├── UIManager.java                   (Terminal view hierarchy, themes, & layout bounds)
│           ├── commands/                        (Command abstractions & command implementations)
│           │   ├── Command.java                 (Interface for executable commands)
│           │   ├── CommandAbstraction.java      (Base class for built-in launcher commands)
│           │   ├── CommandGroup.java            (Groups related subcommands)
│           │   ├── CommandTuils.java            (Utilities for command parsing)
│           │   ├── CommandsPreferences.java     (Command preference settings)
│           │   ├── ExecutePack.java             (Execution context pack passed to commands)
│           │   ├── main/                        (Primary built-in commands)
│           │   │   ├── MainPack.java            (Package containing references to managers)
│           │   │   ├── Param.java               (Command parameter wrapper)
│           │   │   ├── raw/                     (Command raw implementations: ai, sys, pkg, tui, macro, etc.)
│           │   │   └── specific/                (Specific command handlers)
│           │   └── tuixt/                       (Text editor commands)
│           ├── managers/                        (Core subsystem managers)
│           │   ├── AiManager.java               (Gemini AI integration)
│           │   ├── AliasManager.java            (Custom command aliases)
│           │   ├── AptPackageResolver.java      (APT index fetching, .deb extraction, launcher shims)
│           │   ├── AppsManager.java             (App indexing and launch handling)
│           │   ├── ContactManager.java          (Device contacts query & shortcuts)
│           │   ├── DashboardItemManager.java    (Dashboard grid items)
│           │   ├── FileManager.java             (File system browsing & file operations)
│           │   ├── HTMLExtractManager.java      (HTML parsing utilities)
│           │   ├── MacroEngine.kt               (Native macro recording, parameters, JSON persistence)
│           │   ├── NotesManager.java            (In-app terminal notes)
│           │   ├── PackageHelper.java           (Termux bridge & standalone package helper)
│           │   ├── PackageManager.java          (Package download, checksums, install queues)
│           │   ├── PluginManager.java           (Plugin system lifecycle & studio)
│           │   ├── ProotDistroManager.java      (PRoot Linux containers distribution manager)
│           │   ├── RegexManager.java            (Regular expression text matching)
│           │   ├── RssManager.java              (RSS feed reader)
│           │   ├── SessionManager.java          (Terminal session switching)
│           │   ├── TerminalManager.java         (Terminal output view adapter & scroll buffer)
│           │   ├── TermuxSessionManager.kt      (Environment config, shell execution, executeNativeLibBinary)
│           │   ├── ThemeManager.java            (Theme color scheme manager)
│           │   ├── TimeManager.java             (Time and date formatting)
│           │   ├── TuiDownloadService.java      (Foreground service for background downloads)
│           │   ├── TuiLocationManager.java      (GPS location telemetry)
│           │   ├── VariablesManager.java        (Custom variable storage and substitution)
│           │   ├── VoiceRecognitionManager.java (Speech-to-text integration)
│           │   ├── flashlight/                  (Torch/Flashlight management)
│           │   ├── music/                       (MediaSession music playback monitor)
│           │   ├── notifications/              (Notification listener service)
│           │   ├── suggestions/                 (Command auto-completion suggestions)
│           │   └── xml/                         (XML options & preference files)
│           ├── tuils/                           (Utility classes & interface definitions)
│           │   ├── BusyBoxInstaller.java        (Embedded BusyBox extraction & installation)
│           │   ├── CustomExceptionHandler.java   (Global uncaught exception handler)
│           │   ├── NetworkUtils.java            (HTTP & network connectivity helpers)
│           │   ├── ProgressBarUtils.java        (ASCII progress bar formatting)
│           │   ├── StoppableThread.java         (Cancelable background threads)
│           │   ├── TuiEditorActivity.java       (GUI text editor activity)
│           │   ├── TuiScriptEngine.java         (T-UI script execution engine)
│           │   ├── Tuils.java                   (General system utilities & terminal output dispatch)
│           │   ├── html_escape/                 (HTML entity escaping)
│           │   ├── interfaces/                  (Component interfaces: CommandExecuter, Outputable, etc.)
│           │   └── libsuperuser/                (Shell execution gobblers)
│           └── ui/
│               └── theme/                       (Jetpack Compose theme & color definitions)
├── build.gradle                                 (Root Gradle build script)
├── metadata.json                                (AI Studio platform metadata)
├── fi-bug.md                                    (Changelog, bug fixes, & release history)
├── app-state.md                                 (This documentation file)
├── settings.gradle                              (Gradle settings: rootProject.name)
└── gradle.properties                            (Gradle daemon & JVM args settings)
```

---

## 4. Key File Details & Responsibilities

| File Path | Role & Primary Responsibility |
|---|---|
| `app/build.gradle` | Main build script. Defines `applicationId "com.huntech.tui"`, `versionName "v6.15.15-beta"`, compileSdk 34, dependencies, and `jniLibs.srcDirs`. |
| `MainManager.java` | Main command router. Handles command triggers (`StandalonePackageTrigger`, `AppTrigger`, `RedirectCommand`), pipeline split (`\|`), and output delivery. |
| `TermuxSessionManager.kt` | Configures internal environment (`HOME`, `PREFIX`, `PATH`, `LD_LIBRARY_PATH`), executes commands via ProcessBuilder, and provides `executeNativeLibBinary` for JNI binaries. |
| `AptPackageResolver.java` | Downloads Termux APT index, parses Debian `.deb` packages, extracts archives, handles dangling symlinks unconditionally, and writes launcher shims. |
| `PackageManager.java` | Manages package queue downloads, SHA-256 verification, dependency resolution, and auto-installs `proot` when required. |
| `ProotDistroManager.java` | Handles downloading, rootfs extraction, and login/run execution for Linux distributions (Alpine, Debian, Ubuntu). |
| `MacroEngine.kt` | Manages native macro recording (`macro -rec`), parameter substitution (`$1`, `$*`), and persistence in `macros.json`. |
| `Tuils.java` | Central utility class providing `sendOutput(...)` for delivering colorized output lines to the terminal UI thread. |

---

## 5. Critical Guidelines & Recommendations for AI Agents

1. **Native Binary Execution (`jniLibs`)**:
   - For high-performance, non-Termux binaries, place compiled ARM64 binaries in `app/src/main/jniLibs/arm64-v8a/lib<name>_exec.so`.
   - Android automatically extracts them with `0755` executable permissions into `context.applicationInfo.nativeLibraryDir`.
   - Always execute them via `sessionMgr.executeNativeLibBinary(binaryName, args)` for maximum native speed without ptrace overhead.

2. **Termux & Debian Package Compatibility**:
   - Termux packages in `$PREFIX` (`files/usr`) rely on path mapping `/data/data/com.termux/files/usr`.
   - Termux binaries linking against shared libraries (e.g. `libass.so`) must be run through PRoot (`$PREFIX/bin/proot`) to pass Android Bionic dynamic linker ELF section header checks.
   - Always ensure launcher shims created via `AptPackageResolver.createLauncherShim` map system paths (`-b /dev -b /proc -b /sys -b $PREFIX:/data/data/com.termux/files/usr -b /data`).

3. **Symlink Extraction & File Overwrites**:
   - Standard Java `File.exists()` returns `false` for dangling symlinks.
   - When overwriting or extracting symlinks/files, unconditionally call `targetFile.delete()` and `Os.remove(targetFile.getAbsolutePath())` before creating new symlinks or opening output streams.

4. **Terminal UI Threading**:
   - Never block the UI thread when executing shell commands or network downloads.
   - Run commands inside `StoppableThread` or background threads, and dispatch output back to the terminal using `Tuils.sendOutput(context, message)`.

5. **Gradle Build Compatibility**:
   - Never create or use `local.properties`.
   - Never alter `debug.keystore` or signing configurations in `app/build.gradle`.
   - Always run `compile_applet` to verify compilation after file modifications.

---

 ## Other features

 Implement a Compose-based predictive text overlay that appears above the terminal input line, offering command suggestions based on the user's history and installed aliases.
 Create a formal Plugin Manager class that allows loading and unloading external JAR files as runtime modules to extend TUI's command set .