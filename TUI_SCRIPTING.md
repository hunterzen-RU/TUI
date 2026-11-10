# T-UI Scripting Guide (.tui)

T-UI now supports a custom automation engine for executing sequences of commands, managing variables, and interacting with the Android system via `.tui` files.

## 📄 File Anatomy

A `.tui` script is a plain text file containing a sequence of instructions. It is recommended to start the file with the T-UI shebang for identification.

```tui
#!/bin/sh/tui

# This is a comment
// This is also a comment

set name="User"
echo "Hello, {{name}}!"
```

## 🛠 Syntax & Instructions

### 1. Shebang
Use `#!/bin/sh/tui` on the first line to identify the file as a T-UI script.

### 2. Comments
Lines starting with `#` or `//` are ignored by the engine.

### 3. Variable Management
Variables can be assigned and reused throughout the script.
*   **Assignment**: `set [var]=[value]` or simply `[var]=[value]`.
*   **Resolution**: Use `{{var}}` or `$var` to inject the variable value into other commands.

```tui
set target="world"
echo "Hello {{target}}"
```

### 4. Command Injection
You can execute standard T-UI launcher commands (like `apps`, `theme`, `wifi`) by wrapping them in double braces.
*   **Syntax**: `{{command [args]}};`

```tui
# Toggle wifi and open notes
{{wifi}};
{{notes -ls}};
```

### 5. Built-in Engine Commands
The engine provides several native instructions for script control:

*   **`echo [text]`**: Print text to the terminal output.
*   **`sleep [ms]`** / **`delay [ms]`**: Pause execution for the specified milliseconds.
*   **`action [intent_action]`**: Trigger an Android system intent (e.g., `android.intent.action.VIEW`).
*   **`sh [command]`** / **`shell [command]`**: Execute a direct shell command in the Termux/System environment.

### 6. Dynamic System Variables
T-UI provides several "magic" variables that resolve at runtime:
*   `{{date}}`: Current date (YYYY-MM-DD).
*   `{{time}}`: Current time (HH:mm:ss).
*   `{{battery}}`: Current battery percentage.
*   `{{uptime}}`: System uptime in seconds.

## 🚀 Execution

### Using the `run` command
Execute any `.tui` file from the main terminal:
```bash
run my_automation.tui
```

### Using the Editor (`tedit`)
Open the specialized script editor to write, dry-run, and execute scripts:
```bash
tedit my_script.tui
```
*   **SAVE**: Writes changes to disk.
*   **DRY**: Performs a syntax check (validates shebang, unclosed braces).
*   **RUN**: Executes the script immediately and closes the editor.

## 💡 Example: System Cleanup & Info
```tui
#!/bin/sh/tui

echo "Starting System Report..."
echo "Date: {{date}} | Time: {{time}}"
echo "Battery: {{battery}}%"

# Pause for 1 second
sleep 1000

echo "Checking storage..."
sh df -h /data

echo "Cleaning terminal..."
{{clear}};

echo "Report Complete."
```
