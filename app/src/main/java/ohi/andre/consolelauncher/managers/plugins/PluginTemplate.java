package ohi.andre.consolelauncher.managers.plugins;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

public class PluginTemplate {

    public String id;
    public String name;
    public String description;
    public String author;
    public String version;
    public String zipUrl;
    public Map<String, String> files = new LinkedHashMap<>();

    public PluginTemplate(String id, String name, String description, String author, String version, String zipUrl) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.author = author;
        this.version = version;
        this.zipUrl = zipUrl;
    }

    public static PluginTemplate fromJson(JSONObject obj) {
        if (obj == null) return null;
        String id = obj.optString("id", obj.optString("name", "template")).toLowerCase().replace(" ", "_");
        String name = obj.optString("name", "Unnamed Template");
        String desc = obj.optString("description", "");
        String author = obj.optString("author", "T-UI Community");
        String ver = obj.optString("version", "1.0.0");
        String zip = obj.optString("zip_url", obj.optString("url", ""));
        return new PluginTemplate(id, name, desc, author, ver, zip);
    }
}
