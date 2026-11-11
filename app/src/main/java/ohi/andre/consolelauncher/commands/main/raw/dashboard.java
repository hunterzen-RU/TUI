package ohi.andre.consolelauncher.commands.main.raw;

import java.io.File;
import java.util.List;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.DashboardItem;
import ohi.andre.consolelauncher.managers.DashboardItemManager;
import ohi.andre.consolelauncher.managers.DashboardManager;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Dashboard;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;

public class dashboard extends ParamCommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {

        mode {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String targetMode = pack.getString().trim().toLowerCase();
                if (!targetMode.equals("grid") && !targetMode.equals("native")) {
                    return "Invalid mode. Use 'grid' or 'native'.";
                }

                XMLPrefsManager.XMLPrefsRoot.DASHBOARD.write(Dashboard.dashboard_mode, targetMode);
                XMLPrefsManager.XMLPrefsRoot.DASHBOARD.write(Dashboard.use_grid_dashboard, String.valueOf(targetMode.equals("grid")));

                if (pack.context instanceof Reloadable) {
                    ((Reloadable) pack.context).reload();
                }

                return "Dashboard mode set to: " + targetMode.toUpperCase();
            }
        },
        toggle {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(ExecutePack pack) {
                String current = XMLPrefsManager.get(Dashboard.dashboard_mode);
                String targetMode = "grid".equalsIgnoreCase(current) ? "native" : "grid";

                XMLPrefsManager.XMLPrefsRoot.DASHBOARD.write(Dashboard.dashboard_mode, targetMode);
                XMLPrefsManager.XMLPrefsRoot.DASHBOARD.write(Dashboard.use_grid_dashboard, String.valueOf(targetMode.equals("grid")));

                if (pack.context instanceof Reloadable) {
                    ((Reloadable) pack.context).reload();
                }

                return "Dashboard mode toggled to: " + targetMode.toUpperCase();
            }
        },
        layout {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String layoutStr = pack.getString().trim().toLowerCase();
                if (!layoutStr.contains("x")) {
                    return "Invalid layout format. Use e.g. 2x2, 1x2, 4x4, up to 5x5.";
                }

                XMLPrefsManager.XMLPrefsRoot.DASHBOARD.write(Dashboard.grid_layout, layoutStr);

                if (pack.context instanceof Reloadable) {
                    ((Reloadable) pack.context).reload();
                }

                return "Dashboard grid layout set to: " + layoutStr;
            }
        },
        blur {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String val = pack.getString().trim().toLowerCase();
                boolean enable = "true".equals(val) || "on".equals(val) || "1".equals(val);
                XMLPrefsManager.XMLPrefsRoot.DASHBOARD.write(Dashboard.enable_blur_effect, String.valueOf(enable));
                XMLPrefsManager.XMLPrefsRoot.DASHBOARD.write(Dashboard.blur_effect, String.valueOf(enable));

                if (pack.context instanceof Reloadable) {
                    ((Reloadable) pack.context).reload();
                }

                return "Dashboard blur effect set to: " + enable;
            }
        },
        items {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(ExecutePack pack) {
                DashboardItemManager manager = DashboardItemManager.getInstance();
                List<DashboardItem> list = manager.getItems();
                if (list.isEmpty()) {
                    return "No items configured in dashboard-item.xml.";
                }

                StringBuilder sb = new StringBuilder("[dashboard-item.xml items]:\n");
                for (DashboardItem item : list) {
                    sb.append(String.format("  box%d: type=%s, title=\"%s\", color=%s, bar=%b",
                            item.getIndex(), item.getType(), item.getTitle(), item.getColor(), item.isShowBar()));
                    if (item.getVariableName() != null && !item.getVariableName().isEmpty()) {
                        sb.append(", var=").append(item.getVariableName());
                    }
                    if (item.getCustomText() != null && !item.getCustomText().isEmpty()) {
                        sb.append(", text=").append(item.getCustomText());
                    }
                    sb.append("\n");
                }
                return sb.toString().trim();
            }
        },
        set {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String str = pack.getString().trim();
                // Usage: dashboard -set [box_number] [type] [optional: title] [optional: color]
                String[] parts = str.split("\\s+");
                if (parts.length < 2) {
                    return "Usage: dashboard -set [box#] [type] [title] [color]";
                }

                int boxIndex;
                String boxStr = parts[0].toLowerCase().replace("box", "");
                try {
                    boxIndex = Integer.parseInt(boxStr);
                } catch (Exception e) {
                    return "Invalid box index: " + parts[0];
                }

                String type = parts[1].toLowerCase();
                String title = parts.length > 2 ? parts[2] : DashboardItem.getDefaultTitleForType(type);
                String color = parts.length > 3 ? parts[3] : DashboardItem.getDefaultColorForType(type);

                DashboardItemManager manager = DashboardItemManager.getInstance();
                DashboardItem existing = manager.getItem(boxIndex);
                boolean showBar = existing != null ? existing.isShowBar() : true;
                String var = existing != null ? existing.getVariableName() : "";
                String text = existing != null ? existing.getCustomText() : "";

                manager.setItem(boxIndex, type, title, color, "", showBar, var, text);

                if (DashboardManager.instance != null) {
                    DashboardManager.instance.reloadGrid();
                } else if (pack.context instanceof Reloadable) {
                    ((Reloadable) pack.context).reload();
                }

                return "Configured box" + boxIndex + ": type=" + type + ", title=" + title + ", color=" + color;
            }
        },
        add {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String str = pack.getString().trim();
                String[] parts = str.split("\\s+");
                if (parts.length < 1 || parts[0].isEmpty()) {
                    return "Usage: dashboard -add [type] [optional: title] [optional: color]";
                }

                String type = parts[0].toLowerCase();
                String title = parts.length > 1 ? parts[1] : DashboardItem.getDefaultTitleForType(type);
                String color = parts.length > 2 ? parts[2] : DashboardItem.getDefaultColorForType(type);

                DashboardItemManager manager = DashboardItemManager.getInstance();
                int nextIndex = manager.getItems().size() + 1;
                DashboardItem newItem = new DashboardItem(nextIndex, "box" + nextIndex, type, title, color, "", true, "", "");
                manager.addItem(newItem);

                if (DashboardManager.instance != null) {
                    DashboardManager.instance.reloadGrid();
                } else if (pack.context instanceof Reloadable) {
                    ((Reloadable) pack.context).reload();
                }

                return "Added box" + nextIndex + " (" + type + ") to dashboard-item.xml";
            }
        },
        del {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String str = pack.getString().trim().toLowerCase().replace("box", "");
                int boxIndex;
                try {
                    boxIndex = Integer.parseInt(str);
                } catch (Exception e) {
                    return "Invalid box index: " + str;
                }

                DashboardItemManager manager = DashboardItemManager.getInstance();
                boolean removed = manager.removeItem(boxIndex);
                if (!removed) {
                    return "Box " + boxIndex + " not found.";
                }

                if (DashboardManager.instance != null) {
                    DashboardManager.instance.reloadGrid();
                } else if (pack.context instanceof Reloadable) {
                    ((Reloadable) pack.context).reload();
                }

                return "Removed box" + boxIndex + " from dashboard-item.xml.";
            }
        },
        reset {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(ExecutePack pack) {
                DashboardItemManager.getInstance().resetDefaults();
                if (DashboardManager.instance != null) {
                    DashboardManager.instance.reloadGrid();
                } else if (pack.context instanceof Reloadable) {
                    ((Reloadable) pack.context).reload();
                }
                return "Reset dashboard-item.xml to default 25 boxes.";
            }
        },
        on {
            @Override
            public int[] args() { return new int[0]; }
            @Override
            public String exec(ExecutePack pack) {
                XMLPrefsManager.XMLPrefsRoot.DASHBOARD.write(Dashboard.dashboard_enabled, "true");
                if (pack.context instanceof Reloadable) ((Reloadable) pack.context).reload();
                return "Dashboard enabled.";
            }
        },
        off {
            @Override
            public int[] args() { return new int[0]; }
            @Override
            public String exec(ExecutePack pack) {
                XMLPrefsManager.XMLPrefsRoot.DASHBOARD.write(Dashboard.dashboard_enabled, "false");
                if (pack.context instanceof Reloadable) ((Reloadable) pack.context).reload();
                return "Dashboard disabled.";
            }
        },
        itemfile {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(ExecutePack pack) {
                DashboardItemManager.getInstance().loadFromFile();
                File itemFile = new File(Tuils.getFolder(), DashboardItemManager.FILE_NAME);
                if (!itemFile.exists()) {
                    itemFile = new File(Tuils.getFolder(), DashboardItemManager.ALT_FILE_NAME);
                }
                pack.context.startActivity(Tuils.openFile(pack.context, itemFile));
                return null;
            }
        },
        file {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(ExecutePack pack) {
                File dashboardFile = new File(Tuils.getFolder(), XMLPrefsManager.XMLPrefsRoot.DASHBOARD.path);
                pack.context.startActivity(Tuils.openFile(pack.context, dashboardFile));
                return null;
            }
        };

        static Param get(String p) {
            p = p.toLowerCase();
            Param[] ps = values();
            for (Param p1 : ps)
                if (p.endsWith(p1.label()))
                    return p1;
            return null;
        }

        static String[] labels() {
            Param[] ps = values();
            String[] ss = new String[ps.length];

            for (int count = 0; count < ps.length; count++) {
                ss[count] = ps[count].label();
            }

            return ss;
        }

        @Override
        public String label() {
            return Tuils.MINUS + name();
        }

        @Override
        public String onNotArgEnough(ExecutePack pack, int nArgs) {
            return pack.context.getString(R.string.help_dashboard);
        }

        @Override
        public String onArgNotFound(ExecutePack pack, int index) {
            return pack.context.getString(R.string.output_invalid_param) + Tuils.SPACE + pack.get();
        }
    }

    @Override
    public String[] params() {
        return Param.labels();
    }

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(MainPack pack, String param) {
        return Param.get(param);
    }

    @Override
    protected String doThings(ExecutePack pack) {
        return null;
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public int helpRes() {
        return R.string.help_dashboard;
    }
}
