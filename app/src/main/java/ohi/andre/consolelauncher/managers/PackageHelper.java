package ohi.andre.consolelauncher.managers;

import android.content.Context;
import android.graphics.Color;
import java.io.File;
import ohi.andre.consolelauncher.tuils.Tuils;

public class PackageHelper {

    private static final String[] COMMON_PATHS = {
        "/system/bin/apt",
        "/system/bin/pkg",
        "/usr/bin/apt",
        "/data/data/com.termux/files/usr/bin/apt",
        "/data/data/com.termux/files/usr/bin/pkg"
    };

    public static String findAvailableManager() {
        for (String path : COMMON_PATHS) {
            File f = new File(path);
            if (f.exists() && f.canExecute()) {
                return path;
            }
        }
        return null;
    }

    public static boolean isRootRequired(String path) {
        File f = new File(path);
        // If file exists but is not executable by our UID, it likely needs root
        return f.exists() && !f.canExecute();
    }

    public static String executeBridged(Context context, String command) {
        String managerPath = findAvailableManager();

        if (managerPath == null) {
            // Fallback: If no binary found, check if we can call it via Termux login wrapper anyway
            if (command.startsWith("apt") || command.startsWith("pkg")) {
                return executeViaTermuxWrapper(context, command);
            }
            return "Error: No native package manager binary found.";
        }

        if (isRootRequired(managerPath)) {
            // Check if it's a Termux path, if so, use the wrapper
            if (managerPath.contains("com.termux")) {
                return executeViaTermuxWrapper(context, command);
            }
            return "Error: " + managerPath + " exists but is inaccessible. Please run this command with root privileges (su -c " + command + ").";
        }

        // Bridge to the existing TermuxSessionManager for execution
        TermuxSessionManager sessionMgr = new TermuxSessionManager(context);
        String fullCmd = managerPath + " " + command;
        
        Tuils.sendOutput(context, "Executing: " + fullCmd);
        return sessionMgr.executeDirectShellCommand(fullCmd, 60);
    }

    public static String executeViaTermuxWrapper(Context context, String command) {
        String termuxLogin = "/data/data/com.termux/files/usr/bin/login";
        String termuxBinBase = "/data/data/com.termux/files/usr/bin/";
        
        String binary = command.split(" ")[0];
        String args = command.substring(binary.length()).trim();
        
        // Wrapper: login -d /path/to/binary args
        String wrapperCmd = termuxLogin + " -d " + termuxBinBase + binary + " " + args;
        
        Tuils.sendOutput(context, "Using Termux Wrapper: " + wrapperCmd);
        TermuxSessionManager sessionMgr = new TermuxSessionManager(context);
        return sessionMgr.executeDirectShellCommand(wrapperCmd, 300); // Package installs take time
    }

    public static String tuiRun(Context context, String command) {
        String termuxLogin = "/data/data/com.termux/files/usr/bin/login";
        String termuxBinBase = "/data/data/com.termux/files/usr/bin/";
        
        // Use login -d to run ANY command in termux environment
        String wrapperCmd = termuxLogin + " -d " + termuxBinBase + command;
        
        Tuils.sendOutput(context, "tui_run: " + wrapperCmd);
        TermuxSessionManager sessionMgr = new TermuxSessionManager(context);
        return sessionMgr.executeDirectShellCommand(wrapperCmd, 300);
    }
}
