package ohi.andre.consolelauncher.managers;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.SystemClock;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.Tuils;

public class VariablesManager {

    public static final String FILE_NAME = "variables.xml";
    public static final String ALT_FILE_NAME = "variable.xml";

    public static class PredefinedVar {
        public final String name;
        public final String placeholder;
        public final String type;
        public final String description;

        public PredefinedVar(String name, String placeholder, String type, String description) {
            this.name = name;
            this.placeholder = placeholder;
            this.type = type;
            this.description = description;
        }
    }

    public static final List<PredefinedVar> PREDEFINED_VARIABLES = new ArrayList<>();
    static {
        PREDEFINED_VARIABLES.add(new PredefinedVar("cpu_usage", "{{cpu_usage}}", "INTEGER", "Current CPU load percentage"));
        PREDEFINED_VARIABLES.add(new PredefinedVar("ram_total", "{{ram_total}}", "STRING", "Total installed RAM (GB)"));
        PREDEFINED_VARIABLES.add(new PredefinedVar("ram_avail", "{{ram_avail}}", "STRING", "Currently available RAM (GB)"));
        PREDEFINED_VARIABLES.add(new PredefinedVar("ram_used", "{{ram_used}}", "STRING", "Currently used RAM (GB)"));
        PREDEFINED_VARIABLES.add(new PredefinedVar("battery", "{{battery}}", "INTEGER", "Current battery level (%)"));
        PREDEFINED_VARIABLES.add(new PredefinedVar("battery_level", "{{battery_level}}", "INTEGER", "Battery charge level percentage"));
        PREDEFINED_VARIABLES.add(new PredefinedVar("battery_status", "{{battery_status}}", "STRING", "Battery charging status (Charging/Discharging)"));
        PREDEFINED_VARIABLES.add(new PredefinedVar("storage_total", "{{storage_total}}", "STRING", "Total internal storage (GB)"));
        PREDEFINED_VARIABLES.add(new PredefinedVar("storage_avail", "{{storage_avail}}", "STRING", "Available internal storage (GB)"));
        PREDEFINED_VARIABLES.add(new PredefinedVar("uptime", "{{uptime}}", "INTEGER", "System uptime in seconds"));
        PREDEFINED_VARIABLES.add(new PredefinedVar("device", "{{device}}", "STRING", "Device model name"));
        PREDEFINED_VARIABLES.add(new PredefinedVar("user", "{{user}}", "STRING", "T-UI username (from ui.xml)"));
        PREDEFINED_VARIABLES.add(new PredefinedVar("os_version", "{{os_version}}", "STRING", "Android version"));
        PREDEFINED_VARIABLES.add(new PredefinedVar("network", "{{network}}", "STRING", "Active network type (WIFI/MOBILE/OFFLINE)"));
        PREDEFINED_VARIABLES.add(new PredefinedVar("network_name", "{{network_name}}", "STRING", "SSID (WiFi) or Subtype (Mobile)"));
        PREDEFINED_VARIABLES.add(new PredefinedVar("network_upload", "{{network_upload}}", "STRING", "Total data uploaded since boot"));
        PREDEFINED_VARIABLES.add(new PredefinedVar("network_download", "{{network_download}}", "STRING", "Total data downloaded since boot"));
        PREDEFINED_VARIABLES.add(new PredefinedVar("date", "{{date}}", "STRING", "Current date (YYYY-MM-DD)"));
        PREDEFINED_VARIABLES.add(new PredefinedVar("time", "{{time}}", "STRING", "Current time (HH:mm:ss)"));
    }

    private static final Map<String, String> cachedVars = new ConcurrentHashMap<>();
    private static final Map<String, String> cachedDescriptions = new ConcurrentHashMap<>();
    private static boolean loaded = false;

    public static String detectType(String val) {
        if (val == null) return "STRING";
        String trimmed = val.trim();
        if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
            return "BOOLEAN";
        }
        if (trimmed.equals("0") || trimmed.equals("1")) {
            return "BOOLEAN";
        }
        if (trimmed.equals("00") || trimmed.equals("01")) {
            return "INTEGER";
        }
        if (trimmed.matches("-?\\d+")) {
            return "INTEGER";
        }
        if (trimmed.matches("-?\\d+\\.\\d+")) {
            return "FLOAT";
        }
        if (trimmed.length() == 1) {
            return "CHAR";
        }
        return "STRING";
    }

    public static boolean isPredefined(String name) {
        if (name == null) return false;
        String clean = name.trim();
        for (PredefinedVar pv : PREDEFINED_VARIABLES) {
            if (pv.name.equalsIgnoreCase(clean)) return true;
        }
        return false;
    }

    public static String getDefaultDescription(String name) {
        if (name == null) return "";
        String clean = name.trim();
        for (PredefinedVar pv : PREDEFINED_VARIABLES) {
            if (pv.name.equalsIgnoreCase(clean)) return pv.description;
        }
        return "";
    }

    public static synchronized void loadFromFile() {
        cachedVars.clear();
        cachedDescriptions.clear();

        File folder = Tuils.getFolder();
        if (folder == null) {
            prefillDefaults();
            loaded = true;
            return;
        }

        File mainFile = new File(folder, FILE_NAME);
        File altFile = new File(folder, ALT_FILE_NAME);

        File fileToRead = null;
        if (altFile.exists()) {
            fileToRead = altFile;
        } else if (mainFile.exists()) {
            fileToRead = mainFile;
        }

        if (fileToRead != null && fileToRead.exists()) {
            try {
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(fileToRead);
                doc.getDocumentElement().normalize();

                NodeList nList = doc.getElementsByTagName("entry");
                for (int temp = 0; temp < nList.getLength(); temp++) {
                    Node nNode = nList.item(temp);
                    if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element eElement = (Element) nNode;
                        String key = eElement.getAttribute("name");
                        String value = eElement.getAttribute("value");
                        String desc = eElement.getAttribute("description");
                        if (key != null && !key.isEmpty()) {
                            cachedVars.put(key, value != null ? value : "");
                            if (desc != null && !desc.isEmpty()) {
                                cachedDescriptions.put(key, desc);
                            } else if (isPredefined(key)) {
                                cachedDescriptions.put(key, getDefaultDescription(key));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Tuils.log(e);
            }
        }

        // Ensure all predefined variables are present with short descriptions
        boolean addedPredefined = false;
        for (PredefinedVar pv : PREDEFINED_VARIABLES) {
            if (!cachedVars.containsKey(pv.name)) {
                cachedVars.put(pv.name, pv.placeholder);
                cachedDescriptions.put(pv.name, pv.description);
                addedPredefined = true;
            } else if (!cachedDescriptions.containsKey(pv.name) || cachedDescriptions.get(pv.name).isEmpty()) {
                cachedDescriptions.put(pv.name, pv.description);
                addedPredefined = true;
            }
        }

        loaded = true;

        if (fileToRead == null || !fileToRead.exists() || addedPredefined) {
            saveToFile();
        }
    }

    private static void prefillDefaults() {
        for (PredefinedVar pv : PREDEFINED_VARIABLES) {
            cachedVars.put(pv.name, pv.placeholder);
            cachedDescriptions.put(pv.name, pv.description);
        }
    }

    public static synchronized void saveToFile() {
        File folder = Tuils.getFolder();
        if (folder == null) return;

        try {
            File mainFile = new File(folder, FILE_NAME);
            File altFile = new File(folder, ALT_FILE_NAME);

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.newDocument();

            Element rootElement = doc.createElement("variables");
            doc.appendChild(rootElement);

            // First write predefined variables in ordered sequence
            for (PredefinedVar pv : PREDEFINED_VARIABLES) {
                Element entryElem = doc.createElement("entry");
                entryElem.setAttribute("name", pv.name);
                String val = cachedVars.getOrDefault(pv.name, pv.placeholder);
                entryElem.setAttribute("value", val);
                entryElem.setAttribute("type", pv.type);
                String desc = cachedDescriptions.getOrDefault(pv.name, pv.description);
                entryElem.setAttribute("description", desc);
                rootElement.appendChild(entryElem);
            }

            // Next write user-defined variables
            for (Map.Entry<String, String> entry : cachedVars.entrySet()) {
                if (isPredefined(entry.getKey())) continue;
                Element entryElem = doc.createElement("entry");
                entryElem.setAttribute("name", entry.getKey());
                entryElem.setAttribute("value", entry.getValue());
                entryElem.setAttribute("type", detectType(entry.getValue()));
                String desc = cachedDescriptions.getOrDefault(entry.getKey(), "");
                if (!desc.isEmpty()) {
                    entryElem.setAttribute("description", desc);
                }
                rootElement.appendChild(entryElem);
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            DOMSource source = new DOMSource(doc);
            StreamResult resultMain = new StreamResult(mainFile);
            transformer.transform(source, resultMain);

            // Mirror to variable.xml
            StreamResult resultAlt = new StreamResult(altFile);
            transformer.transform(source, resultAlt);

        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    public static void setVariable(String name, String value) {
        setVariable(name, value, null);
    }

    public static void setVariable(String name, String value, String description) {
        if (!loaded) loadFromFile();
        if (name == null || name.trim().isEmpty()) return;
        String cleanName = name.trim();
        cachedVars.put(cleanName, value != null ? value : "");
        if (description != null) {
            cachedDescriptions.put(cleanName, description.trim());
        } else if (!cachedDescriptions.containsKey(cleanName) && isPredefined(cleanName)) {
            cachedDescriptions.put(cleanName, getDefaultDescription(cleanName));
        }
        saveToFile();
    }

    public static void setDescription(String name, String description) {
        if (!loaded) loadFromFile();
        if (name == null || name.trim().isEmpty()) return;
        String cleanName = name.trim();
        cachedDescriptions.put(cleanName, description != null ? description.trim() : "");
        saveToFile();
    }

    public static String getDescription(String name) {
        if (!loaded) loadFromFile();
        if (name == null) return "";
        return cachedDescriptions.getOrDefault(name.trim(), getDefaultDescription(name.trim()));
    }

    public static String getVariable(String name) {
        if (!loaded) loadFromFile();
        if (name == null) return "";
        return cachedVars.getOrDefault(name.trim(), "");
    }

    public static Map<String, String> getAllVariables() {
        if (!loaded) loadFromFile();
        return new LinkedHashMap<>(cachedVars);
    }

    public static Map<String, String> getAllDescriptions() {
        if (!loaded) loadFromFile();
        return new LinkedHashMap<>(cachedDescriptions);
    }

    public static String getLiveValue(Context context, String name) {
        if (name == null) return "";
        String clean = name.trim().toLowerCase();
        switch (clean) {
            case "date":
                return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            case "time":
                return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            case "battery":
            case "battery_level":
                if (context != null) {
                    try {
                        android.content.Intent batIntent = context.getApplicationContext().registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
                        int level = batIntent != null ? batIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
                        return String.valueOf(level);
                    } catch (Exception ignored) {}
                }
                return "0";
            case "battery_status":
                if (context != null) {
                    try {
                        android.content.Intent batIntent = context.getApplicationContext().registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
                        if (batIntent != null) {
                            int status = batIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                            if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                                return "Charging";
                            } else if (status == BatteryManager.BATTERY_STATUS_FULL) {
                                return "Full";
                            } else if (status == BatteryManager.BATTERY_STATUS_DISCHARGING) {
                                return "Discharging";
                            } else if (status == BatteryManager.BATTERY_STATUS_NOT_CHARGING) {
                                return "Not Charging";
                            }
                        }
                    } catch (Exception ignored) {}
                }
                return "Discharging";
            case "uptime":
                return String.valueOf(SystemClock.elapsedRealtime() / 1000);
            case "device":
                return Build.MODEL;
            case "user":
                String user = XMLPrefsManager.get(Ui.username);
                return user != null ? user : "user";
            case "os_version":
                return "Android " + Build.VERSION.RELEASE;
            case "cpu_usage":
                return String.valueOf(Tuils.getCpuUsage());
            case "ram_total":
                return Tuils.round(Tuils.totalRam() / 1024.0 / 1024.0, 1) + "G";
            case "ram_avail":
                if (context != null) {
                    try {
                        android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                        am.getMemoryInfo(mi);
                        return Tuils.round(mi.availMem / 1024.0 / 1024.0 / 1024.0, 1) + "G";
                    } catch (Exception ignored) {}
                }
                return "0G";
            case "ram_used":
                if (context != null) {
                    try {
                        android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                        am.getMemoryInfo(mi);
                        long total = Tuils.totalRam() * 1024;
                        long used = total - mi.availMem;
                        return Tuils.round(used / 1024.0 / 1024.0 / 1024.0, 1) + "G";
                    } catch (Exception ignored) {}
                }
                return "0G";
            case "storage_total":
                return (int) Tuils.getTotalInternalMemorySize(Tuils.GIGA) + "G";
            case "storage_avail":
                return (int) Tuils.getAvailableInternalMemorySize(Tuils.GIGA) + "G";
            case "network":
                if (context != null) {
                    try {
                        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                        return (activeNetwork != null && activeNetwork.isConnected()) ? activeNetwork.getTypeName() : "OFFLINE";
                    } catch (Exception ignored) {}
                }
                return "OFFLINE";
            case "network_name":
                if (context != null) {
                    try {
                        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                        if (activeNetwork != null && activeNetwork.isConnected()) {
                            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                                WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                                WifiInfo info = wifiManager.getConnectionInfo();
                                String s = info != null ? info.getSSID() : null;
                                if (s != null) return s.replace("\"", "");
                            } else {
                                return activeNetwork.getSubtypeName();
                            }
                        }
                    } catch (Exception ignored) {}
                }
                return "OFFLINE";
            case "network_upload":
                return Tuils.formatSize(TrafficStats.getTotalTxBytes(), Tuils.MEGA) + "MB";
            case "network_download":
                return Tuils.formatSize(TrafficStats.getTotalRxBytes(), Tuils.MEGA) + "MB";
            default:
                return "";
        }
    }

    private static String replaceVarTokens(String input, String name, String value) {
        if (input == null || name == null || value == null) return input;
        String res = input.replace("{{" + name + "}}", value);
        res = res.replace("$" + name, value);
        return res;
    }

    public static String resolveVariables(Context context, String input, Map<String, String> localVars) {
        if (input == null || input.isEmpty()) return input;
        String result = input;

        // 1. Dynamic system variables (both {{var}} and $var)
        if (result.contains("date")) {
            String val = getLiveValue(context, "date");
            result = replaceVarTokens(result, "date", val);
        }
        if (result.contains("time")) {
            String val = getLiveValue(context, "time");
            result = replaceVarTokens(result, "time", val);
        }
        if (result.contains("battery_level")) {
            String val = getLiveValue(context, "battery_level");
            result = replaceVarTokens(result, "battery_level", val);
        }
        if (result.contains("battery_status")) {
            String val = getLiveValue(context, "battery_status");
            result = replaceVarTokens(result, "battery_status", val);
        }
        if (result.contains("battery")) {
            String val = getLiveValue(context, "battery");
            result = replaceVarTokens(result, "battery", val);
        }
        if (result.contains("uptime")) {
            String val = getLiveValue(context, "uptime");
            result = replaceVarTokens(result, "uptime", val);
        }
        if (result.contains("device")) {
            String val = getLiveValue(context, "device");
            result = replaceVarTokens(result, "device", val);
        }
        if (result.contains("user")) {
            String val = getLiveValue(context, "user");
            result = replaceVarTokens(result, "user", val);
        }
        if (result.contains("os_version")) {
            String val = getLiveValue(context, "os_version");
            result = replaceVarTokens(result, "os_version", val);
        }
        if (result.contains("cpu_usage")) {
            String val = getLiveValue(context, "cpu_usage");
            result = replaceVarTokens(result, "cpu_usage", val);
        }
        if (result.contains("ram_total")) {
            String val = getLiveValue(context, "ram_total");
            result = replaceVarTokens(result, "ram_total", val);
        }
        if (result.contains("ram_avail")) {
            String val = getLiveValue(context, "ram_avail");
            result = replaceVarTokens(result, "ram_avail", val);
        }
        if (result.contains("ram_used")) {
            String val = getLiveValue(context, "ram_used");
            result = replaceVarTokens(result, "ram_used", val);
        }
        if (result.contains("storage_total")) {
            String val = getLiveValue(context, "storage_total");
            result = replaceVarTokens(result, "storage_total", val);
        }
        if (result.contains("storage_avail")) {
            String val = getLiveValue(context, "storage_avail");
            result = replaceVarTokens(result, "storage_avail", val);
        }
        if (result.contains("network_upload")) {
            String val = getLiveValue(context, "network_upload");
            result = replaceVarTokens(result, "network_upload", val);
        }
        if (result.contains("network_download")) {
            String val = getLiveValue(context, "network_download");
            result = replaceVarTokens(result, "network_download", val);
        }
        if (result.contains("network_name")) {
            String val = getLiveValue(context, "network_name");
            result = replaceVarTokens(result, "network_name", val);
        }
        if (result.contains("network")) {
            String val = getLiveValue(context, "network");
            result = replaceVarTokens(result, "network", val);
        }

        // 2. Local script variables
        if (localVars != null) {
            for (Map.Entry<String, String> entry : localVars.entrySet()) {
                result = replaceVarTokens(result, entry.getKey(), entry.getValue());
            }
        }

        // 3. Stored user variables from variable.xml / variables.xml
        if (!loaded) loadFromFile();
        for (Map.Entry<String, String> entry : cachedVars.entrySet()) {
            if (isPredefined(entry.getKey())) continue;
            String k = entry.getKey();
            String v = entry.getValue();
            if (v != null && (v.contains("{{") || v.contains("$"))) {
                v = resolveVariables(context, v, localVars);
            }
            result = replaceVarTokens(result, k, v);
        }

        return result;
    }
}
