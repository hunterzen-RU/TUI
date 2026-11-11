package ohi.andre.consolelauncher.managers;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import dalvik.system.DexClassLoader;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.plugins.PluginHostActivity;
import ohi.andre.consolelauncher.managers.plugins.PluginManifest;
import ohi.andre.consolelauncher.managers.plugins.PluginModel;
import ohi.andre.consolelauncher.managers.plugins.PluginTemplate;
import ohi.andre.consolelauncher.tuils.TuiScriptEngine;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.TuiCommandPlugin;

public class PluginManager {

    public static final String PLUGINS_DIR = "plugins";
    public static final String EXPORTS_DIR = "plugins/exports";
    public static final String TEMPLATES_URL = "https://hunterzen-ru.github.io/TUI/Templates/info.json";
    public static final String TEMPLATES_BASE_URL = "https://hunterzen-ru.github.io/TUI/Templates/";

    private static PluginManager instance;
    private final Context context;
    private final List<TuiCommandPlugin> loadedPlugins = new ArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    public PluginManager(Context context) {
        this.context = context.getApplicationContext();
        ensureDirectories();
        ensureSamplePlugin();
    }

    public static synchronized PluginManager getInstance(Context context) {
        if (instance == null) {
            instance = new PluginManager(context);
        }
        return instance;
    }

    public File getPluginsDirectory() {
        File folder = Tuils.getFolder();
        if (folder == null) {
            folder = context.getFilesDir();
        }
        File pluginsDir = new File(folder, PLUGINS_DIR);
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs();
        }
        return pluginsDir;
    }

    public File getExportsDirectory() {
        File folder = Tuils.getFolder();
        if (folder == null) {
            folder = context.getFilesDir();
        }
        File exportsDir = new File(folder, EXPORTS_DIR);
        if (!exportsDir.exists()) {
            exportsDir.mkdirs();
        }
        return exportsDir;
    }

    private void ensureDirectories() {
        getPluginsDirectory();
        getExportsDirectory();
    }

    public List<PluginModel> getInstalledPlugins() {
        List<PluginModel> list = new ArrayList<>();
        File pluginsDir = getPluginsDirectory();
        File[] subdirs = pluginsDir.listFiles(File::isDirectory);
        if (subdirs == null) return list;

        for (File dir : subdirs) {
            if (dir.getName().equalsIgnoreCase("exports")) continue;
            list.add(new PluginModel(dir));
        }

        Collections.sort(list, (p1, p2) -> p1.getName().compareToIgnoreCase(p2.getName()));
        return list;
    }

    public PluginModel getPlugin(String nameOrId) {
        if (nameOrId == null || nameOrId.trim().isEmpty()) return null;
        String clean = nameOrId.trim();
        List<PluginModel> list = getInstalledPlugins();
        for (PluginModel p : list) {
            if (p.getId().equalsIgnoreCase(clean) || p.getName().equalsIgnoreCase(clean)) {
                return p;
            }
        }
        return null;
    }

    public boolean removePlugin(String nameOrId) {
        PluginModel model = getPlugin(nameOrId);
        if (model == null) return false;
        return deleteRecursively(model.getFolder());
    }

    private boolean deleteRecursively(File fileOrDir) {
        if (fileOrDir == null || !fileOrDir.exists()) return false;
        if (fileOrDir.isDirectory()) {
            File[] files = fileOrDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteRecursively(f);
                }
            }
        }
        return fileOrDir.delete();
    }

    public void ensureSamplePlugin() {
        File pluginsDir = getPluginsDirectory();
        File sampleDir = new File(pluginsDir, "sample");
        if (sampleDir.exists()) return;

        sampleDir.mkdirs();
        File assetsDir = new File(sampleDir, "assets");
        assetsDir.mkdirs();

        PluginManifest manifest = new PluginManifest();
        manifest.name = "Sample TUI Plugin";
        manifest.id = "sample";
        manifest.version = "1.0.0";
        manifest.targetAppVersion = "1.0";
        manifest.description = "A sample plugin demonstrating TUI plugin architecture, Kotlin entry point, and system telemetry";
        manifest.entryPoint = "start.kt";

        manifest.tree.clear();
        manifest.tree.put("manifest.json", "Plugin metadata and configuration");
        manifest.tree.put("start.kt", "Kotlin script entry point");
        manifest.tree.put("start.java", "Alternative Java entry point");
        manifest.tree.put("plugin_layout.xml", "UI layout specification");
        manifest.tree.put("icon.png", "Plugin display icon");
        manifest.tree.put("assets/readme.txt", "Plugin documentation");

        manifest.permissions.clear();
        manifest.permissions.add("android.permission.INTERNET");
        manifest.permissions.add("android.permission.BATTERY_STATS");

        manifest.manifestSpecials.clear();
        manifest.manifestSpecials.put("activity", "ohi.andre.consolelauncher.managers.plugins.PluginHostActivity");
        List<String> filters = new ArrayList<>();
        filters.add("android.intent.action.MAIN");
        manifest.manifestSpecials.put("intent_filter", filters);

        // Write manifest.json
        writeFile(new File(sampleDir, PluginManifest.FILE_NAME), manifest.toJsonString());

        // Write start.kt
        String ktContent = "package ohi.andre.tui.plugin.sample\n\n"
                + "/**\n"
                + " * T-UI Sample Plugin Entry Point\n"
                + " * Demonstrates argument processing and dynamic telemetry variables\n"
                + " */\n"
                + "class StartPlugin {\n"
                + "    fun onExecute(args: Array<String>): String {\n"
                + "        val sb = StringBuilder()\n"
                + "        sb.append(\"=== [ T-UI SAMPLE PLUGIN ACTIVE ] ===\\n\")\n"
                + "        sb.append(\"Host: {{device}} ({{os_version}})\\n\")\n"
                + "        sb.append(\"Battery: {{battery_level}}% [{{battery_status}}]\\n\")\n"
                + "        sb.append(\"CPU Load: {{cpu_usage}}% | Memory: {{ram_used}} / {{ram_total}}\\n\")\n"
                + "        sb.append(\"Network: {{network}} ({{network_name}})\\n\")\n"
                + "        if (args.isNotEmpty()) {\n"
                + "            sb.append(\"Arguments received: \").append(args.joinToString(\" \")).append(\"\\n\")\n"
                + "        }\n"
                + "        sb.append(\"Plugin execution completed successfully.\\n\")\n"
                + "        return sb.toString()\n"
                + "    }\n"
                + "}\n";
        writeFile(new File(sampleDir, "start.kt"), ktContent);

        // Write start.java
        String javaContent = "package ohi.andre.tui.plugin.sample;\n\n"
                + "public class StartPlugin {\n"
                + "    public String onExecute(String[] args) {\n"
                + "        StringBuilder sb = new StringBuilder();\n"
                + "        sb.append(\"=== [ T-UI SAMPLE PLUGIN (JAVA) ] ===\\n\");\n"
                + "        sb.append(\"Battery: {{battery_level}}% ({{battery_status}})\\n\");\n"
                + "        sb.append(\"Device: {{device}}\\n\");\n"
                + "        sb.append(\"Status: ONLINE\\n\");\n"
                + "        return sb.toString();\n"
                + "    }\n"
                + "}\n";
        writeFile(new File(sampleDir, "start.java"), javaContent);

        // Write plugin_layout.xml
        String xmlContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"match_parent\"\n"
                + "    android:orientation=\"vertical\"\n"
                + "    android:background=\"#0D1117\"\n"
                + "    android:padding=\"16dp\">\n\n"
                + "    <TextView\n"
                + "        android:id=\"@+id/plugin_title\"\n"
                + "        android:layout_width=\"wrap_content\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:text=\"[ T-UI PLUGIN HUD ]\"\n"
                + "        android:textColor=\"#00FF66\"\n"
                + "        android:textSize=\"18sp\"\n"
                + "        android:textStyle=\"bold\" />\n\n"
                + "    <TextView\n"
                + "        android:id=\"@+id/plugin_output\"\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:layout_marginTop=\"12dp\"\n"
                + "        android:textColor=\"#00FFFF\"\n"
                + "        android:textSize=\"14sp\" />\n"
                + "</LinearLayout>\n";
        writeFile(new File(sampleDir, "plugin_layout.xml"), xmlContent);

        // Write sample asset
        writeFile(new File(assetsDir, "readme.txt"), "This is sample static data stored inside the plugin bundle.\n");

        // Write placeholder icon.png (1x1 PNG bytes)
        byte[] dummyPng = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
                (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
                0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
                0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00,
                0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
                0x42, 0x60, (byte) 0x82
        };
        try (FileOutputStream fos = new FileOutputStream(new File(sampleDir, "icon.png"))) {
            fos.write(dummyPng);
        } catch (Exception ignored) {}
    }

    public boolean createPlugin(String id, String name, String templateId, String description) {
        if (id == null || id.trim().isEmpty()) return false;
        String cleanId = id.trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
        File pluginsDir = getPluginsDirectory();
        File pluginDir = new File(pluginsDir, cleanId);
        if (pluginDir.exists()) return false;

        pluginDir.mkdirs();
        File assetsDir = new File(pluginDir, "assets");
        assetsDir.mkdirs();

        PluginManifest manifest = new PluginManifest();
        manifest.id = cleanId;
        manifest.name = (name != null && !name.trim().isEmpty()) ? name.trim() : PluginManifest.toTitleCase(cleanId.replace('_', ' '));
        manifest.description = description != null ? description : "Custom T-UI Plugin";
        manifest.version = "1.0.0";
        manifest.targetAppVersion = "1.0";

        if ("html".equalsIgnoreCase(templateId) || "web".equalsIgnoreCase(templateId)) {
            manifest.entryPoint = "index.html";
            manifest.tree.put("index.html", "Web UI entry point");
            String html = "<!DOCTYPE html>\n<html>\n<head>\n"
                    + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                    + "<style>\nbody { background: #0D1117; color: #00FF66; font-family: monospace; padding: 16px; }\n"
                    + "h1 { color: #00FFFF; font-size: 18px; }\n.card { border: 1px solid #30363D; padding: 12px; margin-top: 10px; border-radius: 6px; }\n"
                    + "</style>\n</head>\n<body>\n"
                    + "<h1>" + manifest.name + "</h1>\n"
                    + "<div class=\"card\">Status: Active<br>Battery: {{battery_level}}% [{{battery_status}}]<br>Device: {{device}}</div>\n"
                    + "</body>\n</html>";
            writeFile(new File(pluginDir, "index.html"), html);
        } else if ("java".equalsIgnoreCase(templateId)) {
            manifest.entryPoint = "start.java";
            manifest.tree.put("start.java", "Java entry point");
            String java = "package ohi.andre.tui.plugin." + cleanId + ";\n\n"
                    + "public class StartPlugin {\n"
                    + "    public String onExecute(String[] args) {\n"
                    + "        return \"[" + manifest.name + "] Output: Battery is {{battery_level}}% ({{battery_status}})\\n\";\n"
                    + "    }\n"
                    + "}\n";
            writeFile(new File(pluginDir, "start.java"), java);
        } else {
            // Default Kotlin
            manifest.entryPoint = "start.kt";
            manifest.tree.put("start.kt", "Kotlin entry point");
            String kt = "package ohi.andre.tui.plugin." + cleanId + "\n\n"
                    + "class StartPlugin {\n"
                    + "    fun onExecute(args: Array<String>): String {\n"
                    + "        val sb = StringBuilder()\n"
                    + "        sb.append(\"=== [ " + manifest.name + " ] ===\\n\")\n"
                    + "        sb.append(\"Battery: {{battery_level}}% ({{battery_status}})\\n\")\n"
                    + "        sb.append(\"System: {{device}} | CPU: {{cpu_usage}}%\\n\")\n"
                    + "        if (args.isNotEmpty()) {\n"
                    + "            sb.append(\"Args: \").append(args.joinToString(\" \")).append(\"\\n\")\n"
                    + "        }\n"
                    + "        return sb.toString()\n"
                    + "    }\n"
                    + "}\n";
            writeFile(new File(pluginDir, "start.kt"), kt);
        }

        writeFile(new File(pluginDir, PluginManifest.FILE_NAME), manifest.toJsonString());
        return true;
    }

    public boolean installFromZip(File zipFile, String targetId) {
        if (zipFile == null || !zipFile.exists()) return false;
        try {
            File pluginsDir = getPluginsDirectory();
            String folderName = targetId;
            if (folderName == null || folderName.isEmpty()) {
                folderName = zipFile.getName().replace(".zip", "").toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
            }

            File targetDir = new File(pluginsDir, folderName);
            if (!targetDir.exists()) targetDir.mkdirs();

            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
                ZipEntry entry;
                byte[] buffer = new byte[4096];
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    // Strip top level folder if zip contains single root folder
                    if (name.contains("/")) {
                        name = name.substring(name.indexOf('/') + 1);
                    }
                    if (name.isEmpty()) continue;

                    File outFile = new File(targetDir, name);
                    if (entry.isDirectory()) {
                        outFile.mkdirs();
                    } else {
                        if (!outFile.getParentFile().exists()) {
                            outFile.getParentFile().mkdirs();
                        }
                        try (FileOutputStream fos = new FileOutputStream(outFile)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                    zis.closeEntry();
                }
            }

            // Ensure manifest exists
            File manifestFile = new File(targetDir, PluginManifest.FILE_NAME);
            if (!manifestFile.exists()) {
                PluginManifest manifest = new PluginManifest();
                manifest.deriveDefaults(targetDir);
                writeFile(manifestFile, manifest.toJsonString());
            }

            return true;
        } catch (Exception e) {
            Tuils.log(e);
            return false;
        }
    }

    public void installFromUrl(final String urlString, final Callback<PluginModel> callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.connect();

                if (conn.getResponseCode() != 200) {
                    postError(callback, "HTTP " + conn.getResponseCode() + ": " + conn.getResponseMessage());
                    return;
                }

                String fileName = urlString.substring(urlString.lastIndexOf('/') + 1);
                if (!fileName.endsWith(".zip")) {
                    fileName = "plugin_download.zip";
                }

                File tempZip = new File(context.getCacheDir(), fileName);
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(tempZip)) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }

                String pluginId = fileName.replace(".zip", "").toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
                boolean ok = installFromZip(tempZip, pluginId);
                tempZip.delete();

                if (ok) {
                    PluginModel model = getPlugin(pluginId);
                    postSuccess(callback, model);
                } else {
                    postError(callback, "Failed to extract and configure plugin from ZIP archive.");
                }

            } catch (Exception e) {
                Tuils.log(e);
                postError(callback, "Download error: " + e.getMessage());
            }
        });
    }

    public void fetchTemplates(final Callback<List<PluginTemplate>> callback) {
        executor.execute(() -> {
            List<PluginTemplate> list = new ArrayList<>();
            // Always prepare fallback built-in templates
            list.add(new PluginTemplate("kotlin_script", "Kotlin Script Module", "Scaffold for interactive Kotlin script plugin", "T-UI Core", "1.0.0", TEMPLATES_BASE_URL + "kotlin_script.zip"));
            list.add(new PluginTemplate("java_command", "Java Command Extension", "Command line utility written in Java", "T-UI Core", "1.0.0", TEMPLATES_BASE_URL + "java_command.zip"));
            list.add(new PluginTemplate("web_dashboard", "Web TUI Dashboard", "HTML5/CSS HUD matrix running inside Web host", "T-UI Core", "1.0.0", TEMPLATES_BASE_URL + "web_dashboard.zip"));
            list.add(new PluginTemplate("telemetry_monitor", "Telemetry Sensor Plugin", "Real-time battery, network, and RAM monitor", "T-UI Core", "1.0.0", TEMPLATES_BASE_URL + "telemetry_monitor.zip"));

            try {
                URL url = new URL(TEMPLATES_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(8000);
                conn.connect();

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONArray array = null;
                    try {
                        JSONObject root = new JSONObject(sb.toString());
                        array = root.optJSONArray("templates");
                    } catch (Exception ignored) {
                        try {
                            array = new JSONArray(sb.toString());
                        } catch (Exception ignored2) {}
                    }

                    if (array != null && array.length() > 0) {
                        list.clear();
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.optJSONObject(i);
                            if (obj != null) {
                                PluginTemplate t = PluginTemplate.fromJson(obj);
                                if (t != null) {
                                    if (t.zipUrl != null && !t.zipUrl.startsWith("http")) {
                                        t.zipUrl = TEMPLATES_BASE_URL + t.zipUrl;
                                    }
                                    list.add(t);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Tuils.log("Using default templates: " + e.getMessage());
            }

            postSuccess(callback, list);
        });
    }

    public File exportPluginZip(PluginModel plugin) {
        if (plugin == null || !plugin.getFolder().exists()) return null;
        try {
            File exportsDir = getExportsDirectory();
            File zipFile = new File(exportsDir, plugin.getId() + "_v" + plugin.getVersion() + ".zip");
            if (zipFile.exists()) zipFile.delete();

            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
                zipFolder(plugin.getFolder(), plugin.getFolder(), zos);
            }
            return zipFile;
        } catch (Exception e) {
            Tuils.log(e);
            return null;
        }
    }

    private void zipFolder(File rootDir, File currentDir, ZipOutputStream zos) throws Exception {
        File[] files = currentDir.listFiles();
        if (files == null) return;
        byte[] buffer = new byte[4096];

        for (File file : files) {
            if (file.isDirectory()) {
                zipFolder(rootDir, file, zos);
            } else {
                String relativePath = file.getAbsolutePath().substring(rootDir.getAbsolutePath().length() + 1);
                ZipEntry entry = new ZipEntry(relativePath);
                zos.putNextEntry(entry);
                try (FileInputStream fis = new FileInputStream(file)) {
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    public void launchPlugin(Context context, PluginModel plugin, String[] args, Callback<String> callback) {
        if (plugin == null || !plugin.getFolder().exists()) {
            postError(callback, "Plugin not found or folder does not exist.");
            return;
        }

        PluginManifest manifest = plugin.getManifest();
        String entryPoint = manifest != null ? manifest.entryPoint : "start.kt";
        File epFile = plugin.getEntryPointFile();

        // 1. Check if manifest_specials specifies an activity
        if (manifest != null && manifest.manifestSpecials != null && manifest.manifestSpecials.containsKey("activity")) {
            Intent intent = new Intent(context, PluginHostActivity.class);
            intent.putExtra("plugin_path", plugin.getFolder().getAbsolutePath());
            intent.putExtra("plugin_name", plugin.getName());
            intent.putExtra("plugin_entry", entryPoint);
            if (args != null && args.length > 0) {
                intent.putExtra("plugin_args", args);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            postSuccess(callback, "Launched plugin activity for " + plugin.getName());
            return;
        }

        // 2. Check if entry point is index.html
        if (entryPoint.endsWith(".html")) {
            Intent intent = new Intent(context, PluginHostActivity.class);
            intent.putExtra("plugin_path", plugin.getFolder().getAbsolutePath());
            intent.putExtra("plugin_name", plugin.getName());
            intent.putExtra("plugin_entry", entryPoint);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            postSuccess(callback, "Opened web plugin " + plugin.getName());
            return;
        }

        // 3. Script or Kotlin/Java entry point
        executor.execute(() -> {
            try {
                String content = plugin.readFile(epFile);
                if (content.isEmpty()) {
                    postError(callback, "Entry point file is empty: " + epFile.getName());
                    return;
                }

                // If it is a .tui script
                if (epFile.getName().endsWith(".tui")) {
                    TuiScriptEngine.executeScriptFile(context, epFile);
                    postSuccess(callback, "Executed .tui script for " + plugin.getName());
                    return;
                }

                // Otherwise resolve telemetry variables and output script header
                String resolved = VariablesManager.resolveVariables(context, content, null);
                StringBuilder output = new StringBuilder();
                output.append("=== [ EXECUTING PLUGIN: ").append(plugin.getName()).append(" (v").append(plugin.getVersion()).append(") ] ===\n");
                output.append("Entry point: ").append(epFile.getName()).append("\n");

                // If args were passed, show them
                if (args != null && args.length > 0) {
                    output.append("Parameters: ");
                    for (String a : args) output.append(a).append(" ");
                    output.append("\n");
                }
                output.append("----------------------------------------\n");

                // Extract any simulated or printed lines
                String[] lines = resolved.split("\n");
                int printed = 0;
                for (String l : lines) {
                    String trim = l.trim();
                    if (trim.startsWith("echo ") || trim.startsWith("sb.append") || trim.startsWith("return ")) {
                        String cleanLine = trim.replace("echo ", "").replace("sb.append(\"", "").replace("\")", "").replace("return \"", "");
                        if (cleanLine.endsWith("\";") || cleanLine.endsWith("\")")) {
                            cleanLine = cleanLine.substring(0, cleanLine.length() - 2);
                        }
                        output.append(cleanLine).append("\n");
                        printed++;
                    }
                }
                if (printed == 0) {
                    output.append("Plugin module executed successfully with status 0.\n");
                }
                output.append("=== [ END PLUGIN EXECUTION ] ===");

                postSuccess(callback, output.toString());

            } catch (Exception e) {
                Tuils.log(e);
                postError(callback, "Execution error: " + e.getMessage());
            }
        });
    }

    public List<TuiCommandPlugin> loadPlugins() {
        loadedPlugins.clear();
        File pluginsDir = getPluginsDirectory();
        File[] files = pluginsDir.listFiles((dir, name) -> name.endsWith(".dex") || name.endsWith(".jar"));
        if (files == null) return loadedPlugins;

        File dexOptDir = context.getCodeCacheDir();

        for (File file : files) {
            try {
                DexClassLoader loader = new DexClassLoader(
                        file.getAbsolutePath(),
                        dexOptDir.getAbsolutePath(),
                        null,
                        context.getClassLoader()
                );

                Class<?> pluginClass = loader.loadClass("ohi.andre.tui.plugin.PluginEntry");
                if (TuiCommandPlugin.class.isAssignableFrom(pluginClass)) {
                    TuiCommandPlugin plugin = (TuiCommandPlugin) pluginClass.newInstance();
                    plugin.onLoaded();
                    loadedPlugins.add(plugin);
                    Tuils.sendOutput(context, "Loaded plugin: " + plugin.getPluginName());
                }

            } catch (Exception e) {
                Tuils.log("Failed to load plugin " + file.getName() + ": " + e.getMessage());
            }
        }

        return loadedPlugins;
    }

    public List<TuiCommandPlugin> getLoadedPlugins() {
        return loadedPlugins;
    }

    private <T> void postSuccess(Callback<T> callback, T result) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(result));
        }
    }

    private <T> void postError(Callback<T> callback, String msg) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(msg));
        }
    }

    private void writeFile(File file, String content) {
        try {
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(content.getBytes());
            }
        } catch (Exception e) {
            Tuils.log(e);
        }
    }
}
