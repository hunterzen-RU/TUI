package ohi.andre.consolelauncher.commands.main.raw;

import android.graphics.Color;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.AiManager;
import ohi.andre.consolelauncher.managers.VariablesManager;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Ai;
import ohi.andre.consolelauncher.tuils.Tuils;

public class ai extends ParamCommand {

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
        },
        test {
            @Override
            public int[] args() { return new int[0]; }

            @Override
            public String exec(ExecutePack pack) {
                Tuils.sendOutput(pack.context, "Testing AI connection (" + XMLPrefsManager.get(Ai.ai_mode) + ")...");
                AiManager aiManager = new AiManager(pack.context);
                aiManager.testApi(new AiManager.AiCallback() {
                    @Override
                    public void onResponse(CharSequence response) {
                        Tuils.sendOutput(Color.GREEN, pack.context, response);
                    }

                    @Override
                    public void onError(String error) {
                        Tuils.sendOutput(Color.RED, pack.context, error);
                    }
                });
                return null;
            }
        },
        set {
            @Override
            public int[] args() { return new int[]{CommandAbstraction.PLAIN_TEXT, CommandAbstraction.PLAIN_TEXT}; }

            @Override
            public String exec(ExecutePack pack) {
                String prompt = pack.getString().trim();
                String varName = pack.getString().trim();
                
                if (prompt.isEmpty() || varName.isEmpty()) return pack.context.getString(R.string.output_lessarg);

                Tuils.sendOutput(pack.context, "AI processing: " + prompt);
                AiManager aiManager = new AiManager(pack.context);
                aiManager.generateResponse(prompt, new AiManager.AiCallback() {
                    @Override
                    public void onResponse(CharSequence response) {
                        VariablesManager.setVariable(varName, response.toString().trim());
                        Tuils.sendOutput(pack.context, "AI result saved to variable: " + varName);
                    }

                    @Override
                    public void onError(String error) {
                        Tuils.sendOutput(Color.RED, pack.context, "AI Error: " + error);
                    }
                });
                return null;
            }
        },
        sys {
            @Override
            public int[] args() { return new int[]{CommandAbstraction.PLAIN_TEXT}; }

            @Override
            public String exec(ExecutePack pack) {
                String prompt = pack.getString().trim();
                if (prompt.isEmpty()) return pack.context.getString(R.string.output_lessarg);
                XMLPrefsManager.XMLPrefsRoot.AI.write(Ai.system_prompt, prompt);
                return "AI System Prompt updated in ai.xml.";
            }
        },
        mode {
            @Override
            public int[] args() { return new int[]{CommandAbstraction.PLAIN_TEXT}; }

            @Override
            public String exec(ExecutePack pack) {
                String mode = pack.getString().trim().toLowerCase();
                if (!mode.equals("global") && !mode.equals("local")) {
                    return "Invalid mode. Use 'global' or 'local'.";
                }
                XMLPrefsManager.XMLPrefsRoot.AI.write(Ai.ai_mode, mode);
                return "AI Mode set to: " + mode;
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
        public String onNotArgEnough(ExecutePack pack, int n) { return pack.context.getString(R.string.help_ai); }

        @Override
        public String onArgNotFound(ExecutePack pack, int index) { return pack.context.getString(R.string.output_invalid_param) + Tuils.SPACE + pack.get(); }
    }

    @Override
    public String[] params() { return Param.labels(); }

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(MainPack pack, String param) { return Param.get(param); }

    @Override
    protected String doThings(ExecutePack pack) {
        String prompt = pack.getString().trim();
        if (prompt.isEmpty()) return pack.context.getString(R.string.help_ai);

        Tuils.sendOutput(pack.context, "AI thinking (" + XMLPrefsManager.get(Ai.ai_mode) + ")...");
        AiManager aiManager = new AiManager(pack.context);
        aiManager.generateResponse(prompt, new AiManager.AiCallback() {
            @Override
            public void onResponse(CharSequence response) {
                Tuils.sendOutput(pack.context, response);
            }

            @Override
            public void onError(String error) {
                Tuils.sendOutput(Color.RED, pack.context, "AI Error: " + error);
            }
        });
        return null;
    }

    @Override
    public int priority() { return 5; }

    @Override
    public int helpRes() { return R.string.help_ai; }
}
