# T-UI Launcher: Changelog & Bug Fixes (v6.15.1 – v6.15.15-beta)

This document provides a comprehensive log of architectural enhancements, new features, and bug fixes introduced across releases **v6.15.1** through **v6.15.15-beta** for the T-UI Launcher Android platform.

---

## 📌 Release Summary

| Version | Focus Area | Highlights |
|---|---|---|
| **v6.15.15-beta** | **Direct JNI Execution & PRoot Bridge** | `jniLibs` ARM64 native binary execution (`executeNativeLibBinary`), `.deb` extraction `EEXIST` fix, automated PRoot dependency bridge, and `mpv` driver protection. |
| **v6.15.10** | **Inbuilt Native Macro Engine** | Native command recording, persistent `macros.json`, argument substitution (`$1`, `$*`), parameter passing, removal of external MacroDroid dependencies. |
| **v6.15.9** | **Speech & Tactile Haptic System** | Hands-free SpeechRecognizer input (`voice`, toolbar microphone), physical `HapticFeedbackConstants` haptic feedback on typing, navigation, and execution. |
| **v6.15.8** | **System Telemetry & ASCII Visualizer** | ASCII bar visualization (`sys -graph`), memory breakdown, battery percentage & charging status telemetry. |
| **v6.15.7** | **Unix Piping & Filter Chain Engine** | Command piping (`| grep`, `head`, `tail`, `sort`, `wc`, `upper`, `lower`), multi-stage CLI data transforms. |
| **v6.15.6** | **Architecture-Aware App Updater** | GitHub release asset checking (`tui -update_app`), device ABI detection (arm64-v8a, armeabi-v7a, x86_64), SHA-256 integrity verification. |
| **v6.15.5** | **Theme Presets & Palette Switcher** | Modern developer themes (`matrix`, `dracula`, `catppuccin`, `nord`, `gruvbox`) with hot-reloading. |
| **v6.15.4** | **Media Session Telemetry** | Active playback tracking (`music -now`), variables `{{media_track}}` & `{{media_playback_state}}`, notification listener integration. |
| **v6.15.3** | **Jetpack Compose MAD UI Overhaul** | Redesign of `PluginStudioActivity`, `AiChatActivity`, and `GridAppLauncherActivity` conforming to Material Design 3. |
| **v6.15.2** | **Plugin Architecture & Web Templates** | Plugin installer, manager, and GUI code studio; dynamic template fetching from GitHub Pages repository. |
| **v6.15.1** | **Modern Android Baseline** | Initial migration to Android 14 (API 34), edge-to-edge support, and build system stabilization. |

---

## 🚀 Version-by-Version Detailed Breakdown

### 🔹 v6.15.15-beta — Direct Native JNI Execution & PRoot Bridge
- **Direct JNI Native Execution Engine (`TermuxSessionManager.kt`)**:
  - Implemented `executeNativeLibBinary` using `ProcessBuilder` pointing directly to `context.applicationInfo.nativeLibraryDir`.
  - Configured `jniLibs.srcDirs = ['src/main/jniLibs']` in `app/build.gradle` and created `app/src/main/jniLibs/arm64-v8a/`.
  - Added auto-discovery and launcher shim generation in `$HOME/tui/bin` for binaries disguised as shared libraries (`lib<name>_exec.so` / `lib<name>.so`).
- **PRoot Execution Bridge & Dependency Auto-Provisioning (`PackageManager.java`)**:
  - `ensurePackageDependencies()` automatically detects if `proot` is missing when launching or installing APT packages and installs the PRoot execution bridge.
  - Generates launcher shims mapping `/data/data/com.termux/files/usr` to `$PREFIX` via PRoot, ensuring Termux shared libraries load without Bionic linker errors.
- **Unattached GUI Driver Protection (`mpv`)**:
  - Added parameter safety handling to launcher shims for `mpv`. Invoking `$ mpv` without command line arguments automatically appends `--help`, preventing video driver segfaults when running without an attached surface.

---

## 🛠️ Bug Fixes & Stability Improvements (v6.15.15-beta)

1. **Extraction `symlink failed: EEXIST (File exists)`**:
   - *Cause*: Standard `java.io.File.exists()` returns `false` for dangling symlinks, skipping `delete()` before calling `Os.symlink()`.
   - *Fix*: Added unconditional target path cleanup (`targetFile.delete()` and `Os.remove(targetFile.getAbsolutePath())`) before symlink creation or file writing in `AptPackageResolver.java` and `ProotDistroManager.java`.

2. **PRoot Segmentation Faults**:
   - *Cause*: Termux ARM64 binaries (`mpv`, `ffmpeg`) missing required system mounts (`/dev`, `/proc`, `/sys`) under PRoot ptrace.
   - *Fix*: Updated launcher shims with complete system mount flags (`-b /dev -b /proc -b /sys -b $PREFIX/tmp:/tmp -b /data`) and `--link2symlink -0`.

3. **Android Bionic Linker Rejection (`libass.so has invalid shdr offset/size`)**:
   - *Cause*: Android host Bionic linker (`/system/bin/linker64`) rejects Termux-compiled shared libraries (`.so`) when loaded directly outside PRoot due to non-Bionic GNU ELF section header offsets.
   - *Fix*: Routed Termux APT packages through PRoot path translation (`-b $PREFIX:/data/data/com.termux/files/usr`), allowing Termux shared libraries to load through PRoot path mapping.

4. **Known Issues / Limitations**:
   - Standalone execution of Termux-compiled packages requires the PRoot execution bridge when linking against shared libraries with Termux-specific ELF section offsets.
