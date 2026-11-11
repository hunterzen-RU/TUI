package ohi.andre.consolelauncher.managers;

import android.content.Context;
import android.os.Build;
import android.system.Os;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.tukaani.xz.XZInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

import ohi.andre.consolelauncher.tuils.ProgressBarUtils;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * Manages Linux container distributions running on top of PRoot (Alpine, Debian, Ubuntu).
 * Provides automated rootfs download, unpacking, DNS configuration, and container execution.
 */
public class ProotDistroManager {

    public interface OutputCallback {
        void onOutput(String text);
    }

    private static volatile String sActiveDistro = null;

    public static boolean hasActiveDistro() {
        return sActiveDistro != null;
    }

    public static String getActiveDistro() {
        return sActiveDistro;
    }

    public static void setActiveDistro(String distro) {
        sActiveDistro = distro;
    }

    public static void runInActiveDistro(final Context context, final String command, final OutputCallback callback) {
        final String distro = sActiveDistro;
        if (distro == null) return;
        new Thread(() -> {
            try {
                TermuxSessionManager sessionMgr = new TermuxSessionManager(context);
                TermuxSessionManager.SessionConfig config = sessionMgr.createSessionConfig();
                File distroBaseDir = new File(config.getPrefixPath(), "var/lib/proot-distro/installed-rootfs");
                executeSingleDistroCommand(sessionMgr, config, distro, distroBaseDir, command, callback);
            } catch (Exception e) {
                Tuils.log(e);
                callback.onOutput("Error running in container: " + e.getLocalizedMessage());
            }
        }).start();
    }

    public static boolean isDistroCommand(String input) {
        if (input == null) return false;
        String trimmed = input.trim();
        if (trimmed.equals("proot") || trimmed.equals("proot-distro")) return true;
        return trimmed.startsWith("proot install") || trimmed.startsWith("proot-distro install") ||
                trimmed.startsWith("proot login") || trimmed.startsWith("proot-distro login") ||
                trimmed.startsWith("proot run") || trimmed.startsWith("proot-distro run") ||
                trimmed.startsWith("proot list") || trimmed.startsWith("proot-distro list") ||
                trimmed.startsWith("proot remove") || trimmed.startsWith("proot-distro remove") ||
                trimmed.startsWith("proot reset") || trimmed.startsWith("proot-distro reset") ||
                trimmed.equals("proot logout") || trimmed.equals("proot-distro logout");
    }

    public static String normalizeDistroName(String distro) {
        if (distro == null) return "alpine";
        String lower = distro.trim().toLowerCase();
        if (lower.equals("alpine") || lower.equals("apline") || lower.equals("alpin") || lower.equals("alpne")) {
            return "alpine";
        }
        if (lower.equals("debian") || lower.equals("debain") || lower.equals("deb") || lower.equals("debi")) {
            return "debian";
        }
        if (lower.equals("ubuntu") || lower.equals("ubunutu") || lower.equals("ubunt") || lower.equals("ubantu")) {
            return "ubuntu";
        }
        return lower;
    }

    public static void executeDistroCommand(final Context context, final String input, final OutputCallback callback) {
        new Thread(() -> {
            try {
                TermuxSessionManager sessionMgr = new TermuxSessionManager(context);
                TermuxSessionManager.SessionConfig config = sessionMgr.createSessionConfig();
                File prefixDir = new File(config.getPrefixPath());
                File homeDir = new File(config.getHomePath());
                File distroBaseDir = new File(prefixDir, "var/lib/proot-distro/installed-rootfs");
                if (!distroBaseDir.exists()) distroBaseDir.mkdirs();

                File tmpDir = new File(prefixDir, "tmp");
                if (!tmpDir.exists()) tmpDir.mkdirs();

                // Ensure PRoot and dependencies are in place
                File prootBin = new File(prefixDir, "bin/proot");
                String trimmed = input.trim();
                String[] parts = trimmed.split("\\s+");
                String action = parts.length > 1 ? parts[1].toLowerCase() : "";
                String targetDistro = "alpine";
                if (parts.length > 2) {
                    targetDistro = normalizeDistroName(parts[2]);
                } else if (parts.length > 1 && !action.equals("install") && !action.equals("login") && !action.equals("run") && !action.equals("remove") && !action.equals("reset") && !action.equals("list")) {
                    targetDistro = normalizeDistroName(parts[1]);
                    action = "login";
                }
                final String finalDistro = targetDistro;

                if (!prootBin.exists()) {
                    callback.onOutput("PRoot binary is not yet installed. Installing proot first...");
                    PackageManager.installPackage(context, "proot", new PackageManager.InstallCallback() {
                        @Override
                        public void onSuccess(String message) {
                            callback.onOutput(message);
                            callback.onOutput("✓ PRoot installed. Now run 'proot install " + finalDistro + "'.");
                        }

                        @Override
                        public void onError(String error) {
                            callback.onOutput("Failed to install proot: " + error);
                        }
                    });
                    return;
                }
                PackageManager.ensurePackageDependencies(context, "proot");

                if (parts.length == 1) {
                    // Bare proot or proot-distro call
                    showHelp(callback, distroBaseDir);
                    return;
                }

                switch (action) {
                    case "install":
                        installDistro(context, targetDistro, prefixDir, homeDir, distroBaseDir, callback);
                        break;
                    case "login":
                        loginDistro(sessionMgr, config, targetDistro, distroBaseDir, parts, callback);
                        break;
                    case "run":
                        runInDistro(sessionMgr, config, targetDistro, distroBaseDir, parts, callback);
                        break;
                    case "logout":
                        if (sActiveDistro != null) {
                            String prev = sActiveDistro;
                            sActiveDistro = null;
                            callback.onOutput("Logged out of '" + prev + "' container. Returned to host shell.");
                        } else {
                            callback.onOutput("No active container session.");
                        }
                        break;
                    case "list":
                        listDistros(distroBaseDir, callback);
                        break;
                    case "remove":
                    case "reset":
                        removeDistro(targetDistro, distroBaseDir, callback);
                        break;
                    default:
                        showHelp(callback, distroBaseDir);
                        break;
                }
            } catch (Exception e) {
                Tuils.log(e);
                callback.onOutput("Error executing PRoot distribution command: " + e.getLocalizedMessage());
            }
        }).start();
    }

    private static void showHelp(OutputCallback callback, File distroBaseDir) {
        StringBuilder sb = new StringBuilder();
        sb.append("T-UI PRoot Linux Container Subsystem\n");
        sb.append("Usage:\n");
        sb.append("  proot install <distro>    Install a Linux distribution (e.g. 'proot install alpine')\n");
        sb.append("  proot login [distro]      Log in to an installed distribution (e.g. 'proot login alpine')\n");
        sb.append("  proot run <distro> <cmd>  Run a command inside the distribution\n");
        sb.append("  proot list                List supported and installed distributions\n");
        sb.append("  proot remove <distro>     Remove an installed distribution\n\n");
        sb.append("Supported distributions:\n");
        sb.append("  • alpine   (Alpine Linux 3.20 - minirootfs ~3.9 MB) [Recommended]\n");
        sb.append("  • debian   (Debian 12 Bookworm ~65 MB)\n");
        sb.append("  • ubuntu   (Ubuntu 24.04 Noble ~75 MB)\n\n");

        File alpineDir = new File(distroBaseDir, "alpine");
        if (alpineDir.exists() && alpineDir.isDirectory()) {
            sb.append("✓ Alpine Linux is currently installed. Run 'proot login alpine' to start.\n");
        }
        callback.onOutput(sb.toString().trim());
    }

    private static void listDistros(File distroBaseDir, OutputCallback callback) {
        StringBuilder sb = new StringBuilder();
        sb.append("PRoot Linux Distributions:\n");
        String[] distros = new String[]{"alpine", "debian", "ubuntu"};
        for (String d : distros) {
            File rootfs = new File(distroBaseDir, d);
            boolean installed = rootfs.exists() && rootfs.isDirectory() && new File(rootfs, "bin").exists();
            sb.append("  • ").append(d);
            if (installed) {
                sb.append(" [Installed - Ready to use with 'proot login ").append(d).append("']");
            } else {
                sb.append(" [Not installed - Install with 'proot install ").append(d).append("']");
            }
            sb.append("\n");
        }
        callback.onOutput(sb.toString().trim());
    }

    private static void removeDistro(String distro, File distroBaseDir, OutputCallback callback) {
        File rootfs = new File(distroBaseDir, distro);
        if (!rootfs.exists()) {
            callback.onOutput("Distribution '" + distro + "' is not installed.");
            return;
        }
        callback.onOutput("Removing distribution '" + distro + "'...");
        deleteRecursively(rootfs);
        callback.onOutput("✓ Distribution '" + distro + "' has been removed.");
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        file.delete();
    }

    private static void installDistro(final Context context, final String distro,
                                      final File prefixDir, final File homeDir,
                                      final File distroBaseDir, final OutputCallback callback) {
        File rootfsDir = new File(distroBaseDir, distro);
        if (rootfsDir.exists() && new File(rootfsDir, "bin/sh").exists()) {
            callback.onOutput("✓ Distribution '" + distro + "' is already installed.\n" +
                    "To enter the container, run: proot login " + distro);
            return;
        }

        String arch = AptPackageResolver.getTermuxArch();
        List<String> urls = getDistroDownloadUrls(distro, arch);
        if (urls.isEmpty()) {
            callback.onOutput("Unsupported distribution: '" + distro + "'. Supported: alpine, debian, ubuntu");
            return;
        }

        callback.onOutput("Preparing installation of " + distro + " Linux (" + arch + ")...");

        File cacheDir = context.getCacheDir();
        String ext = urls.get(0).endsWith(".tar.xz") ? ".tar.xz" : ".tar.gz";
        File destArchive = new File(cacheDir, distro + "-rootfs-" + arch + ext);

        boolean downloaded = downloadWithProgress(context, distro, urls, destArchive, callback);
        if (!downloaded || !destArchive.exists() || destArchive.length() < 1000) {
            callback.onOutput("Failed downloading " + distro + " rootfs archive from official mirrors.");
            return;
        }

        callback.onOutput("Extracting " + distro + " root filesystem...");
        if (!rootfsDir.exists()) rootfsDir.mkdirs();

        try {
            extractRootfsArchive(destArchive, rootfsDir, callback);
            destArchive.delete();

            // Post-extract configuration
            configureRootfs(rootfsDir, prefixDir);

            callback.onOutput("✓ Successfully installed " + distro + " Linux!\n" +
                    "To enter your container, run:\n" +
                    "  proot login " + distro);

        } catch (Exception e) {
            Tuils.log(e);
            callback.onOutput("Extraction failed for " + distro + ": " + e.getLocalizedMessage());
        }
    }

    private static List<String> getDistroDownloadUrls(String rawDistro, String arch) {
        String distro = normalizeDistroName(rawDistro);
        List<String> list = new ArrayList<>();
        if ("alpine".equalsIgnoreCase(distro)) {
            String alpineArch = arch;
            if ("arm".equalsIgnoreCase(arch)) alpineArch = "armv7";
            else if ("i686".equalsIgnoreCase(arch)) alpineArch = "x86";

            list.add("https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/" + alpineArch + "/alpine-minirootfs-3.20.10-" + alpineArch + ".tar.gz");
            list.add("https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/" + alpineArch + "/alpine-minirootfs-3.20.10-" + alpineArch + ".tar.gz");
            list.add("https://github.com/termux/proot-distro/releases/download/v4.30.1/alpine-" + arch + "-pd-v4.30.1.tar.xz");
            list.add("https://github.com/termux/proot-distro/releases/download/v4.29.0/alpine-" + arch + "-pd-v4.29.0.tar.xz");
        } else if ("debian".equalsIgnoreCase(distro)) {
            list.add("https://github.com/termux/proot-distro/releases/download/v4.29.0/debian-trixie-" + arch + "-pd-v4.29.0.tar.xz");
            list.add("https://github.com/termux/proot-distro/releases/download/v4.29.0/debian-bookworm-" + arch + "-pd-v4.29.0.tar.xz");
        } else if ("ubuntu".equalsIgnoreCase(distro)) {
            list.add("https://github.com/termux/proot-distro/releases/download/v4.30.1/ubuntu-questing-" + arch + "-pd-v4.30.1.tar.xz");
            list.add("https://github.com/termux/proot-distro/releases/download/v4.29.0/ubuntu-plucky-" + arch + "-pd-v4.29.0.tar.xz");
        }
        return list;
    }

    private static boolean downloadWithProgress(Context context, String distro, List<String> urls, File destFile, OutputCallback callback) {
        HttpURLConnection conn = null;
        String activeUrl = null;

        for (String urlStr : urls) {
            try {
                String curr = urlStr;
                HttpURLConnection testConn = null;
                int redirects = 0;
                while (redirects < 5) {
                    URL u = new URL(curr);
                    testConn = (HttpURLConnection) u.openConnection();
                    testConn.setInstanceFollowRedirects(true);
                    testConn.setConnectTimeout(15000);
                    testConn.setReadTimeout(30000);
                    testConn.setRequestProperty("User-Agent", "T-UI-Distro-Installer/1.0");
                    testConn.connect();
                    int code = testConn.getResponseCode();
                    if (code >= 300 && code <= 308) {
                        String loc = testConn.getHeaderField("Location");
                        testConn.disconnect();
                        if (loc == null) break;
                        curr = loc;
                        redirects++;
                    } else if (code == 200) {
                        conn = testConn;
                        activeUrl = curr;
                        break;
                    } else {
                        testConn.disconnect();
                        break;
                    }
                }
                if (conn != null) break;
            } catch (Exception ignored) {}
        }

        if (conn == null) return false;

        callback.onOutput("Downloading from " + activeUrl);

        try {
            int fileLength = conn.getContentLength();
            InputStream input = conn.getInputStream();
            FileOutputStream output = new FileOutputStream(destFile);

            byte[] data = new byte[8192];
            long total = 0;
            int count;
            int lastProgress = -1;

            long startTime = System.currentTimeMillis();
            long lastTime = startTime;
            long bytesSinceLastTime = 0;
            double currentSpeed = 0.0;

            while ((count = input.read(data)) != -1) {
                total += count;
                bytesSinceLastTime += count;
                long now = System.currentTimeMillis();
                long timeDiff = now - lastTime;

                if (timeDiff >= 400) {
                    currentSpeed = (bytesSinceLastTime * 1000.0) / timeDiff;
                    lastTime = now;
                    bytesSinceLastTime = 0;
                }

                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    if (progress >= lastProgress + 10 || progress == 100) {
                        String display = ProgressBarUtils.buildProgressDisplay(distro + "-rootfs", 1, 1, progress, currentSpeed);
                        callback.onOutput(display);
                        lastProgress = progress;
                    }
                }
                output.write(data, 0, count);
            }

            output.flush();
            output.close();
            input.close();
            conn.disconnect();
            return true;
        } catch (Exception e) {
            Tuils.log(e);
            return false;
        }
    }

    private static void extractRootfsArchive(File archiveFile, File rootfsDir, OutputCallback callback) throws Exception {
        InputStream raw = new FileInputStream(archiveFile);
        InputStream decomp;
        if (archiveFile.getName().endsWith(".xz")) {
            decomp = new XZInputStream(raw);
        } else if (archiveFile.getName().endsWith(".gz") || archiveFile.getName().endsWith(".tgz")) {
            decomp = new GZIPInputStream(raw);
        } else {
            decomp = raw;
        }

        TarArchiveInputStream tar = new TarArchiveInputStream(decomp);
        TarArchiveEntry entry;
        byte[] buf = new byte[8192];
        List<AptPackageResolver.AptEntry> symlinks = new ArrayList<>();

        while ((entry = tar.getNextEntry()) != null) {
            String name = entry.getName();
            if (name.startsWith("./")) name = name.substring(2);
            if (name.isEmpty() || name.equals(".")) continue;

            File target = new File(rootfsDir, name);

            if (entry.isDirectory()) {
                if (!target.exists()) target.mkdirs();
            } else if (entry.isSymbolicLink()) {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                
                // File.exists() returns false for dangling symlinks.
                // Unconditionally delete and remove target path before creating symlink.
                target.delete();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        Os.remove(target.getAbsolutePath());
                    } catch (Exception ignored) {}
                }

                String linkTarget = entry.getLinkName();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        Os.symlink(linkTarget, target.getAbsolutePath());
                    } catch (Exception symlinkEx) {
                        try {
                            FileOutputStream symOut = new FileOutputStream(target);
                            symOut.write(linkTarget.getBytes());
                            symOut.flush();
                            symOut.close();
                        } catch (Exception ignored) {}
                    }
                }
            } else {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();

                FileOutputStream out = new FileOutputStream(target);
                int len;
                while ((len = tar.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
                out.flush();
                out.close();

                // Set executable permissions
                if (name.startsWith("bin/") || name.startsWith("sbin/") ||
                        name.startsWith("usr/bin/") || name.startsWith("usr/sbin/") ||
                        name.contains(".so")) {
                    target.setExecutable(true, false);
                    target.setReadable(true, false);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        try {
                            Os.chmod(target.getAbsolutePath(), 0755);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        tar.close();
        decomp.close();
        raw.close();
    }

    private static void configureRootfs(File rootfsDir, File prefixDir) {
        try {
            // DNS configuration
            File etcDir = new File(rootfsDir, "etc");
            if (!etcDir.exists()) etcDir.mkdirs();

            File resolvConf = new File(etcDir, "resolv.conf");
            FileOutputStream resolvOut = new FileOutputStream(resolvConf);
            resolvOut.write("nameserver 8.8.8.8\nnameserver 8.8.4.4\n".getBytes());
            resolvOut.flush();
            resolvOut.close();

            File hostsFile = new File(etcDir, "hosts");
            FileOutputStream hostsOut = new FileOutputStream(hostsFile);
            hostsOut.write("127.0.0.1 localhost\n::1 localhost\n".getBytes());
            hostsOut.flush();
            hostsOut.close();

            // Ensure tmp directory exists
            File innerTmp = new File(rootfsDir, "tmp");
            if (!innerTmp.exists()) innerTmp.mkdirs();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    Os.chmod(innerTmp.getAbsolutePath(), 0777);
                } catch (Exception ignored) {}
            }

            // Ensure root home exists
            File innerRoot = new File(rootfsDir, "root");
            if (!innerRoot.exists()) innerRoot.mkdirs();

        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    private static void loginDistro(TermuxSessionManager sessionMgr, TermuxSessionManager.SessionConfig config,
                                    String distro, File distroBaseDir, String[] parts, OutputCallback callback) {
        File rootfs = new File(distroBaseDir, distro);
        if (!rootfs.exists() || !new File(rootfs, "bin/sh").exists()) {
            callback.onOutput("Distribution '" + distro + "' is not installed.\n" +
                    "Run 'proot install " + distro + "' first.");
            return;
        }

        sActiveDistro = distro;

        File prootBin = new File(config.getPrefixPath(), "bin/proot");
        File tmpDir = new File(config.getPrefixPath(), "tmp");
        if (!tmpDir.exists()) tmpDir.mkdirs();

        String testCmd = prootBin.getAbsolutePath() + " " +
                "--kill-on-exit " +
                "-r \"" + rootfs.getAbsolutePath() + "\" " +
                "-0 " +
                "-b /dev " +
                "-b /proc " +
                "-b /sys " +
                "-b \"" + tmpDir.getAbsolutePath() + ":/tmp\" " +
                "-b \"" + tmpDir.getAbsolutePath() + ":/data/data/com.termux/files/usr/tmp\" " +
                "-b /data " +
                "-b \"" + config.getHomePath() + ":/root/host_home\" " +
                "-w /root " +
                "/bin/sh -c \"cat /etc/os-release 2>/dev/null | grep PRETTY_NAME | cut -d= -f2 | tr -d '\\\"'\"";

        String prettyName = sessionMgr.executeDirectShellCommand(testCmd, 10);
        if (prettyName == null || prettyName.isEmpty() || prettyName.startsWith("[")) {
            prettyName = distro + " Linux";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("✓ Logged into '").append(distro).append("' container (root)\n");
        sb.append("OS: ").append(prettyName.trim()).append("\n");
        sb.append("Host home mounted at: /root/host_home\n");
        sb.append("Active shell: all commands now execute in ").append(distro).append(".\n");
        sb.append("Type 'exit' or 'proot logout' to return to Android host.");
        callback.onOutput(sb.toString());
    }

    private static void runInDistro(TermuxSessionManager sessionMgr, TermuxSessionManager.SessionConfig config,
                                    String distro, File distroBaseDir, String[] parts, OutputCallback callback) {
        StringBuilder cmdBuilder = new StringBuilder();
        for (int i = 3; i < parts.length; i++) {
            cmdBuilder.append(parts[i]).append(" ");
        }
        String innerCmd = cmdBuilder.toString().trim();
        if (innerCmd.isEmpty()) {
            callback.onOutput("Usage: proot run <distro> <command>");
            return;
        }

        executeSingleDistroCommand(sessionMgr, config, distro, distroBaseDir, innerCmd, callback);
    }

    public static void executeSingleDistroCommand(TermuxSessionManager sessionMgr, TermuxSessionManager.SessionConfig config,
                                                  String distro, File distroBaseDir, String innerCmd, OutputCallback callback) {
        File rootfs = new File(distroBaseDir, distro);
        if (!rootfs.exists() || !new File(rootfs, "bin/sh").exists()) {
            callback.onOutput("Distribution '" + distro + "' is not installed.\n" +
                    "Run 'proot install " + distro + "' first.");
            return;
        }

        File prootBin = new File(config.getPrefixPath(), "bin/proot");
        File tmpDir = new File(config.getPrefixPath(), "tmp");
        if (!tmpDir.exists()) tmpDir.mkdirs();

        String cmd = prootBin.getAbsolutePath() + " " +
                "--kill-on-exit " +
                "-r \"" + rootfs.getAbsolutePath() + "\" " +
                "-0 " +
                "-b /dev " +
                "-b /proc " +
                "-b /sys " +
                "-b \"" + tmpDir.getAbsolutePath() + ":/tmp\" " +
                "-b \"" + tmpDir.getAbsolutePath() + ":/data/data/com.termux/files/usr/tmp\" " +
                "-b /data " +
                "-b \"" + config.getHomePath() + ":/root/host_home\" " +
                "-w /root " +
                "/bin/sh -c \"" + innerCmd.replace("\"", "\\\"") + "\"";

        String result = sessionMgr.executeDirectShellCommand(cmd, 300);
        if (result != null && !result.isEmpty()) {
            callback.onOutput(result);
        }
    }
}
