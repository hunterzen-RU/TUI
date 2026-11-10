package ohi.andre.consolelauncher.managers;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
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
        public String type; // "tui", "bin", "script"
        public String url;
        public String targetName;
        public List<String> dependencies = new ArrayList<>();
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
                        list.add(pkg);
                    }
                }
            }
        } catch (Exception e) {
            Tuils.log(e);
        }
        return list;
    }

    public static void installPackage(final Context context, final String packageName, final InstallCallback callback) {
        String activeMgr = VariablesManager.getVariable("active_pkg_mgr");

        if ("apt".equalsIgnoreCase(activeMgr) || "pkg".equalsIgnoreCase(activeMgr)) {
            // BYPASS PATH: Execute via Bridge (Termux Wrapper)
            new Thread(() -> {
                String result = PackageHelper.executeBridged(context, "install " + packageName + " -y");
                if (callback != null) callback.onSuccess("Direct " + activeMgr + " install result:\n" + result);
            }).start();
            return;
        }

        // Standard Path (Hybrid)
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
                    // Native Fallback
                    new Thread(() -> {
                        String result = PackageHelper.executeBridged(context, "install " + packageName + " -y");
                        if (callback != null) callback.onSuccess("Package not in manifest, fallback to native:\n" + result);
                    }).start();
                }
            }

            @Override
            public void onError(String error) {
                if (callback != null) callback.onError(error);
            }
        });
    }

    private static void downloadAndInstall(final Context context, final PackageInfo pkg, final InstallCallback callback) {
        new Thread(() -> {
            try {
                TermuxSessionManager sessionMgr = new TermuxSessionManager(context);
                TermuxSessionManager.SessionConfig config = sessionMgr.createSessionConfig();

                // Install dependencies first
                if (pkg.dependencies != null && !pkg.dependencies.isEmpty()) {
                    Tuils.sendOutput(context, "Installing dependencies for " + pkg.name + "...");
                    for (String dep : pkg.dependencies) {
                        PackageHelper.executeBridged(context, "install " + dep + " -y");
                    }
                }

                // All types (tui, script, python, bash, etc.) go to tui/bin/ to allow execution
                File destDir = new File(config.getHomePath(), "tui/bin");
                if (!destDir.exists()) destDir.mkdirs();

                String filename = pkg.targetName != null && !pkg.targetName.isEmpty() ? pkg.targetName : pkg.name;
                File destFile = new File(destDir, filename);

                // Start Foreground Service
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

                // Inline download for CLI progress (Alternative: Listen to service broadcasts)
                Tuils.sendOutput(context, "Starting download: " + pkg.name);
                
                URL url = new URL(pkg.url);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();
                int fileLength = conn.getContentLength();
                InputStream input = conn.getInputStream();
                FileOutputStream output = new FileOutputStream(destFile);

                byte[] data = new byte[4096];
                long total = 0;
                int count;
                int lastProgress = -1;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        if (progress / 10 > lastProgress / 10) { // Update every 10%
                            String bar = getProgressBar(progress);
                            Tuils.sendOutput(context, pkg.name + " " + bar + " " + progress + "%");
                            lastProgress = progress;
                        }
                    }
                    output.write(data, 0, count);
                }

                output.flush();
                output.close();
                input.close();

                // Create shim if needed
                if ("apk".equalsIgnoreCase(pkg.type)) {
                    Tuils.sendOutput(context, "Download complete. Launching installer for " + pkg.name + "...");
                    Intent installIntent = new Intent(Intent.ACTION_VIEW);
                    installIntent.setDataAndType(androidx.core.content.FileProvider.getUriForFile(context, context.getPackageName() + ".FILE_PROVIDER", destFile), "application/vnd.android.package-archive");
                    installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(installIntent);
                } else if (!"tui".equalsIgnoreCase(pkg.type) && !"bin".equalsIgnoreCase(pkg.type)) {
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

                if (callback != null) {
                    callback.onSuccess("Successfully installed '" + pkg.name + "'");
                }

            } catch (Exception e) {
                if (callback != null) callback.onError("Install error: " + e.getLocalizedMessage());
            }
        }).start();
    }

    private static String getProgressBar(int progress) {
        int totalWidth = 20;
        int filledWidth = (progress * totalWidth) / 100;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < totalWidth; i++) {
            if (i < filledWidth) sb.append("#");
            else sb.append("-");
        }
        sb.append("]");
        return sb.toString();
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
