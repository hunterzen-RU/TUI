package ohi.andre.consolelauncher.managers.plugins;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ohi.andre.consolelauncher.tuils.Tuils;

public class PluginManifest {

    public static final String FILE_NAME = "manifest.json";

    public String name = "New Plugin";
    public String id = "new_plugin";
    public String version = "1.0.0";
    public String targetAppVersion = "1.0";
    public String description = "T-UI Plugin Module";
    public String entryPoint = "start.kt";
    public Map<String, String> tree = new LinkedHashMap<>();
    public List<String> permissions = new ArrayList<>();
    public Map<String, Object> manifestSpecials = new LinkedHashMap<>();

    public PluginManifest() {
        // Default tree structure
        tree.put("manifest.json", "Plugin configuration and metadata");
        tree.put("start.kt", "Kotlin script entry point");
        tree.put("plugin_layout.xml", "UI layout definition");
        tree.put("icon.png", "Plugin icon");
    }

    public static PluginManifest fromJson(String jsonStr, File pluginFolder) {
        PluginManifest manifest = new PluginManifest();
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            manifest.deriveDefaults(pluginFolder);
            return manifest;
        }

        try {
            JSONObject obj = new JSONObject(jsonStr);
            manifest.name = obj.optString("name", obj.optString("plugin_name", "Unnamed Plugin"));
            manifest.id = obj.optString("id", pluginFolder != null ? pluginFolder.getName() : "plugin");
            manifest.version = obj.optString("version", "1.0.0");
            manifest.targetAppVersion = obj.optString("target_app_version", "1.0");
            manifest.description = obj.optString("description", "");

            // Entry point defaults to start.kt, start.java, or index.html
            manifest.entryPoint = obj.optString("entry_point", obj.optString("plugin_entry_point", ""));
            if (manifest.entryPoint.isEmpty() && pluginFolder != null) {
                manifest.deriveEntryPoint(pluginFolder);
            }
            if (manifest.entryPoint.isEmpty()) {
                manifest.entryPoint = "start.kt";
            }

            // Tree structure
            manifest.tree.clear();
            if (obj.has("tree")) {
                JSONObject treeObj = obj.optJSONObject("tree");
                if (treeObj != null) {
                    Iterator<String> keys = treeObj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        manifest.tree.put(key, treeObj.optString(key));
                    }
                } else {
                    JSONArray treeArr = obj.optJSONArray("tree");
                    if (treeArr != null) {
                        for (int i = 0; i < treeArr.length(); i++) {
                            manifest.tree.put(treeArr.optString(i), "");
                        }
                    }
                }
            }

            // Permissions
            manifest.permissions.clear();
            if (obj.has("permissions")) {
                JSONArray permArr = obj.optJSONArray("permissions");
                if (permArr != null) {
                    for (int i = 0; i < permArr.length(); i++) {
                        manifest.permissions.add(permArr.optString(i));
                    }
                }
            }

            // Manifest specials: intent-filter, activity, service, receivers, providers, etc.
            manifest.manifestSpecials.clear();
            JSONObject specials = obj.optJSONObject("manifest_specials");
            if (specials == null) specials = obj.optJSONObject("specials");
            if (specials != null) {
                Iterator<String> keys = specials.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    manifest.manifestSpecials.put(k, specials.opt(k));
                }
            }

        } catch (Exception e) {
            Tuils.log(e);
            manifest.deriveDefaults(pluginFolder);
        }

        return manifest;
    }

    public static String toTitleCase(String str) {
        if (str == null || str.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String s : str.split("\\s+")) {
            if (!s.isEmpty()) {
                sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1).toLowerCase()).append(" ");
            }
        }
        return sb.toString().trim();
    }

    public void deriveDefaults(File pluginFolder) {
        if (pluginFolder != null) {
            this.id = pluginFolder.getName();
            this.name = toTitleCase(this.id.replace('_', ' ').replace('-', ' '));
            deriveEntryPoint(pluginFolder);
        }
    }

    public void deriveEntryPoint(File pluginFolder) {
        if (pluginFolder == null || !pluginFolder.exists()) return;
        if (new File(pluginFolder, "start.kt").exists()) {
            this.entryPoint = "start.kt";
        } else if (new File(pluginFolder, "start.java").exists()) {
            this.entryPoint = "start.java";
        } else if (new File(pluginFolder, "index.html").exists()) {
            this.entryPoint = "index.html";
        } else if (new File(pluginFolder, "start.tui").exists()) {
            this.entryPoint = "start.tui";
        }
    }

    public JSONObject toJsonObject() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("name", name);
            obj.put("id", id);
            obj.put("version", version);
            obj.put("target_app_version", targetAppVersion);
            obj.put("description", description);
            obj.put("entry_point", entryPoint);

            JSONObject treeObj = new JSONObject();
            for (Map.Entry<String, String> entry : tree.entrySet()) {
                treeObj.put(entry.getKey(), entry.getValue());
            }
            obj.put("tree", treeObj);

            JSONArray permArr = new JSONArray();
            for (String p : permissions) {
                permArr.put(p);
            }
            obj.put("permissions", permArr);

            JSONObject specialsObj = new JSONObject();
            for (Map.Entry<String, Object> entry : manifestSpecials.entrySet()) {
                specialsObj.put(entry.getKey(), entry.getValue());
            }
            obj.put("manifest_specials", specialsObj);

        } catch (JSONException e) {
            Tuils.log(e);
        }
        return obj;
    }

    public String toJsonString() {
        try {
            return toJsonObject().toString(4);
        } catch (JSONException e) {
            return toJsonObject().toString();
        }
    }
}
