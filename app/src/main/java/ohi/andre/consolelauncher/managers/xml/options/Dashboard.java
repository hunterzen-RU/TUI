package ohi.andre.consolelauncher.managers.xml.options;

import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;

public enum Dashboard implements XMLPrefsSave {
    dashboard_mode {
        @Override
        public String defaultValue() { return "grid"; }
        @Override
        public String type() { return XMLPrefsSave.TEXT; }
        @Override
        public String info() { return "Dashboard system mode: 'grid' (modern grid system) or 'native' (legacy list)."; }
    },
    dashboard_enabled {
        @Override
        public String defaultValue() { return "true"; }
        @Override
        public String type() { return XMLPrefsSave.BOOLEAN; }
        @Override
        public String info() { return "Enable or disable the dashboard entirely."; }
    },
    enable_blur_effect {
        @Override
        public String defaultValue() { return "true"; }
        @Override
        public String type() { return XMLPrefsSave.BOOLEAN; }
        @Override
        public String info() { return "Enables frosted glass blur overlay effect on the floating grid dashboard."; }
    },
    use_grid_dashboard {
        @Override
        public String defaultValue() { return "true"; }
        @Override
        public String type() { return XMLPrefsSave.BOOLEAN; }
        @Override
        public String info() { return "If true, enables the professional Grid System Dashboard floating above output."; }
    },
    blur_effect {
        @Override
        public String defaultValue() { return "true"; }
        @Override
        public String type() { return XMLPrefsSave.BOOLEAN; }
        @Override
        public String info() { return "Enables translucent frosted glass overlay effect on the floating grid dashboard."; }
    },
    dashboard_title {
        @Override
        public String defaultValue() { return "SYS_KERNEL_MATRIX"; }
        @Override
        public String type() { return XMLPrefsSave.TEXT; }
        @Override
        public String info() { return "Dashboard title banner"; }
    },
    grid_layout {
        @Override
        public String defaultValue() { return "2x2"; }
        @Override
        public String type() { return XMLPrefsSave.TEXT; }
        @Override
        public String info() { return "Grid layout configuration (e.g. 1x1, 2x2, 1x2, 3x3, 4x4, up to 5x5)"; }
    },
    grid_columns {
        @Override
        public String defaultValue() { return "2"; }
        @Override
        public String type() { return XMLPrefsSave.INTEGER; }
        @Override
        public String info() { return "Number of columns in dashboard grid (1-5)"; }
    },
    grid_rows {
        @Override
        public String defaultValue() { return "2"; }
        @Override
        public String type() { return XMLPrefsSave.INTEGER; }
        @Override
        public String info() { return "Number of rows in dashboard grid (1-5)"; }
    },
    box_bg_color {
        @Override
        public String defaultValue() { return "#00000000"; }
        @Override
        public String type() { return XMLPrefsSave.TEXT; }
        @Override
        public String info() { return "Background color for dashboard boxes (transparent by default)"; }
    },
    box_border_color {
        @Override
        public String defaultValue() { return "#00FF66"; }
        @Override
        public String type() { return XMLPrefsSave.TEXT; }
        @Override
        public String info() { return "Dashboard box border accent color"; }
    },
    cpu_color {
        @Override
        public String defaultValue() { return "#00FF66"; }
        @Override
        public String type() { return XMLPrefsSave.TEXT; }
        @Override
        public String info() { return "CPU box accent color"; }
    },
    ram_color {
        @Override
        public String defaultValue() { return "#FFD700"; }
        @Override
        public String type() { return XMLPrefsSave.TEXT; }
        @Override
        public String info() { return "RAM box accent color"; }
    },
    battery_color {
        @Override
        public String defaultValue() { return "#FF5555"; }
        @Override
        public String type() { return XMLPrefsSave.TEXT; }
        @Override
        public String info() { return "Battery box accent color"; }
    },
    storage_color {
        @Override
        public String defaultValue() { return "#00FFFF"; }
        @Override
        public String type() { return XMLPrefsSave.TEXT; }
        @Override
        public String info() { return "Storage box accent color"; }
    },
    net_color {
        @Override
        public String defaultValue() { return "#FF00FF"; }
        @Override
        public String type() { return XMLPrefsSave.TEXT; }
        @Override
        public String info() { return "Network box accent color"; }
    },
    uptime_color {
        @Override
        public String defaultValue() { return "#00FF66"; }
        @Override
        public String type() { return XMLPrefsSave.TEXT; }
        @Override
        public String info() { return "Uptime box accent color"; }
    },
    show_cpu_box {
        @Override
        public String defaultValue() { return "true"; }
        @Override
        public String type() { return XMLPrefsSave.BOOLEAN; }
        @Override
        public String info() { return "Show CPU usage monitor box"; }
    },
    show_ram_box {
        @Override
        public String defaultValue() { return "true"; }
        @Override
        public String type() { return XMLPrefsSave.BOOLEAN; }
        @Override
        public String info() { return "Show RAM availability box"; }
    },
    show_battery_box {
        @Override
        public String defaultValue() { return "true"; }
        @Override
        public String type() { return XMLPrefsSave.BOOLEAN; }
        @Override
        public String info() { return "Show battery level box"; }
    },
    show_storage_box {
        @Override
        public String defaultValue() { return "true"; }
        @Override
        public String type() { return XMLPrefsSave.BOOLEAN; }
        @Override
        public String info() { return "Show storage usage box"; }
    },
    show_net_box {
        @Override
        public String defaultValue() { return "true"; }
        @Override
        public String type() { return XMLPrefsSave.BOOLEAN; }
        @Override
        public String info() { return "Show network connection status box"; }
    },
    show_uptime_box {
        @Override
        public String defaultValue() { return "true"; }
        @Override
        public String type() { return XMLPrefsSave.BOOLEAN; }
        @Override
        public String info() { return "Show device uptime box"; }
    },
    bar_fill_char {
        @Override
        public String defaultValue() { return "█"; }
        @Override
        public String type() { return XMLPrefsSave.TEXT; }
        @Override
        public String info() { return "Terminal progress bar fill character"; }
    },
    bar_empty_char {
        @Override
        public String defaultValue() { return "░"; }
        @Override
        public String type() { return XMLPrefsSave.TEXT; }
        @Override
        public String info() { return "Terminal progress bar empty character"; }
    },
    refresh_interval_ms {
        @Override
        public String defaultValue() { return "1000"; }
        @Override
        public String type() { return XMLPrefsSave.INTEGER; }
        @Override
        public String info() { return "Dashboard real-time refresh interval in milliseconds"; }
    },
    long_press_action {
        @Override
        public String defaultValue() { return "ai_chat"; }
        @Override
        public String type() { return XMLPrefsSave.TEXT; }
        @Override
        public String info() { return "Action on dashboard long press: 'none', 'ai_chat' (AI chat with history), 'app_launcher' (Grid icon launcher)."; }
    };

    @Override
    public XMLPrefsElement parent() {
        return XMLPrefsManager.XMLPrefsRoot.DASHBOARD;
    }

    @Override
    public String label() {
        return name();
    }

    @Override
    public String[] invalidValues() {
        return null;
    }

    @Override
    public String getLowercaseString() {
        return label();
    }

    @Override
    public String getString() {
        return label();
    }
}
