package ohi.andre.consolelauncher.commands.main.raw;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Ai;
import ohi.andre.consolelauncher.tuils.Tuils;

public class api extends ParamCommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {
        key {
            @Override
            public int[] args() { return new int[]{CommandAbstraction.PLAIN_TEXT}; }

            @Override
            public String exec(ExecutePack pack) {
                String key = pack.getString().trim();
                if (key.isEmpty()) return pack.context.getString(R.string.output_lessarg);
                XMLPrefsManager.XMLPrefsRoot.AI.write(Ai.google_api_key, key);
                return "Google AI API Key updated in ai.xml.";
            }
        };

        static Param get(String p) {
            p = p.toLowerCase();
            for (Param p1 : values()) if (p.endsWith(p1.label())) return p1;
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
        public String onNotArgEnough(ExecutePack pack, int n) { return "Usage: api -key [key]"; }

        @Override
        public String onArgNotFound(ExecutePack pack, int index) { return pack.context.getString(R.string.output_invalid_param) + Tuils.SPACE + pack.get(); }
    }

    @Override
    public String[] params() { return Param.labels(); }

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(MainPack pack, String param) { return Param.get(param); }

    @Override
    protected String doThings(ExecutePack pack) {
        return "Usage: api -key [key]";
    }

    @Override
    public int priority() { return 5; }

    @Override
    public int helpRes() { return R.string.help_api_cmd; }
}
