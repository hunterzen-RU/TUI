package ohi.andre.consolelauncher.commands.main.raw;

import java.io.File;
import java.util.ArrayList;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.AliasManager;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * Created by andre on 15/11/15.
 */
public class alias extends ParamCommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {

        add {
            @Override
            public String exec(ExecutePack pack) {
                ArrayList<String> args = pack.getList();
                if(args.size() < 2) return pack.context.getString(R.string.output_lessarg);

                String name = args.remove(0);
                String val = Tuils.toPlanString(args, Tuils.SPACE);

                ((MainPack) pack).aliasManager.add(pack.context, name, val);
                new ohi.andre.consolelauncher.managers.AliasDbHelper(pack.context).setAlias(name, val);
                return "Alias mapped: " + name + " --> " + val;
            }

            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.TEXTLIST};
            }
        },
        rm {
            @Override
            public String exec(ExecutePack pack) {
                ArrayList<String> args = pack.getList();
                if(args.size() < 1) return pack.context.getString(R.string.output_lessarg);
                String name = args.get(0);
                ((MainPack) pack).aliasManager.remove(pack.context, name);
                new ohi.andre.consolelauncher.managers.AliasDbHelper(pack.context).removeAlias(name);
                return "Alias removed: " + name;
            }

            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.TEXTLIST};
            }
        },
        file {
            @Override
            public String exec(ExecutePack pack) {
                pack.context.startActivity(Tuils.openFile(pack.context, new File(Tuils.getFolder(), AliasManager.PATH)));
                return null;
            }

            @Override
            public int[] args() {
                return new int[0];
            }
        },
        ls {
            @Override
            public String exec(ExecutePack pack) {
                StringBuilder sb = new StringBuilder();
                String fileAliases = ((MainPack) pack).aliasManager.printAliases();
                if (fileAliases != null && !fileAliases.isEmpty()) {
                    sb.append(fileAliases).append("\n");
                }
                java.util.List<ohi.andre.consolelauncher.managers.AliasDbHelper.AliasEntry> dbAliases =
                        new ohi.andre.consolelauncher.managers.AliasDbHelper(pack.context).getAllAliases();
                if (!dbAliases.isEmpty()) {
                    sb.append("\n[SQLite Aliases]:\n");
                    for (ohi.andre.consolelauncher.managers.AliasDbHelper.AliasEntry e : dbAliases) {
                        sb.append(e.getName()).append(" --> ").append(e.getValue()).append("\n");
                    }
                }
                return sb.toString().trim();
            }

            @Override
            public int[] args() {
                return new int[0];
            }
        },
        tutorial {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(ExecutePack pack) {
                pack.context.startActivity(Tuils.webPage("https://github.com/Andre1299/TUI-ConsoleLauncher/wiki/Alias"));
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
        public String onNotArgEnough(ExecutePack pack, int index) {
            return pack.context.getString(R.string.help_alias);
        }

        @Override
        public String onArgNotFound(ExecutePack pack, int index) {
            return null;
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
    public int helpRes() {
        return R.string.help_alias;
    }

    @Override
    public int priority() {
        return 2;
    }
}
