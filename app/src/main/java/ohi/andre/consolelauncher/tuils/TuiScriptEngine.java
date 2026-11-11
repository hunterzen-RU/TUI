package ohi.andre.consolelauncher.tuils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ohi.andre.consolelauncher.MainManager;
import ohi.andre.consolelauncher.managers.TermuxSessionManager;
import ohi.andre.consolelauncher.managers.VariablesManager;

public class TuiScriptEngine {

    public static final String SHEBANG = "#!/bin/sh/tui";

    public static void executeScriptFile(final Context context, final File file) {
        if (file == null || !file.exists() || !file.canRead()) {
            Tuils.sendOutput(Color.RED, context, "Cannot read .tui script file: " + (file != null ? file.getAbsolutePath() : "null"));
            return;
        }

        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        } catch (Exception e) {
            Tuils.sendOutput(Color.RED, context, "Error reading .tui script: " + e.getMessage());
            return;
        }

        executeScriptLines(context, file.getName(), lines);
    }

    public static void executeScriptInputStream(final Context context, final String scriptName, final InputStream inputStream) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        } catch (Exception e) {
            Tuils.sendOutput(Color.RED, context, "Error reading .tui stream: " + e.getMessage());
            return;
        }

        executeScriptLines(context, scriptName, lines);
    }

    public static void executeScriptLines(final Context context, final String scriptName, final List<String> lines) {
        new Thread(() -> {
            Tuils.sendOutput(context, "[T-UI Engine] Executing: " + scriptName);
            Map<String, String> localVars = new HashMap<>();
            TermuxSessionManager shellManager = new TermuxSessionManager(context);

            for (int i = 0; i < lines.size(); i++) {
                String rawLine = lines.get(i);
                if (rawLine == null) continue;
                String line = rawLine.trim();

                // Skip Shebang header & empty/comment lines
                if (line.isEmpty() || line.startsWith("#!") || line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }

                // Expand variable placeholders {{var}} or $var
                line = VariablesManager.resolveVariables(context, line, localVars);

                String lower = line.toLowerCase();

                // 1. App Custom Executable Commands injected as {{command}};
                if (line.startsWith("{{") && (line.endsWith("}};") || line.endsWith("}}"))) {
                    int endIdx = line.endsWith("}};") ? line.length() - 3 : line.length() - 2;
                    String injectedCmd = line.substring(2, endIdx).trim();

                    // If injected command is shell command
                    if (injectedCmd.toLowerCase().startsWith("shell ") || injectedCmd.toLowerCase().startsWith("sh ")) {
                        String shCmd = injectedCmd.substring(injectedCmd.indexOf(' ') + 1).trim();
                        String res = shellManager.executeDirectShellCommand(shCmd, 15);
                        if (res != null && !res.trim().isEmpty()) {
                            Tuils.sendOutput(context, res.trim());
                        }
                    } else {
                        // Broadcast T-UI custom app command
                        try {
                            Intent cmdIntent = new Intent(MainManager.ACTION_EXEC);
                            cmdIntent.putExtra(MainManager.CMD_COUNT, MainManager.commandCount);
                            cmdIntent.putExtra(MainManager.CMD, injectedCmd);
                            LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(cmdIntent);
                        } catch (Exception e) {
                            Tuils.log(e);
                        }
                    }
                    try { Thread.sleep(150); } catch (Exception ignored) {}
                    continue;
                }

                // 2. Variable initializations (e.g. set var=val OR var=val)
                if ((lower.startsWith("set ") || line.contains("=")) && !line.startsWith("{{") && !lower.startsWith("echo ") && !lower.startsWith("sleep ") && !lower.startsWith("delay ")) {
                    String assignLine = lower.startsWith("set ") ? line.substring(4).trim() : line;
                    int eqIdx = assignLine.indexOf('=');
                    if (eqIdx > 0) {
                        String varName = assignLine.substring(0, eqIdx).trim();
                        String varVal = assignLine.substring(eqIdx + 1).trim();
                        if (varVal.startsWith("\"") && varVal.endsWith("\"") && varVal.length() >= 2) {
                            varVal = varVal.substring(1, varVal.length() - 1);
                        }

                        localVars.put(varName, varVal);
                        VariablesManager.setVariable(varName, varVal);
                        continue;
                    }
                }

                // 3. Sleep / Delay commands
                if (lower.startsWith("sleep ") || lower.startsWith("delay ")) {
                    String delayStr = line.substring(line.indexOf(' ') + 1).trim();
                    try {
                        long ms = Long.parseLong(delayStr);
                        Thread.sleep(Math.max(10, ms));
                    } catch (Exception ignored) {}
                    continue;
                }

                // 4. Echo commands
                if (lower.startsWith("echo ")) {
                    String msg = line.substring(5).trim();
                    Tuils.sendOutput(context, msg);
                    continue;
                }

                // 5. Action trigger
                if (lower.startsWith("action ")) {
                    String actionStr = line.substring(7).trim();
                    try {
                        Intent actionIntent = new Intent(actionStr);
                        actionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(actionIntent);
                    } catch (Exception e) {
                        try {
                            Intent bIntent = new Intent(actionStr);
                            context.sendBroadcast(bIntent);
                        } catch (Exception ex) {
                            Tuils.sendOutput(Color.RED, context, "Failed action: " + actionStr);
                        }
                    }
                    continue;
                }

                // 6. Default: Shell script command execution (e.g. ls -a, dumpsys battery, etc.)
                try {
                    String res = shellManager.executeDirectShellCommand(line, 15);
                    if (res != null && !res.trim().isEmpty()) {
                        Tuils.sendOutput(context, res.trim());
                    }
                } catch (Exception e) {
                    Tuils.sendOutput(Color.RED, context, "Execution error: " + e.getMessage());
                }

                try { Thread.sleep(150); } catch (Exception ignored) {}
            }
            Tuils.sendOutput(context, "[T-UI Engine] Completed script: " + scriptName);
        }).start();
    }
    public static List<String> validateSyntax(List<String> lines) {
        List<String> issues = new ArrayList<>();
        boolean hasShebang = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                if (i == 0 && line.startsWith(SHEBANG)) hasShebang = true;
                continue;
            }
            if (line.startsWith("{{") && !line.contains("}}")) {
                issues.add("Line " + (i + 1) + ": Unclosed custom command placeholder '{{'");
            }
            if (line.contains("=") && !line.startsWith("set ") && !line.contains("{{")) {
                // simple assignment check
            }
        }
        if (!hasShebang) {
            issues.add("Warning: Missing recommended TUI shebang '" + SHEBANG + "' at line 1.");
        }
        return issues;
    }
}
