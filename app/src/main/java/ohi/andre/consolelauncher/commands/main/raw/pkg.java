package ohi.andre.consolelauncher.commands.main.raw;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.PackageManager;
import ohi.andre.consolelauncher.tuils.Tuils;

public class pkg extends ParamCommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {

        install {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String pkgName = pack.getString().trim();
                if (pkgName.isEmpty()) {
                    return pack.context.getString(R.string.output_lessarg);
                }

                Tuils.sendOutput(pack.context, "Fetching package '" + pkgName + "' from repository...");
                PackageManager.installPackage(pack.context, pkgName, new PackageManager.InstallCallback() {
                    @Override
                    public void onSuccess(String message) {
                        Tuils.sendOutput(Color.GREEN, pack.context, message);
                    }

                    @Override
                    public void onError(String error) {
                        Tuils.sendOutput(Color.RED, pack.context, "Install failed: " + error);
                    }
                });

                return null;
            }
        },
        search {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String query = pack.getString().trim().toLowerCase();
                Tuils.sendOutput(pack.context, "Searching repository (" + PackageManager.getRepoUrl() + ")...");

                PackageManager.fetchRepoIndex(pack.context, new PackageManager.RepoCallback() {
                    @Override
                    public void onSuccess(List<PackageManager.PackageInfo> packages) {
                        if (packages.isEmpty()) {
                            Tuils.sendOutput(pack.context, "No packages found in repository.");
                            return;
                        }

                        StringBuilder sb = new StringBuilder("--- Available Packages ---\n");
                        int count = 0;
                        for (PackageManager.PackageInfo p : packages) {
                            if (query.isEmpty() || p.name.toLowerCase().contains(query) || p.description.toLowerCase().contains(query)) {
                                sb.append(" • ").append(p.name).append(" (v").append(p.version).append(" - ").append(p.type).append(")\n");
                                if (!p.description.isEmpty()) {
                                    sb.append("   ").append(p.description).append("\n");
                                }
                                count++;
                            }
                        }

                        if (count == 0) {
                            Tuils.sendOutput(pack.context, "No packages matching '" + query + "'.");
                        } else {
                            Tuils.sendOutput(pack.context, sb.toString().trim());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Tuils.sendOutput(Color.RED, pack.context, "Search failed: " + error);
                    }
                });

                return null;
            }
        },
        ls {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(ExecutePack pack) {
                Map<String, String> installed = PackageManager.getInstalledPackagesMap(pack.context);
                if (installed.isEmpty()) {
                    return "No packages currently installed.";
                }

                StringBuilder sb = new StringBuilder("--- Installed Packages ---\n");
                for (Map.Entry<String, String> entry : installed.entrySet()) {
                    sb.append(" • ").append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n");
                }
                return sb.toString().trim();
            }
        },
        rm {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String pkgName = pack.getString().trim();
                if (pkgName.isEmpty()) {
                    return pack.context.getString(R.string.output_lessarg);
                }

                boolean removed = PackageManager.removeInstalledPackage(pack.context, pkgName);
                if (removed) {
                    return "Successfully uninstalled package '" + pkgName + "'.";
                } else {
                    return "Package '" + pkgName + "' is not installed.";
                }
            }
        },
        repo {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String newUrl = pack.getString().trim();
                if (newUrl.isEmpty()) {
                    return "Current Repository URL:\n" + PackageManager.getRepoUrl();
                }

                PackageManager.setRepoUrl(newUrl);
                return "Repository URL updated to:\n" + newUrl;
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
            return pack.context.getString(R.string.help_pkg);
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
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_pkg;
    }
}
