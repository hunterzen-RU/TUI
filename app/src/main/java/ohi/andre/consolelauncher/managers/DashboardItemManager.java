package ohi.andre.consolelauncher.managers;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import ohi.andre.consolelauncher.tuils.Tuils;

public class DashboardItemManager {

    public static final String FILE_NAME = "dashboard-item.xml";
    public static final String ALT_FILE_NAME = "dashboard_item.xml";

    private static DashboardItemManager instance;
    private final List<DashboardItem> items = new ArrayList<>();
    private boolean loaded = false;

    private DashboardItemManager() {}

    public static synchronized DashboardItemManager getInstance() {
        if (instance == null) {
            instance = new DashboardItemManager();
        }
        return instance;
    }

    public synchronized List<DashboardItem> getItems() {
        if (!loaded) {
            loadFromFile();
        }
        return new ArrayList<>(items);
    }

    public synchronized DashboardItem getItem(int index) {
        if (!loaded) {
            loadFromFile();
        }
        for (DashboardItem item : items) {
            if (item.getIndex() == index) {
                return item;
            }
        }
        if (index > 0 && index <= items.size()) {
            return items.get(index - 1);
        }
        // Fallback default item
        String type = getDefaultTypeForIndex(index);
        return new DashboardItem(index, "box" + index, type, DashboardItem.getDefaultTitleForType(type), DashboardItem.getDefaultColorForType(type), "", shouldTypeHaveBar(type), "", "");
    }

    public synchronized void setItem(int index, String type, String title, String color, String format, boolean showBar, String var, String text) {
        if (!loaded) {
            loadFromFile();
        }
        DashboardItem target = null;
        for (DashboardItem item : items) {
            if (item.getIndex() == index) {
                target = item;
                break;
            }
        }
        if (target != null) {
            if (type != null && !type.isEmpty()) target.setType(type);
            if (title != null && !title.isEmpty()) target.setTitle(title);
            if (color != null && !color.isEmpty()) target.setColor(color);
            if (format != null) target.setFormat(format);
            target.setShowBar(showBar);
            if (var != null) target.setVariableName(var);
            if (text != null) target.setCustomText(text);
        } else {
            DashboardItem newItem = new DashboardItem(index, "box" + index, type, title, color, format, showBar, var, text);
            items.add(newItem);
            sortItems();
        }
        saveToFile();
    }

    public synchronized void addItem(DashboardItem item) {
        if (!loaded) {
            loadFromFile();
        }
        if (item.getIndex() <= 0) {
            item.setIndex(items.size() + 1);
        }
        items.add(item);
        sortItems();
        saveToFile();
    }

    public synchronized boolean removeItem(int index) {
        if (!loaded) {
            loadFromFile();
        }
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getIndex() == index) {
                items.remove(i);
                // Re-index remaining boxes to keep continuous box1 to boxN
                for (int j = 0; j < items.size(); j++) {
                    items.get(j).setIndex(j + 1);
                }
                saveToFile();
                return true;
            }
        }
        return false;
    }

    public synchronized void resetDefaults() {
        items.clear();
        createDefaultItems();
        saveToFile();
    }

    private void sortItems() {
        Collections.sort(items, new Comparator<DashboardItem>() {
            @Override
            public int compare(DashboardItem o1, DashboardItem o2) {
                return Integer.compare(o1.getIndex(), o2.getIndex());
            }
        });
    }

    public synchronized void loadFromFile() {
        items.clear();
        File folder = Tuils.getFolder();
        if (folder == null) {
            createDefaultItems();
            loaded = true;
            return;
        }

        File file = new File(folder, FILE_NAME);
        if (!file.exists()) {
            file = new File(folder, ALT_FILE_NAME);
        }

        if (!file.exists()) {
            createDefaultItems();
            saveToFile();
            loaded = true;
            return;
        }

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(file);
            doc.getDocumentElement().normalize();

            NodeList childNodes = doc.getDocumentElement().getChildNodes();
            int autoIndex = 1;

            for (int i = 0; i < childNodes.getLength(); i++) {
                Node node = childNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element elem = (Element) node;
                    String nodeName = elem.getNodeName();

                    int index = -1;
                    if (nodeName.toLowerCase().startsWith("box")) {
                        try {
                            index = Integer.parseInt(nodeName.substring(3).trim());
                        } catch (Exception ignored) {}
                    }
                    if (index <= 0 && elem.hasAttribute("index")) {
                        try {
                            index = Integer.parseInt(elem.getAttribute("index"));
                        } catch (Exception ignored) {}
                    }
                    if (index <= 0 && elem.hasAttribute("order")) {
                        try {
                            index = Integer.parseInt(elem.getAttribute("order"));
                        } catch (Exception ignored) {}
                    }
                    if (index <= 0 && elem.hasAttribute("id") && elem.getAttribute("id").toLowerCase().startsWith("box")) {
                        try {
                            index = Integer.parseInt(elem.getAttribute("id").substring(3).trim());
                        } catch (Exception ignored) {}
                    }
                    if (index <= 0) {
                        index = autoIndex;
                    }
                    autoIndex = Math.max(autoIndex, index + 1);

                    String id = elem.hasAttribute("id") ? elem.getAttribute("id") : "box" + index;
                    String type = elem.hasAttribute("type") ? elem.getAttribute("type") : getDefaultTypeForIndex(index);
                    String title = elem.hasAttribute("title") ? elem.getAttribute("title") : "";
                    String color = elem.hasAttribute("color") ? elem.getAttribute("color") : "";
                    String format = elem.hasAttribute("format") ? elem.getAttribute("format") : "";

                    boolean showBar = shouldTypeHaveBar(type);
                    if (elem.hasAttribute("bar")) {
                        showBar = Boolean.parseBoolean(elem.getAttribute("bar"));
                    }

                    String var = elem.hasAttribute("var") ? elem.getAttribute("var") : "";
                    String text = elem.hasAttribute("text") ? elem.getAttribute("text") : "";

                    DashboardItem item = new DashboardItem(index, id, type, title, color, format, showBar, var, text);
                    items.add(item);
                }
            }

            if (items.isEmpty()) {
                createDefaultItems();
                saveToFile();
            } else {
                sortItems();
            }

        } catch (Exception e) {
            Tuils.log(e);
            if (items.isEmpty()) {
                createDefaultItems();
            }
        }

        loaded = true;
    }

    public synchronized void saveToFile() {
        File folder = Tuils.getFolder();
        if (folder == null) return;

        try {
            File mainFile = new File(folder, FILE_NAME);
            File altFile = new File(folder, ALT_FILE_NAME);

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.newDocument();

            Element rootElement = doc.createElement("dashboard-items");
            doc.appendChild(rootElement);

            for (DashboardItem item : items) {
                String tagName = "box" + item.getIndex();
                Element elem = doc.createElement(tagName);
                elem.setAttribute("type", item.getType());
                elem.setAttribute("title", item.getTitle());
                elem.setAttribute("color", item.getColor());
                elem.setAttribute("bar", String.valueOf(item.isShowBar()));

                if (item.getFormat() != null && !item.getFormat().isEmpty()) {
                    elem.setAttribute("format", item.getFormat());
                }
                if (item.getVariableName() != null && !item.getVariableName().isEmpty()) {
                    elem.setAttribute("var", item.getVariableName());
                }
                if (item.getCustomText() != null && !item.getCustomText().isEmpty()) {
                    elem.setAttribute("text", item.getCustomText());
                }

                rootElement.appendChild(elem);
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            DOMSource source = new DOMSource(doc);
            StreamResult resultMain = new StreamResult(mainFile);
            transformer.transform(source, resultMain);

            // Also mirror to altFile (dashboard_item.xml) for user convenience
            StreamResult resultAlt = new StreamResult(altFile);
            transformer.transform(source, resultAlt);

        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    private void createDefaultItems() {
        items.clear();
        // 25 default boxes for arrangements from 1x1 up to 5x5 grid
        addDefault(1, "cpu", "[ CPU CORE LOAD ]", "#00FF66", "%v%", true, "", "");
        addDefault(2, "ram", "[ RAM MEM ALLOC ]", "#FFD700", "%used / %total", true, "", "");
        addDefault(3, "battery", "[ BATTERY CELL ]", "#FF5555", "%v%", true, "", "");
        addDefault(4, "storage", "[ DISK STORAGE ]", "#00FFFF", "%used / %total", true, "", "");
        addDefault(5, "network", "[ NETWORK NET ]", "#FF00FF", "%type", false, "", "");
        addDefault(6, "uptime", "[ SYS UPTIME ]", "#00FF66", "%v", true, "", "");
        addDefault(7, "device", "[ DEVICE MODEL ]", "#00FFFF", "%v", false, "", "");
        addDefault(8, "thermal", "[ THERMAL SENS ]", "#FF5555", "%v", true, "", "");
        addDefault(9, "display", "[ DISPLAY MATRIX ]", "#FFD700", "%v", false, "", "");
        addDefault(10, "kernel", "[ SHELL KERNEL ]", "#00FF66", "%v", false, "", "");
        addDefault(11, "time", "[ LOCAL TIME ]", "#00FFFF", "%v", false, "", "");
        addDefault(12, "date", "[ SYSTEM DATE ]", "#FFD700", "%v", false, "", "");
        addDefault(13, "network_download", "[ NET DOWNLOAD ]", "#00FFFF", "%v", false, "", "");
        addDefault(14, "network_upload", "[ NET UPLOAD ]", "#FF00FF", "%v", false, "", "");
        addDefault(15, "variable", "[ USER IDENTITY ]", "#00FF66", "{{user}}", false, "user", "");
        addDefault(16, "custom", "[ STATUS NODE ]", "#00FF66", "ONLINE", false, "", "ONLINE");
        addDefault(17, "variable", "[ NETWORK AP ]", "#FF00FF", "{{network_name}}", false, "network_name", "");
        addDefault(18, "variable", "[ OS PLATFORM ]", "#00FFFF", "{{os_version}}", false, "os_version", "");
        addDefault(19, "cpu", "[ CPU FREQ ]", "#00FF66", "%v%", true, "", "");
        addDefault(20, "ram", "[ RAM FREE ]", "#FFD700", "%avail", true, "", "");
        addDefault(21, "storage", "[ DISK AVAIL ]", "#00FFFF", "%avail", true, "", "");
        addDefault(22, "battery", "[ POWER CELL ]", "#FF5555", "%v%", true, "", "");
        addDefault(23, "time", "[ UTC CLOCK ]", "#00FFFF", "%v", false, "", "");
        addDefault(24, "uptime", "[ SYSTEM ACTIVE ]", "#00FF66", "%v", true, "", "");
        addDefault(25, "custom", "[ SYSTEM MATRIX ]", "#00FF66", "READY", false, "", "READY");
    }

    private void addDefault(int index, String type, String title, String color, String format, boolean showBar, String var, String text) {
        items.add(new DashboardItem(index, "box" + index, type, title, color, format, showBar, var, text));
    }

    private String getDefaultTypeForIndex(int index) {
        int idx = (index - 1) % 10;
        switch (idx) {
            case 0: return "cpu";
            case 1: return "ram";
            case 2: return "battery";
            case 3: return "storage";
            case 4: return "network";
            case 5: return "uptime";
            case 6: return "device";
            case 7: return "thermal";
            case 8: return "display";
            case 9: return "kernel";
            default: return "cpu";
        }
    }

    private boolean shouldTypeHaveBar(String type) {
        if (type == null) return false;
        switch (type.toLowerCase()) {
            case "cpu":
            case "ram":
            case "battery":
            case "storage":
            case "uptime":
            case "thermal":
                return true;
            default:
                return false;
        }
    }
}
