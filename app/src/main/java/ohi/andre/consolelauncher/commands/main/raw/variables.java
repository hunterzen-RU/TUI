package ohi.andre.consolelauncher.commands.main.raw;

import java.io.File;
import java.util.Map;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.VariablesManager;
import ohi.andre.consolelauncher.tuils.Tuils;

public class variables extends ParamCommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {

        set {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String str = pack.getString().trim();
                int eqIdx = str.indexOf('=');
                String name, val;
                if (eqIdx > 0) {
                    name = str.substring(0, eqIdx).trim();
                    val = str.substring(eqIdx + 1).trim();
                } else {
                    String[] parts = str.split("\\s+", 2);
                    if (parts.length < 2) {
                        return pack.context.getString(R.string.output_lessarg);
                    }
                    name = parts[0].trim();
                    val = parts[1].trim();
                }

                if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                    val = val.substring(1, val.length() - 1);
                }

                VariablesManager.setVariable(name, val);
                String detectedType = VariablesManager.detectType(val);
                return "Variable saved: " + name + " = " + val + " [" + detectedType + "]";
            }
        },
        get {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String name = pack.getString().trim();
                String val = VariablesManager.getVariable(name);
                if (val == null || val.isEmpty()) {
                    if (VariablesManager.isPredefined(name)) {
                        val = "{{" + name + "}}";
                    } else {
                        return "Variable '" + name + "' not found.";
                    }
                }
                String detectedType = VariablesManager.detectType(val);
                String desc = VariablesManager.getDescription(name);

                StringBuilder sb = new StringBuilder();
                sb.append(name).append(" = ").append(val).append(" [").append(detectedType).append("]");
                if (desc != null && !desc.isEmpty()) {
                    sb.append("\nDescription: ").append(desc);
                }
                if (VariablesManager.isPredefined(name)) {
                    String live = VariablesManager.getLiveValue(pack.context, name);
                    sb.append("\nLive Value: ").append(live);
                }
                return sb.toString();
            }
        },
        desc {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String str = pack.getString().trim();
                String[] parts = str.split("\\s+", 2);
                if (parts.length < 2) {
                    return "Usage: variables -desc [variable_name] [description text]";
                }
                String name = parts[0].trim();
                String desc = parts[1].trim();
                if (desc.startsWith("\"") && desc.endsWith("\"") && desc.length() >= 2) {
                    desc = desc.substring(1, desc.length() - 1);
                }
                VariablesManager.setDescription(name, desc);
                return "Description updated for " + name + ": " + desc;
            }
        },
        ls {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(ExecutePack pack) {
                Map<String, String> all = VariablesManager.getAllVariables();
                Map<String, String> descs = VariablesManager.getAllDescriptions();

                if (all.isEmpty()) {
                    return "No variables defined in variable.xml.";
                }

                StringBuilder sb = new StringBuilder("[variable.xml / variables.xml]:\n");
                for (Map.Entry<String, String> entry : all.entrySet()) {
                    String name = entry.getKey();
                    String val = entry.getValue();
                    String type = VariablesManager.detectType(val);
                    String desc = descs.getOrDefault(name, "");

                    sb.append("  {{").append(name).append("}} = ").append(val);
                    sb.append(" [").append(type).append("]");
                    if (!desc.isEmpty()) {
                        sb.append(" - ").append(desc);
                    }
                    sb.append("\n");
                }
                return sb.toString().trim();
            }
        },
        file {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(ExecutePack pack) {
                VariablesManager.loadFromFile();
                File xmlFile = new File(Tuils.getFolder(), VariablesManager.ALT_FILE_NAME);
                if (!xmlFile.exists()) {
                    xmlFile = new File(Tuils.getFolder(), VariablesManager.FILE_NAME);
                }
                pack.context.startActivity(Tuils.openFile(pack.context, xmlFile));
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
            return pack.context.getString(R.string.help_variables);
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
        return R.string.help_variables;
    }
}
