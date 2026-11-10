package ohi.andre.consolelauncher.managers;

public class DashboardItem {

    private int index;
    private String id;
    private String type;
    private String title;
    private String color;
    private String format;
    private boolean showBar;
    private String variableName;
    private String customText;

    public DashboardItem(int index, String id, String type, String title, String color, String format, boolean showBar, String variableName, String customText) {
        this.index = index;
        this.id = id != null && !id.isEmpty() ? id : "box" + index;
        this.type = type != null ? type.toLowerCase() : "cpu";
        this.title = title != null ? title : "";
        this.color = color != null ? color : "#00FF66";
        this.format = format != null ? format : "";
        this.showBar = showBar;
        this.variableName = variableName != null ? variableName : "";
        this.customText = customText != null ? customText : "";
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
        this.id = "box" + index;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type != null ? type.toLowerCase() : "cpu";
    }

    public String getTitle() {
        if (title != null && !title.isEmpty()) {
            return title;
        }
        return getDefaultTitleForType(type);
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getColor() {
        if (color != null && !color.isEmpty()) {
            return color;
        }
        return getDefaultColorForType(type);
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public boolean isShowBar() {
        return showBar;
    }

    public void setShowBar(boolean showBar) {
        this.showBar = showBar;
    }

    public String getVariableName() {
        return variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    public String getCustomText() {
        return customText;
    }

    public void setCustomText(String customText) {
        this.customText = customText;
    }

    public static String getDefaultTitleForType(String type) {
        if (type == null) return "[ CPU CORE LOAD ]";
        switch (type.toLowerCase()) {
            case "cpu": return "[ CPU CORE LOAD ]";
            case "ram": return "[ RAM MEM ALLOC ]";
            case "battery": return "[ BATTERY CELL ]";
            case "storage": return "[ DISK STORAGE ]";
            case "network": return "[ NETWORK NET ]";
            case "uptime": return "[ SYS UPTIME ]";
            case "device": return "[ DEVICE MODEL ]";
            case "thermal": return "[ THERMAL SENS ]";
            case "display": return "[ DISPLAY MATRIX ]";
            case "kernel": return "[ SHELL KERNEL ]";
            case "time": return "[ LOCAL TIME ]";
            case "date": return "[ SYSTEM DATE ]";
            case "network_download": return "[ NET DOWNLOAD ]";
            case "network_upload": return "[ NET UPLOAD ]";
            case "variable": return "[ SYSTEM VAR ]";
            case "custom": return "[ CUSTOM NODE ]";
            default: return "[ " + type.toUpperCase() + " ]";
        }
    }

    public static String getDefaultColorForType(String type) {
        if (type == null) return "#00FF66";
        switch (type.toLowerCase()) {
            case "cpu": return "#00FF66";
            case "ram": return "#FFD700";
            case "battery": return "#FF5555";
            case "storage": return "#00FFFF";
            case "network": return "#FF00FF";
            case "uptime": return "#00FF66";
            case "device": return "#00FFFF";
            case "thermal": return "#FF5555";
            case "display": return "#FFD700";
            case "kernel": return "#00FF66";
            case "time": return "#00FFFF";
            case "date": return "#FFD700";
            case "network_download": return "#00FFFF";
            case "network_upload": return "#FF00FF";
            case "variable": return "#00FF66";
            case "custom": return "#00FF66";
            default: return "#00FF66";
        }
    }
}
