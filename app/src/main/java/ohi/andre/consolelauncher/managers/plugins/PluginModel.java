package ohi.andre.consolelauncher.managers.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ohi.andre.consolelauncher.tuils.Tuils;

public class PluginModel {

    private final File folder;
    private PluginManifest manifest;

    public PluginModel(File folder) {
        this.folder = folder;
        loadManifest();
    }

    public void loadManifest() {
        File manifestFile = new File(folder, PluginManifest.FILE_NAME);
        if (manifestFile.exists()) {
            String json = readFile(manifestFile);
            this.manifest = PluginManifest.fromJson(json, folder);
        } else {
            this.manifest = PluginManifest.fromJson(null, folder);
        }
    }

    public void saveManifest() {
        if (manifest == null) return;
        File manifestFile = new File(folder, PluginManifest.FILE_NAME);
        writeFile(manifestFile, manifest.toJsonString());
    }

    public File getFolder() {
        return folder;
    }

    public String getId() {
        return manifest != null ? manifest.id : folder.getName();
    }

    public String getName() {
        return manifest != null ? manifest.name : folder.getName();
    }

    public String getVersion() {
        return manifest != null ? manifest.version : "1.0.0";
    }

    public String getDescription() {
        return manifest != null ? manifest.description : "";
    }

    public PluginManifest getManifest() {
        return manifest;
    }

    public void setManifest(PluginManifest manifest) {
        this.manifest = manifest;
    }

    public File getEntryPointFile() {
        String ep = manifest != null && manifest.entryPoint != null ? manifest.entryPoint : "start.kt";
        File f = new File(folder, ep);
        if (f.exists()) return f;

        // Fallbacks
        File kt = new File(folder, "start.kt");
        if (kt.exists()) return kt;
        File java = new File(folder, "start.java");
        if (java.exists()) return java;
        File html = new File(folder, "index.html");
        if (html.exists()) return html;
        File tui = new File(folder, "start.tui");
        if (tui.exists()) return tui;

        return f;
    }

    public File getIconFile() {
        File icon = new File(folder, "icon.png");
        if (icon.exists()) return icon;
        File iconJpg = new File(folder, "icon.jpg");
        if (iconJpg.exists()) return iconJpg;
        return icon;
    }

    public List<File> listFiles() {
        List<File> list = new ArrayList<>();
        collectFiles(folder, list);
        Collections.sort(list, (f1, f2) -> f1.getAbsolutePath().compareTo(f2.getAbsolutePath()));
        return list;
    }

    private void collectFiles(File dir, List<File> list) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            list.add(f);
            if (f.isDirectory()) {
                collectFiles(f, list);
            }
        }
    }

    public String readFile(File file) {
        if (file == null || !file.exists()) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            Tuils.log(e);
        }
        return sb.toString();
    }

    public boolean writeFile(File file, String content) {
        if (file == null) return false;
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(content.getBytes());
            }
            return true;
        } catch (Exception e) {
            Tuils.log(e);
            return false;
        }
    }

    public String getRelativePath(File file) {
        String base = folder.getAbsolutePath();
        String path = file.getAbsolutePath();
        if (path.startsWith(base)) {
            String rel = path.substring(base.length());
            if (rel.startsWith(File.separator)) rel = rel.substring(1);
            return rel;
        }
        return file.getName();
    }
}
