package ohi.andre.consolelauncher.managers;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import ohi.andre.consolelauncher.tuils.Tuils;

public class PackageHelper {

    public static void ensureBootstrapEnvironment(Context context) {
        try {
            TermuxSessionManager sessionMgr = new TermuxSessionManager(context);
            TermuxSessionManager.SessionConfig config = sessionMgr.createSessionConfig();

            File prefixBin = new File(config.getPrefixPath(), "bin");
            if (!prefixBin.exists()) prefixBin.mkdirs();

            File homeBin = new File(config.getHomePath(), "tui/bin");
            if (!homeBin.exists()) homeBin.mkdirs();

            File etcDir = new File(config.getPrefixPath(), "etc/apt");
            if (!etcDir.exists()) etcDir.mkdirs();

            File sourcesList = new File(etcDir, "sources.list");
            if (!sourcesList.exists()) {
                FileOutputStream fos = new FileOutputStream(sourcesList);
                fos.write("# T-UI Standalone Repository\ndeb https://raw.githubusercontent.com/hunterzen-RU/hunterzen-RU.github.io/main/TUI/conf/ packages/\n".getBytes());
                fos.close();
            }

            // Create executable wrapper scripts for pkg and apt in $PREFIX/bin so system shell has binaries
            File pkgScript = new File(prefixBin, "pkg");
            if (!pkgScript.exists()) {
                FileOutputStream fos = new FileOutputStream(pkgScript);
                fos.write("#!/system/bin/sh\necho \"T-UI Standalone Package Manager: Use 'tui -install <package>' or 'pkg install <package>' in T-UI.\"\n".getBytes());
                fos.close();
                pkgScript.setExecutable(true, false);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    try { android.system.Os.chmod(pkgScript.getAbsolutePath(), 0755); } catch (Exception ignored) {}
                }
            }

            File aptScript = new File(prefixBin, "apt");
            if (!aptScript.exists()) {
                FileOutputStream fos = new FileOutputStream(aptScript);
                fos.write("#!/system/bin/sh\necho \"T-UI Standalone Package Manager: Use 'tui -install <package>' or 'apt install <package>' in T-UI.\"\n".getBytes());
                fos.close();
                aptScript.setExecutable(true, false);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    try { android.system.Os.chmod(aptScript.getAbsolutePath(), 0755); } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    public static String findAvailableManager(Context context) {
        ensureBootstrapEnvironment(context);
        TermuxSessionManager sessionMgr = new TermuxSessionManager(context);
        TermuxSessionManager.SessionConfig config = sessionMgr.createSessionConfig();

        String[] paths = {
            config.getPrefixPath() + "/bin/pkg",
            config.getPrefixPath() + "/bin/apt",
            config.getPrefixPath() + "/bin/apt-get",
            "/data/data/com.termux/files/usr/bin/pkg",
            "/data/data/com.termux/files/usr/bin/apt",
            "/data/data/com.termux/files/usr/bin/apt-get",
            "/system/bin/apt",
            "/system/bin/pkg"
        };

        for (String path : paths) {
            File f = new File(path);
            if (f.exists() && f.canExecute()) {
                return path;
            }
        }
        return null;
    }

    public static boolean isRootRequired(String path) {
        File f = new File(path);
        return f.exists() && !f.canExecute();
    }

    public static String executeBridged(Context context, String command) {
        File termuxPkg = new File("/data/data/com.termux/files/usr/bin/pkg");
        File termuxApt = new File("/data/data/com.termux/files/usr/bin/apt");

        if (termuxPkg.exists() || termuxApt.exists()) {
            return executeViaTermuxWrapper(context, command);
        }

        ensureBootstrapEnvironment(context);

        // Standalone Mode (Termux NOT installed)
        String[] parts = command.trim().split("\\s+");
        String pkgName = "";
        for (int i = 0; i < parts.length; i++) {
            if ("install".equalsIgnoreCase(parts[i]) && i + 1 < parts.length) {
                pkgName = parts[i + 1];
                if (pkgName.startsWith("-") && i + 2 < parts.length) {
                    pkgName = parts[i + 2];
                }
                break;
            }
        }

        if (!pkgName.isEmpty() && !pkgName.startsWith("-")) {
            final String targetPkg = pkgName;
            PackageManager.installPackage(context, targetPkg, null);
            return "Initiated standalone T-UI package installation for '" + targetPkg + "'...";
        }

        return "T-UI Standalone Package Manager. Use 'tui -install <package>' or 'pkg install <package>'.";
    }

    public static String executeViaTermuxWrapper(Context context, String command) {
        String termuxLogin = "/data/data/com.termux/files/usr/bin/login";
        String termuxBinBase = "/data/data/com.termux/files/usr/bin/";

        File loginFile = new File(termuxLogin);
        if (!loginFile.exists()) {
            return executeBridged(context, command);
        }

        String binary = command.split("\\s+")[0];
        String args = command.length() > binary.length() ? command.substring(binary.length()).trim() : "";

        String wrapperCmd = termuxLogin + " -d " + termuxBinBase + binary + " " + args;

        Tuils.sendOutput(context, "Executing via Termux Wrapper: " + wrapperCmd);
        TermuxSessionManager sessionMgr = new TermuxSessionManager(context);
        return sessionMgr.executeDirectShellCommand(wrapperCmd, 300);
    }

    public static String tuiRun(Context context, String command) {
        TermuxSessionManager sessionMgr = new TermuxSessionManager(context);
        return sessionMgr.executeDirectShellCommand(command, 300);
    }
}
