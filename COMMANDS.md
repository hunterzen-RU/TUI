# T-UI Launcher Development & Deployment Guide

This document summarizes the essential commands for building, installing, and managing the T-UI Linux CLI Launcher fork.

## 🛠 Building the APK

To perform a clean build of the F-Droid version (includes SMS permissions):

```bash
# Ensure gradlew is executable
chmod +x gradlew

# Build the F-Droid Debug APK
./gradlew assembleFdroidDebug
```

**Output Path:** `app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk`

---

## 📱 Emulator Deployment (ADB)

To install the APK on your Pixel 9 Pro emulator (or any physical device connected via USB):

```bash
# 1. Start the emulator (if not already running)
emulator -avd Pixel_9_Pro -gpu host -accel on &

# 2. Wait for the device and install (overwriting existing)
adb wait-for-device
adb install -r app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk
```

---

## 🐧 BusyBox Management

The launcher now features a built-in BusyBox manager to enable standard Linux commands.

### Installation
In the T-UI terminal, type:
```bash
bbman -install
```
This downloads the architecture-specific ELF binary, verifies its **SHA-256 hash**, and sets up the environment.

### Usage
Once installed, you can use any Linux command directly:
```bash
ls -la
grep "search_term" file.txt
busybox --list
```

### Removal
To clean up the environment:
```bash
bbman -remove
```

---

## 🔍 Useful ADB Debugging Commands

```bash
# View real-time logs (filtered for T-UI)
adb logcat | grep ohi.andre

# Uninstall the launcher via ADB
adb uninstall ohi.andre.consolelauncher

# Push a file to the launcher's internal storage
adb push local_file.txt /data/user/0/ohi.andre.consolelauncher/files/
```

---

## 🛡 Security Note
All binaries are verified using hardcoded SHA-256 hashes found in `app/src/main/java/ohi/andre/consolelauncher/tuils/BusyBoxInstaller.java`. All network transport is forced over HTTPS.

---

## ⌨️ User Command Reference (New Features)

### 🤖 AI Management (`ai`)
Integrate Google's Gemini AI or your local LLM server directly into your terminal.
*   `ai [prompt]`: Ask the configured AI a question.
*   `ai -key [apikey]`: Set your Google Gemini API key (stored in `ai.xml`).
*   `ai -mode [global|local]`: Switch between Google Gemini (`global`) and local LLM server (`local`).
*   `ai -test`: Verify AI connection and responsiveness for the current mode.
*   `ai -set [prompt] [var]`: Save AI response into a T-UI variable.
*   `ai -sys [prompt]`: Set the AI's system personality.
*   **Configuration**: Fine-tune models and local URLs in `ai.xml` via `config ai.xml`.

### 📦 Package Management (`pkg` / `tui`)
Manage hybrid scripts and native Termux packages.
*   `pkg -install [name]`: Install custom `.tui` scripts or native packages.
*   `pkg -search [query]`: Search the GitHub repository for tools.
*   `pkg -ls`: List all installed packages and scripts.
*   `pkg -repo [url]`: View or set the custom GitHub Pages repo source.
*   `tui -set [apt|pkg|hybrid]`: Force a specific package manager engine.
*   `tui -run [cmd]`: Run any installed Termux binary (python, git, etc.) via `login -d`.
*   `tui -update-app`: Check for and install T-UI launcher updates.

### 📊 Dashboard (`dashboard`)
Interactive system telemetry and customizable widget matrix.
*   `dashboard -on | -off`: Toggle the system dashboard.
*   `dashboard -toggle`: Switch between **Grid** and **Native** modes.
*   `dashboard -layout [RxC]`: Change grid dimensions (e.g., `2x2`, `3x3`, up to `5x5`).
*   `dashboard -items`: List all configured box items from `dashboard-item.xml`.
*   `dashboard -set [box#] [type] [title] [color]`: Configure a specific box item (e.g. `dashboard -set 1 cpu "[ CPU LOAD ]" #00FF66`).
*   `dashboard -add [type] [title] [color]`: Add a new box item to the matrix.
*   `dashboard -del [box#]`: Remove a box item and re-index remaining boxes.
*   `dashboard -reset`: Reset `dashboard-item.xml` to default 25-box layout.
*   `dashboard -itemfile`: Directly open `dashboard-item.xml` in your text editor.
*   **Expansion**: Click the grid dashboard to expand to a full **5x5 Pro Grid**; click again to restore previous dimensions.
*   **Long Press**: Configurable via `dashboard.xml` (`long_press_action`). Supports `ai_chat` (Interactive Chat) and `app_launcher` (Grid UI).

### 🏷️ Variables & System Telemetry (`variables`)
Global variables stored in `variable.xml` (also mirrored to `variables.xml`).
*   `variables -ls`: List all variables with their type and description.
*   `variables -get [name]`: Show variable details, description, and live resolved value.
*   `variables -set [name]=[value]`: Save a new or update an existing variable.
*   `variables -desc [name] [description]`: Set or update a variable's descriptive summary.
*   `variables -file`: Open `variable.xml` in your text editor.
*   **Pre-filled System Variables**: `variable.xml` is automatically pre-filled with all 19 system telemetry variables:
    *   `{{battery}}`, `{{battery_level}}`, `{{battery_status}}` (Charging/Discharging)
    *   `{{cpu_usage}}`, `{{ram_total}}`, `{{ram_avail}}`, `{{ram_used}}`
    *   `{{storage_total}}`, `{{storage_avail}}`, `{{uptime}}`
    *   `{{device}}`, `{{user}}`, `{{os_version}}`
    *   `{{network}}`, `{{network_name}}`, `{{network_upload}}`, `{{network_download}}`
    *   `{{date}}`, `{{time}}`
*   Available everywhere: in commands (`echo $battery_status`), `.tui` scripts, and dashboard box widgets!

### 🧩 Modular Plugin System & Code Studio (`plugin`)
Develop, install, manage, and launch custom T-UI plugins in Kotlin, Java, or HTML5.
*   `plugin -ls`: List all installed plugins from `plugins/`.
*   `plugin -info [id]`: Display plugin metadata, file tree structure, and permissions.
*   `plugin -launch [id] [args]`: Execute or launch a plugin.
*   `plugin -studio`: Open the integrated GUI Plugin Code Studio with templates and editor.
*   `plugin -edit [id]`: Open a specific plugin directly in Code Studio.
*   `plugin -create [id] [template]`: Create a new plugin scaffold (`kt`, `java`, `html`).
*   `plugin -templates`: Browse and fetch online templates from `https://hunterzen-ru.github.io/TUI/Templates/info.json`.
*   `plugin -install [url|zip]`: Install a plugin from a URL or local ZIP archive.
*   `plugin -export [id]`: Package a plugin as a distributable `.zip` archive.
*   `plugin -rm [id]`: Uninstall an installed plugin.
*   `plugin -sample`: Verify or regenerate the bundled sample plugin.
*   See `PLUGIN_DEVELOPMENT.md` for the complete development guide and architecture specification.

### 📝 Automation & Scripting (`tedit` / `run`)
Write and execute powerful automation workflows.
*   `tedit [file.tui]`: Open the Nordic-style script editor with syntax highlighting.
*   `run [file.tui]`: Execute a `.tui` automation script.
*   See `TUI_SCRIPTING.md` for syntax details.

### ⌨️ Terminal Interface Enhancements
*   **Command History**: Persistent across app restarts. Cycle through previously executed commands using the **Up** and **Down** arrow keys (D-Pad or external keyboard).
*   **Intelligent Auto-completion**: Suggestions now include:
    *   System commands and built-in T-UI aliases.
    *   Installed Android applications.
    *   **Packages** installed via `pkg` (both scripts and native binaries).
    *   Files and directories in the current working directory.

### 📊 System Telemetry & ASCII Visualizer (`sys`)
View real-time system metrics, hardware architecture, and ASCII graphs directly in your terminal:
*   `sys`: Comprehensive hardware, architecture, Android OS, and storage overview.
*   `sys -graph`: Live ASCII bar graphs for CPU, RAM, Disk, and Battery:
    ```text
    CPU  [████████░░░░░░░░░░░░] 38%
    RAM  [████████████░░░░░░░░] 53% (3.2G / 6.0G)
    DISK [██████████████░░░░░░] 68% (84G / 128G)
    BATT [████████████████████] 95% ⚡ Charging
    ```
*   `sys -battery`: Detailed battery health, temp (°C), voltage (mV), and power source.
*   `sys -net`: Network stats, SSID, link speed, and data transfer counters.
*   `sys -storage`: Internal and partition storage usage.

### 🔗 Unix Pipes & Filters (`|`)
Chain commands together using the standard Unix pipe syntax:
*   `apps -ls | grep Google` -> Filter installed apps matching a query.
*   `history | tail 5` -> Display the last 5 executed commands.
*   `variables -ls | grep battery` -> Search across predefined/custom variables.
*   `plugin -ls | sort` -> Alphabetically sort installed plugin list.
*   `notes -ls | wc -l` -> Count note entries.
*   Supported filters: `grep [-i|-v]`, `head [n]`, `tail [n]`, `sort [-r]`, `wc [-l|-w|-c]`, `upper`, `lower`.

### 🎨 Developer Colorway Theme Presets (`theme`)
Switch seamlessly between iconic developer and hacker colorways:
*   `theme -ls`: List all presets with color breakdown.
*   `theme -preset matrix`: Classic phosphor green on deep obsidian black.
*   `theme -preset dracula`: Renowned Dracula violet, pink, and cyan palette.
*   `theme -preset catppuccin`: Modern warm pastel mocha aesthetic.
*   `theme -preset nord`: Clean Arctic frost blue and snow palette.
*   `theme -preset gruvbox`: Retro warm groove contrast palette.
*   `theme -preset cyberpunk`: Neon yellow, bright cyan, and laser red.

### 🎵 Media Session Telemetry & Controls (`music`)
*   `music -now`: Renders a rich ASCII music player with track, artist, and timeline:
    ```text
    🎵 Midnight City - M83
    ▶ Playing [━━━━●━━━━━━━━━━━━] 1:24 / 4:03 (35%)
    ```
*   `music -toggle`: Toggle playback instantly.
*   New dynamic variables: `{{media_track}}`, `{{media_artist}}`, `{{media_album}}`, `{{media_playback_state}}`.

### 🛡️ Architecture-Aware App Updater & Verifier (`tui`)
*   `tui -check_update`: Scans the repository for available T-UI releases tailored to your CPU architecture (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `universal`).
*   `tui -update_app`: Automatically detects device ABI, downloads matching APK with live progress, verifies the **SHA-256 checksum**, and launches the Android package installer.

### 🎙️ Hands-Free Voice-to-Text Input (`voice`)
Dictate and execute commands completely hands-free using Android's `SpeechRecognizer`:
*   `voice`: Initiates speech recognition in the terminal with live partial transcription and automatic execution on completion.
*   **Microphone Toolbar Button**: Tap the new 🎙️ button on the bottom toolbar to speak immediately.
*   **Long-Press Shortcuts**: Long-press the Enter button (`ic_enter`) or the command prefix (`$` / `>`) to start voice dictation with long-press haptic feedback.

### 📳 Tactile Haptic Feedback
Enhanced physical sensation across all terminal interactions:
*   **Key Typing**: Tactile tick (`HapticFeedbackConstants.KEYBOARD_TAP`) on every character typed or deleted.
*   **Virtual Keys & Navigation**: Crisp feedback (`HapticFeedbackConstants.VIRTUAL_KEY`) on Enter, D-Pad Up/Down history cycling, and cursor arrows.
*   **Command Execution**: Confirmation pulse (`HapticFeedbackConstants.CONFIRM`) on command execution.
*   **Long-Press Gestures**: Strong haptic signal (`HapticFeedbackConstants.LONG_PRESS`) on voice shortcuts and context actions.
