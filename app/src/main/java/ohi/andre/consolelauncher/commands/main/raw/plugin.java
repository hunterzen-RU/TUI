package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Intent;

import java.io.File;
import java.util.List;
import java.util.Map;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.PluginManager;
import ohi.andre.consolelauncher.managers.plugins.PluginManifest;
import ohi.andre.consolelauncher.managers.plugins.PluginModel;
import ohi.andre.consolelauncher.managers.plugins.PluginStudioActivity;
import ohi.andre.consolelauncher.managers.plugins.PluginTemplate;
import ohi.andre.consolelauncher.tuils.Tuils;

public class plugin extends ParamCommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {

        ls {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(ExecutePack pack) {
                PluginManager pm = PluginManager.getInstance(pack.context);
                List<PluginModel> list = pm.getInstalledPlugins();
                if (list.isEmpty()) {
                    return "No plugins installed in " + PluginManager.PLUGINS_DIR + "/.\nUse 'plugin -studio' or 'plugin -templates' to create or install plugins.";
                }

                StringBuilder sb = new StringBuilder("[INSTALLED T-UI PLUGINS]:\n");
                for (PluginModel p : list) {
                    sb.append("  • ").append(p.getName())
                            .append(" [id: ").append(p.getId()).append("]")
                            .append(" (v").append(p.getVersion()).append(")")
                            .append(" -> entry: ").append(p.getManifest().entryPoint)
                            .append("\n");
                    if (!p.getDescription().isEmpty()) {
                        sb.append("    Desc: ").append(p.getDescription()).append("\n");
                    }
                }
                return sb.toString().trim();
            }
        },
        info {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String id = pack.getString().trim();
                PluginManager pm = PluginManager.getInstance(pack.context);
                PluginModel p = pm.getPlugin(id);
                if (p == null) {
                    return "Plugin '" + id + "' not found. Use 'plugin -ls' to list installed plugins.";
                }

                PluginManifest m = p.getManifest();
                StringBuilder sb = new StringBuilder();
                sb.append("=== [ PLUGIN INFO: ").append(p.getName()).append(" ] ===\n");
                sb.append("ID: ").append(p.getId()).append("\n");
                sb.append("Version: ").append(p.getVersion()).append(" | Target: ").append(m.targetAppVersion).append("\n");
                sb.append("Description: ").append(p.getDescription()).append("\n");
                sb.append("Entry Point: ").append(m.entryPoint).append("\n");
                sb.append("Location: ").append(p.getFolder().getAbsolutePath()).append("\n");

                sb.append("Permissions: ");
                if (m.permissions.isEmpty()) {
                    sb.append("None\n");
                } else {
                    sb.append(String.join(", ", m.permissions)).append("\n");
                }

                sb.append("Tree Structure:\n");
                if (m.tree.isEmpty()) {
                    for (File f : p.listFiles()) {
                        sb.append("  - ").append(p.getRelativePath(f)).append("\n");
                    }
                } else {
                    for (Map.Entry<String, String> entry : m.tree.entrySet()) {
                        sb.append("  - ").append(entry.getKey());
                        if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                            sb.append(" (").append(entry.getValue()).append(")");
                        }
                        sb.append("\n");
                    }
                }

                if (!m.manifestSpecials.isEmpty()) {
                    sb.append("Manifest Specials: ").append(m.manifestSpecials.toString()).append("\n");
                }

                return sb.toString().trim();
            }
        },
        launch {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String full = pack.getString().trim();
                String[] parts = full.split("\\s+");
                if (parts.length == 0 || parts[0].isEmpty()) {
                    return "Usage: plugin -launch [plugin_id] [optional args...]";
                }

                String id = parts[0];
                String[] args = new String[parts.length - 1];
                System.arraycopy(parts, 1, args, 0, parts.length - 1);

                PluginManager pm = PluginManager.getInstance(pack.context);
                PluginModel p = pm.getPlugin(id);
                if (p == null) {
                    return "Plugin '" + id + "' not found.";
                }

                pm.launchPlugin(pack.context, p, args, new PluginManager.Callback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        Tuils.sendOutput(pack.context, result);
                    }

                    @Override
                    public void onError(String message) {
                        Tuils.sendOutput(pack.context, "[ERROR]: " + message);
                    }
                });

                return "Launching " + p.getName() + "...";
            }
        },
        studio {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(ExecutePack pack) {
                Intent intent = new Intent(pack.context, PluginStudioActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                pack.context.startActivity(intent);
                return "Opening T-UI Plugin Code Studio...";
            }
        },
        edit {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String id = pack.getString().trim();
                Intent intent = new Intent(pack.context, PluginStudioActivity.class);
                intent.putExtra("open_plugin", id);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                pack.context.startActivity(intent);
                return "Opening " + id + " in Plugin Studio...";
            }
        },
        create {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String str = pack.getString().trim();
                String[] parts = str.split("\\s+", 3);
                if (parts.length < 1 || parts[0].isEmpty()) {
                    return "Usage: plugin -create [id] [optional: kt|java|html] [optional: name]";
                }

                String id = parts[0];
                String template = parts.length > 1 ? parts[1] : "kt";
                String name = parts.length > 2 ? parts[2] : null;

                PluginManager pm = PluginManager.getInstance(pack.context);
                boolean ok = pm.createPlugin(id, name, template, "Custom T-UI Plugin");
                if (ok) {
                    return "Plugin '" + id + "' created successfully in plugins/" + id + "/.\nRun 'plugin -edit " + id + "' to open it in Studio.";
                } else {
                    return "Failed to create plugin. ID '" + id + "' may already exist.";
                }
            }
        },
        templates {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(ExecutePack pack) {
                PluginManager pm = PluginManager.getInstance(pack.context);
                pm.fetchTemplates(new PluginManager.Callback<List<PluginTemplate>>() {
                    @Override
                    public void onSuccess(List<PluginTemplate> list) {
                        StringBuilder sb = new StringBuilder("[AVAILABLE PLUGIN TEMPLATES]:\n");
                        sb.append("Source: ").append(PluginManager.TEMPLATES_URL).append("\n\n");
                        for (PluginTemplate t : list) {
                            sb.append("  • ").append(t.name)
                                    .append(" [id: ").append(t.id).append("]")
                                    .append(" (v").append(t.version).append(" by ").append(t.author).append(")\n")
                                    .append("    ").append(t.description).append("\n")
                                    .append("    Zip: ").append(t.zipUrl).append("\n");
                        }
                        sb.append("\nTo create: plugin -create [id] [template_id]\nOr use 'plugin -studio' for GUI Studio.");
                        Tuils.sendOutput(pack.context, sb.toString());
                    }

                    @Override
                    public void onError(String message) {
                        Tuils.sendOutput(pack.context, "Failed to fetch templates: " + message);
                    }
                });
                return "Fetching online templates from " + PluginManager.TEMPLATES_URL + "...";
            }
        },
        install {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String target = pack.getString().trim();
                PluginManager pm = PluginManager.getInstance(pack.context);

                if (target.startsWith("http://") || target.startsWith("https://")) {
                    pm.installFromUrl(target, new PluginManager.Callback<PluginModel>() {
                        @Override
                        public void onSuccess(PluginModel result) {
                            Tuils.sendOutput(pack.context, "Plugin '" + result.getName() + "' (v" + result.getVersion() + ") installed successfully!");
                        }

                        @Override
                        public void onError(String message) {
                            Tuils.sendOutput(pack.context, "[INSTALL ERROR]: " + message);
                        }
                    });
                    return "Downloading and installing plugin from " + target + "...";
                }

                File localZip = new File(target);
                if (!localZip.exists()) {
                    localZip = new File(Tuils.getFolder(), target);
                }
                if (!localZip.exists()) {
                    return "File not found: " + target;
                }

                boolean ok = pm.installFromZip(localZip, null);
                if (ok) {
                    return "Plugin installed successfully from " + localZip.getName() + "!";
                } else {
                    return "Failed to install plugin from ZIP archive.";
                }
            }
        },
        rm {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String id = pack.getString().trim();
                PluginManager pm = PluginManager.getInstance(pack.context);
                boolean removed = pm.removePlugin(id);
                if (removed) {
                    return "Plugin '" + id + "' uninstalled.";
                } else {
                    return "Plugin '" + id + "' not found or could not be removed.";
                }
            }
        },
        export {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String id = pack.getString().trim();
                PluginManager pm = PluginManager.getInstance(pack.context);
                PluginModel p = pm.getPlugin(id);
                if (p == null) {
                    return "Plugin '" + id + "' not found.";
                }

                File zip = pm.exportPluginZip(p);
                if (zip != null && zip.exists()) {
                    return "Plugin exported to: " + zip.getAbsolutePath() + " (" + zip.length() + " bytes)";
                } else {
                    return "Failed to export plugin.";
                }
            }
        },
        sample {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(ExecutePack pack) {
                PluginManager pm = PluginManager.getInstance(pack.context);
                pm.ensureSamplePlugin();
                PluginModel sample = pm.getPlugin("sample");
                if (sample != null) {
                    return "Sample plugin verified in: " + sample.getFolder().getAbsolutePath() + "\nRun 'plugin -launch sample' or 'plugin -edit sample'.";
                }
                return "Sample plugin generated.";
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
            return pack.context.getString(R.string.help_plugin);
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
        return R.string.help_plugin;
    }
}
