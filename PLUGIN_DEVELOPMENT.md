# T-UI Plugin Development Guide

T-UI features a modular plugin architecture that allows developers to write custom extensions in Kotlin, Java, or Web technologies (HTML5/CSS/JS). Plugins run directly within the T-UI engine with access to system telemetry, argument parsing, custom UI layouts, and the terminal command registry.

---

## 📁 1. Plugin Directory Structure

Every T-UI plugin resides in its own folder inside the `plugins/` directory:

```text
plugins/
└── my_plugin/
    ├── manifest.json        # Mandatory plugin metadata & configuration
    ├── icon.png             # Plugin launcher icon
    ├── start.kt             # Default Kotlin entry point (or start.java / index.html)
    ├── plugin_layout.xml    # Custom Android/Nordic UI layout definition
    └── assets/              # Static resources, configs, and assets
        └── data.json
```

---

## 📜 2. The `manifest.json` Specification

Each plugin must contain a `manifest.json` file in its root directory:

```json
{
  "name": "Battery & Thermal Monitor",
  "id": "battery_monitor",
  "version": "1.0.0",
  "target_app_version": "1.0",
  "description": "Real-time battery charge telemetry and thermal safety monitor",
  "entry_point": "start.kt",
  "tree": {
    "manifest.json": "Plugin configuration and metadata",
    "icon.png": "Plugin icon",
    "start.kt": "Kotlin entry point script",
    "plugin_layout.xml": "Custom Nordic UI layout",
    "assets/": "Static data and assets"
  },
  "permissions": [
    "android.permission.INTERNET",
    "android.permission.BATTERY_STATS"
  ],
  "manifest_specials": {
    "activity": "ohi.andre.consolelauncher.managers.plugins.PluginHostActivity",
    "intent_filter": [
      "android.intent.action.MAIN"
    ],
    "services": [],
    "receivers": [],
    "providers": []
  }
}
```

### Manifest Fields Explained:

| Field | Type | Description |
| :--- | :--- | :--- |
| `name` | String | Human-readable title of the plugin. |
| `id` | String | Unique lowercase slug (matches the folder name). |
| `version` | String | Semantic version (e.g., `1.0.0`). |
| `target_app_version`| String | Compatible version of the T-UI launcher. |
| `description` | String | Concise summary of plugin features and purpose. |
| `entry_point` | String | Main execution file. Defaults to `start.kt`, `start.java`, or `index.html`. |
| `tree` | Object | Map of relative file paths to their functional descriptions. |
| `permissions` | Array | List of Android permissions required by the plugin. |
| `manifest_specials`| Object | Android component declarations (`activity`, `intent_filter`, `services`, `receivers`, `providers`). |

---

## 💻 3. Writing Entry Points

### A. Kotlin Entry Point (`start.kt`)
Kotlin is the recommended language for native T-UI scripts. You can use dynamic system variables like `{{battery_level}}` and `{{battery_status}}`:

```kotlin
package ohi.andre.tui.plugin.sample

class StartPlugin {
    fun onExecute(args: Array<String>): String {
        val sb = StringBuilder()
        sb.append("=== [ TELEMETRY REPORT ] ===\n")
        sb.append("Device: {{device}} ({{os_version}})\n")
        sb.append("Battery: {{battery_level}}% [{{battery_status}}]\n")
        sb.append("CPU Load: {{cpu_usage}}% | RAM: {{ram_used}} / {{ram_total}}\n")
        sb.append("Network: {{network}} ({{network_name}})\n")
        
        if (args.isNotEmpty()) {
            sb.append("Arguments: ").append(args.joinToString(" ")).append("\n")
        }
        
        sb.append("Status: All systems nominal.\n")
        return sb.toString()
    }
}
```

### B. Java Entry Point (`start.java`)
Developers who prefer Java can implement `start.java`:

```java
package ohi.andre.tui.plugin.sample;

public class StartPlugin {
    public String onExecute(String[] args) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== [ JAVA PLUGIN ACTIVE ] ===\n");
        sb.append("Battery: {{battery_level}}% ({{battery_status}})\n");
        sb.append("Host: {{device}}\n");
        return sb.toString();
    }
}
```

### C. Web / HTML5 Entry Point (`index.html`)
Plugins with graphical dashboards can specify `index.html` as the entry point. The T-UI Plugin Host renders it in a hardware-accelerated WebView:

```html
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
        body { background: #0D1117; color: #00FF66; font-family: monospace; padding: 16px; }
        h1 { color: #00FFFF; font-size: 18px; border-bottom: 1px solid #30363D; padding-bottom: 8px; }
        .card { background: #161B22; border: 1px solid #30363D; padding: 12px; margin-top: 10px; border-radius: 6px; }
        .val { color: #FFD700; font-weight: bold; }
    </style>
</head>
<body>
    <h1>[ T-UI WEB DASHBOARD ]</h1>
    <div class="card">
        Battery: <span class="val">{{battery_level}}%</span> ({{battery_status}})<br>
        Device: <span class="val">{{device}}</span><br>
        Uptime: <span class="val">{{uptime}}s</span>
    </div>
</body>
</html>
```

---

## 🎨 4. Custom UI Layouts (`plugin_layout.xml`)

Plugins can provide custom Android XML layouts for HUD elements:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#0D1117"
    android:padding="16dp">

    <TextView
        android:id="@+id/plugin_title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="[ CUSTOM PLUGIN HUD ]"
        android:textColor="#00FF66"
        android:textSize="18sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/plugin_output"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:textColor="#00FFFF"
        android:textSize="14sp" />
</LinearLayout>
```

---

## 🛠️ 5. Plugin Studio (GUI Code Studio)

T-UI includes an integrated Code Studio for developing plugins directly on Android:

*   Launch with: `plugin -studio` (or `plugin -edit [plugin_id]`).
*   **Templates Tab**: Browse bundled templates and fetch online templates directly from `https://hunterzen-ru.github.io/TUI/Templates/info.json`. One-click scaffold generation!
*   **Code Editor Tab**: Dark Nordic theme monospace editor with syntax highlighting, line counter, file switcher, and instant save.
*   **Manifest Tab**: Visual form editor for configuring `manifest.json` properties, permissions, and specials.
*   **Files Tab**: Manage files and directories within your plugin folder.
*   **Export Action**: One-click "EXPORT ZIP" packages the plugin into `plugins/exports/<plugin_id>.zip` for easy sharing.

---

## ⌨️ 6. Terminal Command Reference (`plugin`)

| Command | Description | Example |
| :--- | :--- | :--- |
| `plugin -ls` | List all installed plugins | `plugin -ls` |
| `plugin -info [id]` | Inspect plugin metadata, permissions & tree | `plugin -info sample` |
| `plugin -launch [id] [args]` | Execute or launch a plugin | `plugin -launch sample --verbose` |
| `plugin -studio` | Launch the GUI Code Studio | `plugin -studio` |
| `plugin -edit [id]` | Open a specific plugin in Code Studio | `plugin -edit sample` |
| `plugin -create [id] [template]` | Create a new plugin from template (`kt`, `java`, `html`) | `plugin -create my_tool kt` |
| `plugin -templates` | Fetch and list online templates from GitHub | `plugin -templates` |
| `plugin -install [url\|zip]` | Install plugin from URL or local ZIP archive | `plugin -install https://example.com/tool.zip` |
| `plugin -export [id]` | Package plugin into a distribution ZIP | `plugin -export sample` |
| `plugin -rm [id]` | Uninstall a plugin | `plugin -rm my_tool` |
| `plugin -sample` | Verify or regenerate the bundled sample plugin | `plugin -sample` |
