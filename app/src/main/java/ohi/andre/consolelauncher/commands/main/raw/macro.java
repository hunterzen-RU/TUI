package ohi.andre.consolelauncher.commands.main.raw;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.managers.MacroEngine;
import ohi.andre.consolelauncher.tuils.Tuils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inbuilt T-UI Native Macro Command.
 * Record, create, manage, and execute multi-command automation workflows stored in macros.json.
 */
public class macro implements CommandAbstraction {

    private static final Pattern VAR_PATTERN = Pattern.compile("([a-zA-Z0-9_]+)=(?:\"([^\"]*)\"|'([^']*)'|(\\S+))");

    @Override
    public String exec(ExecutePack pack) throws Exception {
        MainPack info = (MainPack) pack;
        String rawArgs = info.getString();
        MacroEngine engine = MacroEngine.Companion.getInstance(info.context);

        if (rawArgs == null || rawArgs.trim().isEmpty()) {
            return formatHelp();
        }

        String trimmed = rawArgs.trim();

        // 1. Parameter switches
        if (trimmed.startsWith("-ls") || trimmed.equals("-list")) {
            List<MacroEngine.MacroDefinition> list = engine.getAllMacros();
            if (list.isEmpty()) {
                return "No macros configured. Use 'macro -rec [name]' or 'macro -add [name] [cmds]' to create one.";
            }
            StringBuilder sb = new StringBuilder("=== Inbuilt T-UI Macros (" + list.size() + ") ===\n");
            for (MacroEngine.MacroDefinition m : list) {
                sb.append("• ").append(m.getName())
                  .append(" (").append(m.getCommands().size()).append(" steps, ran ")
                  .append(m.getRunCount()).append("x): ")
                  .append(m.getDescription().isEmpty() ? "No description" : m.getDescription())
                  .append("\n");
            }
            sb.append("\nRun a macro with: macro [name] or macro -run [name]");
            return sb.toString();
        }

        if (trimmed.startsWith("-status")) {
            if (engine.isRecording()) {
                return "● RECORDING ACTIVE for macro '" + engine.getRecordingName() + "'\n" +
                       "Commands buffered: " + engine.getRecordedBuffer().size() + "\n" +
                       "Type 'macro -stop' to finish or 'macro -cancel' to discard.";
            } else {
                return "No macro recording in progress. Use 'macro -rec [name]' to start recording commands.";
            }
        }

        if (trimmed.startsWith("-rec ") || trimmed.startsWith("-record ")) {
            String rest = trimmed.substring(trimmed.indexOf(" ") + 1).trim();
            if (rest.isEmpty()) return "Usage: macro -rec [macro_name] [optional_description]";
            String[] parts = rest.split("\\s+", 2);
            String name = parts[0];
            String desc = parts.length > 1 ? parts[1] : "";
            boolean started = engine.startRecording(name, desc);
            if (started) {
                return "● Started recording macro: '" + name + "'.\n" +
                       "All subsequent terminal commands will be saved into this macro.\n" +
                       "When done, type: macro -stop";
            } else {
                return "Failed to start recording. Please check macro name.";
            }
        }

        if (trimmed.equals("-stop")) {
            MacroEngine.MacroDefinition saved = engine.stopRecording();
            if (saved != null) {
                return "✓ Recording finished! Macro '" + saved.getName() + "' saved with " +
                       saved.getCommands().size() + " commands into macros.json.\n" +
                       "Execute anytime using: macro " + saved.getName();
            } else {
                return "No macro was being recorded. Type 'macro -rec [name]' to record.";
            }
        }

        if (trimmed.equals("-cancel")) {
            boolean cancelled = engine.cancelRecording();
            if (cancelled) {
                return "Active macro recording was cancelled and discarded.";
            } else {
                return "No macro recording in progress.";
            }
        }

        if (trimmed.startsWith("-info ")) {
            String name = trimmed.substring(6).trim();
            MacroEngine.MacroDefinition m = engine.getMacro(name);
            if (m == null) return "Macro '" + name + "' not found. Use 'macro -ls' to view all.";
            StringBuilder sb = new StringBuilder();
            sb.append("Macro: ").append(m.getName()).append("\n");
            sb.append("Description: ").append(m.getDescription()).append("\n");
            sb.append("Author: ").append(m.getAuthor()).append("\n");
            sb.append("Run Count: ").append(m.getRunCount()).append(" times\n");
            sb.append("Steps (").append(m.getCommands().size()).append("):\n");
            for (int i = 0; i < m.getCommands().size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(m.getCommands().get(i)).append("\n");
            }
            return sb.toString().trim();
        }

        if (trimmed.startsWith("-rm ") || trimmed.startsWith("-del ")) {
            String name = trimmed.substring(trimmed.indexOf(" ") + 1).trim();
            boolean removed = engine.removeMacro(name);
            if (removed) {
                return "Macro '" + name + "' removed successfully.";
            } else {
                return "Macro '" + name + "' not found.";
            }
        }

        if (trimmed.equals("-file")) {
            File f = engine.getMacrosFile();
            return "Macros JSON file: " + f.getAbsolutePath() + " (" + (f.exists() ? f.length() + " bytes" : "not created") + ")";
        }

        if (trimmed.startsWith("-add ")) {
            String rest = trimmed.substring(5).trim();
            String[] parts = rest.split("\\s+", 2);
            if (parts.length < 2) {
                return "Usage: macro -add [name] \"command1; command2; command3\"";
            }
            String name = parts[0];
            String rawCmds = parts[1].trim();
            if (rawCmds.startsWith("\"") && rawCmds.endsWith("\"") && rawCmds.length() >= 2) {
                rawCmds = rawCmds.substring(1, rawCmds.length() - 1);
            }
            String[] cmdArray = rawCmds.split(";");
            List<String> commands = new ArrayList<>();
            for (String c : cmdArray) {
                if (!c.trim().isEmpty()) commands.add(c.trim());
            }
            MacroEngine.MacroDefinition def = new MacroEngine.MacroDefinition(
                name,
                "Custom macro created via CLI",
                commands,
                "user",
                true,
                System.currentTimeMillis(),
                0L,
                0
            );
            engine.addMacro(def);
            return "✓ Created macro '" + name + "' with " + commands.size() + " steps.";
        }

        // 2. Direct execution: `macro -run [name] [args]` or `macro [name] [args]`
        String execPayload = trimmed;
        if (execPayload.startsWith("-run ")) {
            execPayload = execPayload.substring(5).trim();
        }

        String[] tokens = execPayload.split("\\s+");
        String macroName = tokens[0];
        if (macroName.startsWith("\"") && macroName.endsWith("\"") && macroName.length() >= 2) {
            macroName = macroName.substring(1, macroName.length() - 1);
        }

        if (!engine.hasMacro(macroName)) {
            return "Macro '" + macroName + "' not found. Type 'macro -ls' to list available macros.";
        }

        // Parse positional and named arguments
        List<String> positional = new ArrayList<>();
        Map<String, Object> named = new HashMap<>();

        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i];
            Matcher m = VAR_PATTERN.matcher(token);
            if (m.matches()) {
                String k = m.group(1);
                String v = m.group(2) != null ? m.group(2) : (m.group(3) != null ? m.group(3) : m.group(4));
                named.put(k, v != null ? v : "");
            } else {
                positional.add(token);
            }
        }

        engine.executeMacro(macroName, positional, named, output -> {
            Tuils.sendOutput(info.context, output);
            return kotlin.Unit.INSTANCE;
        });

        return null;
    }

    private String formatHelp() {
        return "Inbuilt T-UI Native Macro Automation Engine.\n" +
               "Usage:\n" +
               "• macro -rec [name] [desc]  -> Start recording terminal commands\n" +
               "• macro -stop               -> Stop recording & save to macros.json\n" +
               "• macro -cancel             -> Discard active recording\n" +
               "• macro -status             -> Check recording status\n" +
               "• macro -ls                 -> List all saved macros\n" +
               "• macro -info [name]        -> Inspect macro commands\n" +
               "• macro -add [name] [cmds]  -> Add macro with semicolon-separated steps\n" +
               "• macro -rm [name]          -> Delete a macro\n" +
               "• macro -file               -> Display path to macros.json\n" +
               "• macro [name] [args...]    -> Execute a saved macro";
    }

    @Override
    public int[] argType() {
        return new int[]{CommandAbstraction.TEXTLIST};
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public int helpRes() {
        return R.string.help_macro;
    }

    @Override
    public String onNotArgEnough(ExecutePack info, int nArgs) {
        return formatHelp();
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int index) {
        return null;
    }
}
