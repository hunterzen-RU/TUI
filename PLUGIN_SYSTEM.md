# T-UI Plugin System Documentation

T-UI now supports external Java/Kotlin plugins that can register new commands directly into the launcher's engine.

## 📦 How it Works
T-UI scans the `plugins/` directory in its internal storage for `.jar` or `.dex` files. It attempts to load an entry point class named `ohi.andre.tui.plugin.PluginEntry` that implements the `TuiCommandPlugin` interface.

## 🛠 Developing a Plugin

### 1. Requirements
*   Android SDK (Compile with the same or lower `minSdkVersion` as T-UI).
*   Add T-UI's `CommandAbstraction` and `TuiCommandPlugin` interfaces to your classpath.

### 2. Implement the Interface
Your plugin entry class must be `ohi.andre.tui.plugin.PluginEntry`.

```java
package ohi.andre.tui.plugin;

import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.tuils.interfaces.TuiCommandPlugin;

public class PluginEntry implements TuiCommandPlugin {

    @Override
    public void onLoaded() {
        // Initialization logic
    }

    @Override
    public String getPluginName() {
        return "myplugin"; // This will be your command name
    }

    @Override
    public String exec(ExecutePack pack) throws Exception {
        return "Hello from my custom Java plugin!";
    }

    @Override
    public int helpRes() { return 0; } // Optional: resource ID for help

    @Override
    public int[] argType() { return new int[0]; }

    @Override
    public int priority() { return 5; }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) { return null; }

    @Override
    public String onArgNotFound(ExecutePack pack, int index) { return null; }
}
```

### 3. Build & Deploy
1.  Compile your code into a `.class` file.
2.  Convert the `.class` (and any dependencies) into a Dalvik Executable (`.dex`) file using the `d8` or `dx` tool.
3.  Rename the result to `my_plugin.dex` or wrap it in a `.jar`.
4.  Move the file to the T-UI internal `plugins/` folder:
    `$ variables -file` (to find the folder path, usually `Android/data/.../files/t-ui/plugins/`)
5.  Type `restart` in T-UI.

## 📊 Configurable Dashboard Actions
You can configure a long-press action for the dashboard grid in `dashboard.xml`.

*   **ai_chat**: Launches a native AI Chat activity with persistent history.
*   **app_launcher**: Launches a simple grid-based icon launcher for all apps.
*   **none**: Disables the long-press action.

Example `dashboard.xml` entry:
```xml
<entry name="long_press_action" value="ai_chat" />
```
