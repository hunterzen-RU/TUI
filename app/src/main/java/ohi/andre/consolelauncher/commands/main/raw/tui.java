package ohi.andre.consolelauncher.commands.main.raw;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.PackageManager;
import ohi.andre.consolelauncher.managers.VariablesManager;
import ohi.andre.consolelauncher.tuils.Tuils;

public class tui extends ParamCommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {

        install {
            @Override
            public int[] args() { return new int[] {CommandAbstraction.PLAIN_TEXT}; }

            @Override
            public String exec(ExecutePack pack) {
                String pkgName = pack.getString().trim();
                if (pkgName.isEmpty()) return pack.context.getString(R.string.output_lessarg);

                Tuils.sendOutput(pack.context, "TUI Package Manager: Installing '" + pkgName + "'...");
                PackageManager.installPackage(pack.context, pkgName, new PackageManager.InstallCallback() {
                    @Override
                    public void onSuccess(String message) {
                        Tuils.sendOutput(pack.context, message);
                    }
                    @Override
                    public void onError(String error) {
                        Tuils.sendOutput(android.graphics.Color.RED, pack.context, "TUI Install Error: " + error);
                    }
                });
                return null;
            }
        },
        set {
            @Override
            public int[] args() { return new int[] {CommandAbstraction.PLAIN_TEXT}; }

            @Override
            public String exec(ExecutePack pack) {
                String mgr = pack.getString().trim();
                if (mgr.isEmpty()) return "Usage: tui -set <hybrid|native|termux|apt|pkg>";
                VariablesManager.setVariable("active_pkg_mgr", mgr);
                if ("native".equalsIgnoreCase(mgr) || "termux".equalsIgnoreCase(mgr) || "apt".equalsIgnoreCase(mgr) || "pkg".equalsIgnoreCase(mgr)) {
                    return "Package manager set to: " + mgr + " (Native Termux-style installation mode)";
                } else {
                    return "Package manager set to: " + mgr + " (T-UI Visual Progress Bar mode)";
                }
            }
        },
        update {
            @Override
            public int[] args() { return new int[0]; }
            @Override
            public String exec(ExecutePack pack) {
                Tuils.sendOutput(pack.context, "Checking for updates...");
                PackageManager.fetchRepoIndex(pack.context, new PackageManager.RepoCallback() {
                    @Override
                    public void onSuccess(List<PackageManager.PackageInfo> packages) {
                        Map<String, String> installed = PackageManager.getInstalledPackagesMap(pack.context);
                        boolean found = false;
                        for (PackageManager.PackageInfo p : packages) {
                            if (installed.containsKey(p.name)) {
                                Tuils.sendOutput(pack.context, "Update available: " + p.name + " (" + p.version + ")");
                                found = true;
                            }
                        }
                        if (!found) Tuils.sendOutput(pack.context, "All packages up to date.");
                    }
                    @Override
                    public void onError(String error) { Tuils.sendOutput(android.graphics.Color.RED, pack.context, "Update check failed: " + error); }
                });
                return null;
            }
        },
        upgrade {
            @Override
            public int[] args() { return new int[0]; }
            @Override
            public String exec(ExecutePack pack) {
                Tuils.sendOutput(pack.context, "Upgrading packages...");
                PackageManager.fetchRepoIndex(pack.context, new PackageManager.RepoCallback() {
                    @Override
                    public void onSuccess(List<PackageManager.PackageInfo> packages) {
                        Map<String, String> installed = PackageManager.getInstalledPackagesMap(pack.context);
                        for (PackageManager.PackageInfo p : packages) {
                            if (installed.containsKey(p.name)) {
                                Tuils.sendOutput(pack.context, "Upgrading " + p.name + "...");
                                PackageManager.installPackage(pack.context, p.name, null);
                            }
                        }
                    }
                    @Override
                    public void onError(String error) { Tuils.sendOutput(android.graphics.Color.RED, pack.context, "Upgrade failed: " + error); }
                });
                return null;
            }
        },
        update_app {
            @Override
            public int[] args() { return new int[0]; }
            @Override
            public String exec(ExecutePack pack) {
                String primaryAbi = PackageManager.getPrimaryAbi();
                Tuils.sendOutput(pack.context, "Checking for TUI update (Device ABI: " + primaryAbi + ")...");
                PackageManager.fetchRepoIndex(pack.context, new PackageManager.RepoCallback() {
                    @Override
                    public void onSuccess(List<PackageManager.PackageInfo> packages) {
                        PackageManager.PackageInfo target = null;
                        // 1. Check for ABI specific entry, e.g. "tui-app-arm64-v8a" or abi property
                        for (PackageManager.PackageInfo p : packages) {
                            if (p.name.equalsIgnoreCase("tui-app-" + primaryAbi) ||
                                ("tui-app".equalsIgnoreCase(p.name) && primaryAbi.equalsIgnoreCase(p.abi))) {
                                target = p;
                                break;
                            }
                        }
                        // 2. Fallback to generic "tui-app"
                        if (target == null) {
                            for (PackageManager.PackageInfo p : packages) {
                                if ("tui-app".equalsIgnoreCase(p.name)) {
                                    target = p;
                                    break;
                                }
                            }
                        }

                        if (target != null) {
                            final PackageManager.PackageInfo chosen = target;
                            Tuils.sendOutput(pack.context, "Found update: " + chosen.name + " (" + chosen.version + ")" + (chosen.abi != null && !chosen.abi.isEmpty() ? " [" + chosen.abi + "]" : ""));
                            PackageManager.installPackage(pack.context, chosen.name, new PackageManager.InstallCallback() {
                                @Override
                                public void onSuccess(String message) { Tuils.sendOutput(pack.context, "Package installer initiated. Please complete installation."); }
                                @Override
                                public void onError(String error) { Tuils.sendOutput(android.graphics.Color.RED, pack.context, "App update failed: " + error); }
                            });
                            return;
                        }
                        Tuils.sendOutput(pack.context, "No TUI app update found.");
                    }
                    @Override
                    public void onError(String error) { Tuils.sendOutput(android.graphics.Color.RED, pack.context, "Update check failed: " + error); }
                });
                return null;
            }
        },
        check_update {
            @Override
            public int[] args() { return new int[0]; }
            @Override
            public String exec(ExecutePack pack) {
                String primaryAbi = PackageManager.getPrimaryAbi();
                Tuils.sendOutput(pack.context, "Scanning repository for TUI releases (ABI: " + primaryAbi + ")...");
                PackageManager.fetchRepoIndex(pack.context, new PackageManager.RepoCallback() {
                    @Override
                    public void onSuccess(List<PackageManager.PackageInfo> packages) {
                        boolean found = false;
                        for (PackageManager.PackageInfo p : packages) {
                            if (p.name.startsWith("tui-app")) {
                                Tuils.sendOutput(pack.context, "• " + p.name + " v" + p.version + " (" + (p.abi != null && !p.abi.isEmpty() ? p.abi : "universal") + ")");
                                if (p.sha256 != null && !p.sha256.isEmpty()) {
                                    Tuils.sendOutput(pack.context, "  SHA-256: " + p.sha256);
                                }
                                found = true;
                            }
                        }
                        if (!found) Tuils.sendOutput(pack.context, "No TUI releases indexed in repository.");
                    }
                    @Override
                    public void onError(String error) { Tuils.sendOutput(android.graphics.Color.RED, pack.context, "Check failed: " + error); }
                });
                return null;
            }
        },
        get {
            @Override
            public int[] args() { return new int[0]; }

            @Override
            public String exec(ExecutePack pack) {
                String mgr = VariablesManager.getVariable("active_pkg_mgr");
                if (mgr == null || mgr.isEmpty()) mgr = "hybrid";
                return "Current package manager: " + mgr;
            }
        },
        run {
            @Override
            public int[] args() { return new int[] {CommandAbstraction.PLAIN_TEXT}; }

            @Override
            public String exec(ExecutePack pack) {
                String cmd = pack.getString().trim();
                if (cmd.isEmpty()) return pack.context.getString(R.string.output_lessarg);

                new Thread(() -> {
                    String result = ohi.andre.consolelauncher.managers.PackageHelper.tuiRun(pack.context, cmd);
                    Tuils.sendOutput(pack.context, result);
                }).start();
                return "Launching Termux command: " + cmd;
            }
        },
        version {
            @Override
            public int[] args() { return new int[0]; }

            @Override
            public String exec(ExecutePack pack) {
                try {
                    android.content.pm.PackageInfo pInfo = pack.context.getPackageManager().getPackageInfo(pack.context.getPackageName(), 0);
                    return "T-UI Console Launcher v" + pInfo.versionName + " (Build " + pInfo.versionCode + ")";
                } catch (Exception e) {
                    return "T-UI Console Launcher v1.0";
                }
            }
        },
        about {
            @Override
            public int[] args() { return new int[0]; }

            @Override
            public String exec(ExecutePack pack) {
                String repoUrl = "https://github.com/hunter-zen-RU/TUI/";
                try {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(repoUrl));
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    pack.context.startActivity(intent);
                } catch (Exception e) {
                    Tuils.log(e);
                }
                return "T-UI Repository: " + repoUrl;
            }
        },
        session {
            @Override
            public int[] args() { return new int[] {CommandAbstraction.PLAIN_TEXT}; }

            @Override
            public String exec(ExecutePack pack) {
                String arg = pack.getString().trim();
                if (arg.isEmpty() || "list".equalsIgnoreCase(arg)) {
                    List<String> list = ohi.andre.consolelauncher.managers.SessionManager.getSessions();
                    String active = ohi.andre.consolelauncher.managers.SessionManager.getActiveSession();
                    StringBuilder sb = new StringBuilder("Active Sessions:\n");
                    for (String s : list) {
                        if (s.equalsIgnoreCase(active)) {
                            sb.append("• ").append(s).append(" [ACTIVE]\n");
                        } else {
                            sb.append("• ").append(s).append("\n");
                        }
                    }
                    sb.append("\nTip: Swipe LEFT or RIGHT on the dashboard grid to switch active session.");
                    return sb.toString();
                }

                if (arg.startsWith("close ")) {
                    String toClose = arg.substring(6).trim();
                    return ohi.andre.consolelauncher.managers.SessionManager.closeSession(pack.context, toClose);
                }

                return ohi.andre.consolelauncher.managers.SessionManager.createOrSwitchSession(pack.context, arg);
            }
        };

        static Param get(String p) {
            if (p == null) return null;
            p = p.toLowerCase().trim();
            for (Param p1 : values()) {
                if (p.endsWith(p1.label()) || p.equalsIgnoreCase(p1.name()) || p.equalsIgnoreCase("-" + p1.name())) return p1;
            }
            return null;
        }

        static String[] labels() {
            String[] ss = new String[values().length];
            for (int count = 0; count < values().length; count++) ss[count] = values()[count].label();
            return ss;
        }

        @Override
        public String label() { return Tuils.MINUS + name(); }

        @Override
        public String onNotArgEnough(ExecutePack pack, int n) { return pack.context.getString(R.string.help_tui_manager); }

        @Override
        public String onArgNotFound(ExecutePack pack, int index) { return pack.context.getString(R.string.output_invalid_param) + Tuils.SPACE + pack.get(); }
    }

    @Override
    public String[] params() { return Param.labels(); }

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(MainPack pack, String param) { return Param.get(param); }

    @Override
    protected String doThings(ExecutePack pack) { return null; }

    @Override
    public int priority() { return 3; }

    @Override
    public int helpRes() { return R.string.help_tui_manager; }
}
