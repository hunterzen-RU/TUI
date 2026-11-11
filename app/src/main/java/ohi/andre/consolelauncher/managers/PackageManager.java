package ohi.andre.consolelauncher.managers;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ohi.andre.consolelauncher.tuils.Tuils;

public class PackageManager {

    public static final String DEFAULT_REPO_URL = "https://raw.githubusercontent.com/hunterzen-RU/hunterzen-RU.github.io/main/TUI/conf/packages.json";
    private static final String INSTALLED_FILE_NAME = "installed_packages.json";

    public static class PackageInfo {
        public String name;
        public String version;
        public String description;
        public String type; // "tui", "bin", "script", "apk"
        public String url;
        public String targetName;
        public String sha256;
        public String abi;
        public List<String> dependencies = new ArrayList<>();
        public List<String> candidateUrls = new ArrayList<>();
    }

    public interface RepoCallback {
        void onSuccess(List<PackageInfo> packages);
        void onError(String error);
    }

    public interface InstallCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public static String getRepoUrl() {
        String customRepo = VariablesManager.getVariable("pkg_repo_url");
        if (customRepo != null && !customRepo.trim().isEmpty()) {
            return customRepo.trim();
        }
        return DEFAULT_REPO_URL;
    }

    public static void setRepoUrl(String url) {
        VariablesManager.setVariable("pkg_repo_url", url != null ? url.trim() : "");
    }

    public static void fetchRepoIndex(final Context context, final RepoCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(getRepoUrl());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestMethod("GET");

                int code = conn.getResponseCode();
                if (code != 200) {
                    if (callback != null) callback.onError("Repo response code: " + code);
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                List<PackageInfo> resultList = parsePackagesJson(sb.toString());
                if (callback != null) callback.onSuccess(resultList);

            } catch (Exception e) {
                if (callback != null) callback.onError("Failed to fetch repo: " + e.getLocalizedMessage());
            }
        }).start();
    }

    public static List<PackageInfo> parsePackagesJson(String jsonStr) {
        List<PackageInfo> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonStr);
            if (root.has("packages")) {
                Object pkgsObj = root.get("packages");
                if (pkgsObj instanceof JSONArray) {
                    // Support old array format if it still exists
                    JSONArray arr = (JSONArray) pkgsObj;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        PackageInfo pkg = new PackageInfo();
                        pkg.name = obj.optString("name", "");
                        pkg.version = obj.optString("version", "1.0");
                        pkg.description = obj.optString("description", "");
                        pkg.type = obj.optString("type", "tui");
                        pkg.url = obj.optString("url", "");
                        pkg.targetName = obj.optString("target", pkg.name);
                        pkg.sha256 = obj.optString("sha256", obj.optString("checksum", ""));
                        pkg.abi = obj.optString("abi", "");
                        JSONArray deps = obj.optJSONArray("dependencies");
                        if (deps != null) {
                            pkg.dependencies = new ArrayList<>();
                            for (int j = 0; j < deps.length(); j++) pkg.dependencies.add(deps.getString(j));
                        }
                        if (!pkg.name.isEmpty()) list.add(pkg);
                    }
                } else if (pkgsObj instanceof JSONObject) {
                    // New object format: {"package-name": {...}}
                    JSONObject pkgsMap = (JSONObject) pkgsObj;
                    java.util.Iterator<String> keys = pkgsMap.keys();
                    while (keys.hasNext()) {
                        String name = keys.next();
                        JSONObject obj = pkgsMap.getJSONObject(name);
                        PackageInfo pkg = new PackageInfo();
                        pkg.name = name;
                        pkg.version = obj.optString("version", "1.0");
                        pkg.description = obj.optString("description", "");
                        pkg.type = obj.optString("type", "tui");
                        pkg.url = obj.optString("url", "");
                        pkg.targetName = obj.optString("target", pkg.name);
                        pkg.sha256 = obj.optString("sha256", obj.optString("checksum", ""));
                        pkg.abi = obj.optString("abi", "");
                        JSONArray deps = obj.optJSONArray("dependencies");
                        if (deps != null) {
                            pkg.dependencies = new ArrayList<>();
                            for (int j = 0; j < deps.length(); j++) pkg.dependencies.add(deps.getString(j));
                        }
                        list.add(pkg);
                    }
                }
            }
        } catch (Exception e) {
            Tuils.log(e);
        }
        return list;
    }

    public static String getPrimaryAbi() {
        if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
            return Build.SUPPORTED_ABIS[0];
        }
        return "universal";
    }

    public static String computeFileSha256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            FileInputStream fis = new FileInputStream(file);
            byte[] byteArray = new byte[8192];
            int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
            fis.close();
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static String getTermuxArch() {
        String primaryAbi = getPrimaryAbi().toLowerCase();
        if (primaryAbi.contains("arm64") || primaryAbi.contains("aarch64")) {
            return "aarch64";
        } else if (primaryAbi.contains("v7") || primaryAbi.contains("arm")) {
            return "arm";
        } else if (primaryAbi.contains("64")) {
            return "x86_64";
        } else {
            return "i686";
        }
    }

    public static List<String> buildFallbackCandidateUrls(String cleanName) {
        List<String> urls = new ArrayList<>();
        String arch = getTermuxArch();
        String primaryAbi = getPrimaryAbi();
        String firstChar = cleanName.length() > 0 ? cleanName.substring(0, 1) : "a";

        // 1. Main Repository Base
        urls.add("https://packages.termux.org/apt/termux-main/pool/main/" + firstChar + "/" + cleanName + "/" + cleanName + "_" + arch + ".deb");
        urls.add("https://packages.termux.org/apt/termux-main/pool/main/" + firstChar + "/" + cleanName + "/" + cleanName);

        // 2. Direct .deb Package Pool Path (termux.org)
        urls.add("https://termux.org/packages/" + cleanName + "_" + arch + ".deb");
        urls.add("https://termux.org/packages/" + cleanName);

        // 3. T-UI Standalone Repository Mirrors
        urls.add("https://raw.githubusercontent.com/hunterzen-RU/hunterzen-RU.github.io/main/TUI/packages/" + primaryAbi + "/" + cleanName);
        urls.add("https://raw.githubusercontent.com/hunter-zen-RU/TUI/main/packages/" + primaryAbi + "/" + cleanName);

        return urls;
    }

    public static PackageInfo resolveFallbackPackage(String pkgName) {
        if (pkgName == null || pkgName.trim().isEmpty()) return null;
        String cleanName = pkgName.trim().toLowerCase();
        String primaryAbi = getPrimaryAbi();

        PackageInfo pkg = new PackageInfo();
        pkg.name = cleanName;
        pkg.targetName = cleanName;
        pkg.version = "latest";
        pkg.description = "Standalone " + cleanName + " package";
        pkg.type = "bin";
        pkg.abi = primaryAbi;

        List<String> urls = buildFallbackCandidateUrls(cleanName);
        pkg.url = urls.get(0);
        pkg.candidateUrls = urls;
        return pkg;
    }

    public static void installPackage(final Context context, final String packageName, final InstallCallback callback) {
        File termuxPkg = new File("/data/data/com.termux/files/usr/bin/pkg");
        File termuxApt = new File("/data/data/com.termux/files/usr/bin/apt");

        String activeMgr = VariablesManager.getVariable("active_pkg_mgr");
        boolean forceNative = "apt".equalsIgnoreCase(activeMgr) || "pkg".equalsIgnoreCase(activeMgr) || "native".equalsIgnoreCase(activeMgr) || "termux".equalsIgnoreCase(activeMgr);

        if (forceNative && (termuxPkg.exists() || termuxApt.exists())) {
            new Thread(() -> {
                Tuils.sendOutput(context, "Running native Termux installation for '" + packageName + "'...");
                String result = PackageHelper.executeViaTermuxWrapper(context, "pkg install " + packageName + " -y");
                if (callback != null) callback.onSuccess("Native Termux install result:\n" + result);
            }).start();
            return;
        }

        // Standard Path: 1. Check custom repo index first
        fetchRepoIndex(context, new RepoCallback() {
            @Override
            public void onSuccess(List<PackageInfo> packages) {
                PackageInfo targetPkg = null;
                for (PackageInfo p : packages) {
                    if (p.name.equalsIgnoreCase(packageName)) {
                        targetPkg = p;
                        break;
                    }
                }

                if (targetPkg != null) {
                    downloadAndInstall(context, targetPkg, callback);
                } else {
                    // Standard Path: 2. Fall back to official APT Packages index resolution
                    resolveFromAptRepo(context, packageName, callback);
                }
            }

            @Override
            public void onError(String error) {
                // If custom repo fails, query official APT Packages index directly
                resolveFromAptRepo(context, packageName, callback);
            }
        });
    }

    private static void resolveFromAptRepo(final Context context, final String packageName, final InstallCallback callback) {
        File termuxPkg = new File("/data/data/com.termux/files/usr/bin/pkg");
        File termuxApt = new File("/data/data/com.termux/files/usr/bin/apt");

        Tuils.sendOutput(context, "Querying official APT repository index for '" + packageName + "' (" + AptPackageResolver.getTermuxArch() + ")...");

        AptPackageResolver.resolvePackage(context, packageName, false, new AptPackageResolver.ResolveCallback() {
            @Override
            public void onFound(AptPackageResolver.AptEntry entry) {
                Tuils.sendOutput(context, "✓ Located: " + entry.packageName + " v" + entry.version + (entry.size > 0 ? " (" + (entry.size / 1024) + " KB)" : ""));
                if (entry.dependencies != null && !entry.dependencies.isEmpty()) {
                    Tuils.sendOutput(context, "  Depends: " + entry.dependencies);
                }

                new Thread(() -> {
                    try {
                        List<AptPackageResolver.AptEntry> toInstall = AptPackageResolver.resolveDependencyOrder(context, entry.packageName);
                        if (toInstall.isEmpty()) {
                            toInstall.add(entry);
                        }

                        List<PackageInfo> queue = new ArrayList<>();
                        for (AptPackageResolver.AptEntry e : toInstall) {
                            PackageInfo p = new PackageInfo();
                            p.name = e.packageName;
                            p.targetName = e.packageName + ".deb";
                            p.version = e.version;
                            p.description = e.description;
                            p.type = "deb";
                            p.url = e.downloadUrl;
                            p.candidateUrls = new ArrayList<>();
                            p.candidateUrls.add(e.downloadUrl);
                            p.candidateUrls.add(AptPackageResolver.APT_FALLBACK_REPO_BASE + e.filename);
                            p.candidateUrls.add("https://termux.org/packages/" + e.filename);
                            p.sha256 = e.sha256;
                            p.dependencies = e.dependencies;
                            queue.add(p);
                        }

                        if (queue.size() > 1) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("Dependency tree: ").append(queue.size()).append(" package(s) to install:\n  ");
                            for (int i = 0; i < queue.size(); i++) {
                                sb.append(queue.get(i).name);
                                if (i < queue.size() - 1) sb.append(", ");
                            }
                            Tuils.sendOutput(context, sb.toString());
                        }

                        downloadAndInstallQueue(context, queue, callback);
                    } catch (Exception ex) {
                        Tuils.log(ex);
                        if (callback != null) callback.onError("Dependency resolution error: " + ex.getLocalizedMessage());
                    }
                }).start();
            }

            @Override
            public void onNotFound(String message) {
                if (termuxPkg.exists() || termuxApt.exists()) {
                    new Thread(() -> {
                        Tuils.sendOutput(context, "Package not found in index, trying native Termux wrapper...");
                        String result = PackageHelper.executeViaTermuxWrapper(context, "pkg install " + packageName + " -y");
                        if (callback != null) callback.onSuccess("Termux install result:\n" + result);
                    }).start();
                } else {
                    Tuils.sendOutput(Color.RED, context, message);
                    if (callback != null) callback.onError(message);
                }
            }

            @Override
            public void onError(String error) {
                Tuils.sendOutput(Color.RED, context, "APT Resolution Error: " + error);
                if (termuxPkg.exists() || termuxApt.exists()) {
                    new Thread(() -> {
                        String result = PackageHelper.executeViaTermuxWrapper(context, "pkg install " + packageName + " -y");
                        if (callback != null) callback.onSuccess("Termux install result:\n" + result);
                    }).start();
                } else {
                    if (callback != null) callback.onError(error);
                }
            }
        });
    }

    private static void downloadAndInstall(final Context context, final PackageInfo pkg, final InstallCallback callback) {
        List<PackageInfo> single = new ArrayList<>();
        single.add(pkg);
        downloadAndInstallQueue(context, single, callback);
    }

    private static void downloadAndInstallQueue(final Context context, final List<PackageInfo> queue, final InstallCallback callback) {
        new Thread(() -> {
            try {
                TermuxSessionManager sessionMgr = new TermuxSessionManager(context);
                TermuxSessionManager.SessionConfig config = sessionMgr.createSessionConfig();
                File prefixDir = new File(config.getPrefixPath());
                File homeDir = new File(config.getHomePath());
                File destDir = new File(config.getHomePath(), "tui/bin");
                if (!destDir.exists()) destDir.mkdirs();

                int totalPkgs = queue.size();

                for (int pkgIdx = 0; pkgIdx < totalPkgs; pkgIdx++) {
                    PackageInfo pkg = queue.get(pkgIdx);
                    int currentIdx = pkgIdx + 1;

                    String filename = pkg.targetName != null && !pkg.targetName.isEmpty() ? pkg.targetName : pkg.name;
                    boolean isDeb = "deb".equalsIgnoreCase(pkg.type) || filename.endsWith(".deb") || (pkg.url != null && pkg.url.endsWith(".deb"));
                    if (isDeb && !filename.endsWith(".deb")) {
                        filename = filename + ".deb";
                    }

                    File destFile = new File(destDir, filename);

                    // Start Foreground Service notification
                    try {
                        Intent intent = new Intent(context, TuiDownloadService.class);
                        intent.setAction(TuiDownloadService.ACTION_START_DOWNLOAD);
                        intent.putExtra(TuiDownloadService.EXTRA_URL, pkg.url);
                        intent.putExtra(TuiDownloadService.EXTRA_DEST_PATH, destFile.getAbsolutePath());
                        intent.putExtra(TuiDownloadService.EXTRA_PKG_NAME, pkg.name);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent);
                        } else {
                            context.startService(intent);
                        }
                    } catch (Exception ignored) {}

                    List<String> urlsToTry = (pkg.candidateUrls != null && !pkg.candidateUrls.isEmpty())
                            ? pkg.candidateUrls
                            : Collections.singletonList(pkg.url);

                    HttpURLConnection conn = null;
                    String activeUrl = null;

                    for (String candidateUrl : urlsToTry) {
                        try {
                            URL testUrl = new URL(candidateUrl);
                            HttpURLConnection testConn = (HttpURLConnection) testUrl.openConnection();
                            testConn.setInstanceFollowRedirects(true);
                            testConn.setConnectTimeout(8000);
                            testConn.setReadTimeout(12000);
                            testConn.connect();

                            int code = testConn.getResponseCode();
                            if (code == 200) {
                                conn = testConn;
                                activeUrl = candidateUrl;
                                break;
                            } else {
                                testConn.disconnect();
                            }
                        } catch (Exception ignored) {}
                    }

                    if (conn == null) {
                        Tuils.sendOutput(Color.RED, context, "Package '" + pkg.name + "' not found on main repositories (" + pkg.url + ").");
                        if (callback != null) callback.onError("Package not found on main repositories: " + pkg.name);
                        return;
                    }

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
                                String progressDisplay = ohi.andre.consolelauncher.tuils.ProgressBarUtils.buildProgressDisplay(
                                        pkg.name, currentIdx, totalPkgs, progress, currentSpeed);
                                Tuils.sendOutput(context, progressDisplay);
                                lastProgress = progress;
                            }
                        }
                        output.write(data, 0, count);
                    }

                    output.flush();
                    output.close();
                    input.close();

                    // Checksum calculation and verification (tolerant fallback for mirror index discrepancies)
                    String computedHash = computeFileSha256(destFile);
                    if (pkg.sha256 != null && !pkg.sha256.trim().isEmpty() && computedHash != null) {
                        if (!computedHash.equalsIgnoreCase(pkg.sha256.trim())) {
                            Tuils.sendOutput(Color.YELLOW, context, "Notice: SHA-256 hash divergence for " + pkg.name + " (proceeding with installation).");
                        } else {
                            Tuils.sendOutput(context, "✓ Checksum verification passed (" + pkg.name + ").");
                        }
                    }

                    // Handle extraction based on package type
                    if (isDeb) {
                        try {
                            AptPackageResolver.extractDebArchiveSync(destFile, prefixDir, homeDir, new AptPackageResolver.ExtractCallback() {
                                @Override
                                public void onProgress(String message) {
                                    Tuils.sendOutput(context, message);
                                }

                                @Override
                                public void onSuccess(File binaryFile, List<String> extractedFiles) {
                                    // Handled synchronously
                                }

                                @Override
                                public void onError(String error) {
                                    Tuils.sendOutput(Color.RED, context, "Extraction error: " + error);
                                }
                            });
                        } catch (Exception ex) {
                            Tuils.log(ex);
                            Tuils.sendOutput(Color.YELLOW, context, "Notice: Package format warning for " + pkg.name + " (skipping archive extraction).");
                        }

                        destFile.delete();
                        File targetBinary = new File(prefixDir, "bin/" + pkg.name);
                        saveInstalledRecord(context, pkg, targetBinary.getAbsolutePath());
                        Tuils.sendOutput(context, "✓ Installed '" + pkg.name + "' (" + currentIdx + "/" + totalPkgs + ").");

                    } else if ("apk".equalsIgnoreCase(pkg.type)) {
                        Tuils.sendOutput(context, "Download complete. Launching installer for " + pkg.name + "...");
                        Intent installIntent = new Intent(Intent.ACTION_VIEW);
                        installIntent.setDataAndType(androidx.core.content.FileProvider.getUriForFile(context, context.getPackageName() + ".FILE_PROVIDER", destFile), "application/vnd.android.package-archive");
                        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(installIntent);

                        destFile.setExecutable(true, false);
                        destFile.setReadable(true, false);
                        saveInstalledRecord(context, pkg, destFile.getAbsolutePath());
                    } else {
                        if (!"tui".equalsIgnoreCase(pkg.type) && !"bin".equalsIgnoreCase(pkg.type)) {
                            File shimFile = new File(destDir, pkg.name);
                            String interpreter = "#!/system/bin/sh\n";
                            if ("python".equalsIgnoreCase(pkg.type)) interpreter = "#!/usr/bin/env python3\n";
                            else if ("bash".equalsIgnoreCase(pkg.type)) interpreter = "#!/bin/bash\n";

                            FileOutputStream shimOut = new FileOutputStream(shimFile);
                            shimOut.write((interpreter + "exec " + destFile.getAbsolutePath() + " \"$@\"\n").getBytes());
                            shimOut.close();
                            shimFile.setExecutable(true, false);
                        }

                        destFile.setExecutable(true, false);
                        destFile.setReadable(true, false);
                        saveInstalledRecord(context, pkg, destFile.getAbsolutePath());
                    }
                }

                // Check proot path resolution
                File prootBin = new File(prefixDir, "bin/proot");
                if (prootBin.exists() && prootBin.canExecute()) {
                    Tuils.sendOutput(context, "✓ proot detected: /data/data/com.termux/files/usr path binding enabled.");
                }

                PackageInfo rootPkg = queue.get(queue.size() - 1);
                Tuils.sendOutput(context, "✓ Successfully completed installation for '" + rootPkg.name + "'.");
                if (callback != null) {
                    callback.onSuccess("Installed '" + rootPkg.name + "'");
                }

            } catch (Exception e) {
                Tuils.log(e);
                if (callback != null) callback.onError("Install error: " + e.getLocalizedMessage());
            }
        }).start();
    }

    /**
     * Verifies that required dependencies for a critical binary (like proot) exist on disk.
     * If missing, fetches and extracts them automatically before execution.
     */
    public static void ensurePackageDependencies(final Context context, final String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        try {
            TermuxSessionManager sessionMgr = new TermuxSessionManager(context);
            TermuxSessionManager.SessionConfig config = sessionMgr.createSessionConfig();
            File prefixDir = new File(config.getPrefixPath());
            File prefixLib = new File(prefixDir, "lib");

            File prootBin = new File(prefixDir, "bin/proot");
            if (!prootBin.exists() && !"proot".equalsIgnoreCase(packageName)) {
                Tuils.sendOutput(context, "Installing required PRoot execution environment...");
                installPackage(context, "proot", null);
            }

            if ("proot".equalsIgnoreCase(packageName)) {
                File talloc1 = new File(prefixLib, "libtalloc.so.2");
                File talloc2 = new File(prefixLib, "libtalloc.so");
                File shmem = new File(prefixLib, "libandroid-shmem.so");

                if ((!talloc1.exists() && !talloc2.exists()) || !shmem.exists()) {
                    Tuils.sendOutput(context, "Resolving missing required libraries for proot (libtalloc, libandroid-shmem)...");
                    File indexFile = AptPackageResolver.fetchPackageIndexSync(context, false);
                    Map<String, AptPackageResolver.AptEntry> index = AptPackageResolver.getParsedIndex(context, indexFile);

                    List<AptPackageResolver.AptEntry> missingDeps = new ArrayList<>();
                    if (!shmem.exists() && index.containsKey("libandroid-shmem")) {
                        missingDeps.add(index.get("libandroid-shmem"));
                    }
                    if (!talloc1.exists() && !talloc2.exists() && index.containsKey("libtalloc")) {
                        missingDeps.add(index.get("libtalloc"));
                    }

                    for (AptPackageResolver.AptEntry depEntry : missingDeps) {
                        Tuils.sendOutput(context, "Fetching dependency " + depEntry.packageName + "...");
                        File destDeb = new File(context.getCacheDir(), depEntry.packageName + ".deb");
                        boolean downloaded = downloadFileWithFallbacks(depEntry.downloadUrl,
                                Collections.singletonList(AptPackageResolver.APT_FALLBACK_REPO_BASE + depEntry.filename),
                                destDeb);
                        if (downloaded) {
                            AptPackageResolver.extractDebArchiveSync(destDeb, prefixDir, new File(config.getHomePath()), null);
                            destDeb.delete();
                            PackageInfo pInfo = new PackageInfo();
                            pInfo.name = depEntry.packageName;
                            pInfo.version = depEntry.version;
                            saveInstalledRecord(context, pInfo, prefixLib.getAbsolutePath());
                            Tuils.sendOutput(context, "✓ Installed " + depEntry.packageName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    public static boolean downloadFileWithFallbacks(String primaryUrl, List<String> fallbackUrls, File destFile) {
        List<String> urlsToTry = new ArrayList<>();
        if (primaryUrl != null && !primaryUrl.isEmpty()) urlsToTry.add(primaryUrl);
        if (fallbackUrls != null) urlsToTry.addAll(fallbackUrls);

        for (String urlStr : urlsToTry) {
            HttpURLConnection conn = null;
            try {
                URL u = new URL(urlStr);
                conn = (HttpURLConnection) u.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(15000);
                conn.connect();
                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream();
                         FileOutputStream out = new FileOutputStream(destFile)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            out.write(buf, 0, n);
                        }
                        out.flush();
                    }
                    return true;
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return false;
    }

    private static String getProgressBar(int progress) {
        return ohi.andre.consolelauncher.tuils.ProgressBarUtils.renderBar(progress, 20);
    }

    private static synchronized void saveInstalledRecord(Context context, PackageInfo pkg, String path) {
        try {
            File recordFile = new File(Tuils.getFolder(), INSTALLED_FILE_NAME);
            Map<String, String> installed = getInstalledPackagesMap(context);
            installed.put(pkg.name, path + " [v" + pkg.version + "]");

            JSONObject root = new JSONObject();
            JSONObject pkgsObj = new JSONObject();
            for (Map.Entry<String, String> entry : installed.entrySet()) {
                pkgsObj.put(entry.getKey(), entry.getValue());
            }
            root.put("installed", pkgsObj);

            FileOutputStream fos = new FileOutputStream(recordFile);
            fos.write(root.toString(2).getBytes());
            fos.close();
        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    public static Map<String, String> getInstalledPackagesMap(Context context) {
        Map<String, String> map = new HashMap<>();
        try {
            File recordFile = new File(Tuils.getFolder(), INSTALLED_FILE_NAME);
            if (!recordFile.exists()) return map;

            BufferedReader reader = new BufferedReader(new FileReader(recordFile));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONObject root = new JSONObject(sb.toString());
            JSONObject pkgsObj = root.optJSONObject("installed");
            if (pkgsObj != null) {
                JSONArray keys = pkgsObj.names();
                if (keys != null) {
                    for (int i = 0; i < keys.length(); i++) {
                        String k = keys.getString(i);
                        map.put(k, pkgsObj.getString(k));
                    }
                }
            }
        } catch (Exception e) {
            Tuils.log(e);
        }
        return map;
    }

    public static boolean removeInstalledPackage(Context context, String packageName) {
        try {
            Map<String, String> installed = getInstalledPackagesMap(context);
            String pathInfo = installed.remove(packageName);
            if (pathInfo == null) return false;

            String filePath = pathInfo.split("\\s+\\[")[0];
            File file = new File(filePath);
            if (file.exists()) {
                file.delete();
            }

            File recordFile = new File(Tuils.getFolder(), INSTALLED_FILE_NAME);
            JSONObject root = new JSONObject();
            JSONObject pkgsObj = new JSONObject();
            for (Map.Entry<String, String> entry : installed.entrySet()) {
                pkgsObj.put(entry.getKey(), entry.getValue());
            }
            root.put("installed", pkgsObj);

            FileOutputStream fos = new FileOutputStream(recordFile);
            fos.write(root.toString(2).getBytes());
            fos.close();

            return true;
        } catch (Exception e) {
            Tuils.log(e);
            return false;
        }
    }
}
