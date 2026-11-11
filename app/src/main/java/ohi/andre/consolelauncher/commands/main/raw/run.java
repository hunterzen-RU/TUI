package ohi.andre.consolelauncher.commands.main.raw;

import java.io.File;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.tuils.TuiScriptEngine;
import ohi.andre.consolelauncher.tuils.Tuils;

public class run implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) throws Exception {
        String pathStr = pack.getString();
        if (pathStr == null || pathStr.trim().isEmpty()) {
            return pack.context.getString(R.string.output_lessarg);
        }

        File file = new File(pathStr.trim());
        if (!file.isAbsolute()) {
            file = new File(Tuils.getFolder(), pathStr.trim());
        }

        if (!file.exists()) {
            return pack.context.getString(R.string.output_filenotfound) + ": " + pathStr;
        }

        TuiScriptEngine.executeScriptFile(pack.context, file);
        return "Started .tui automation script: " + file.getName();
    }

    @Override
    public int[] argType() {
        return new int[] {CommandAbstraction.FILE};
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public int helpRes() {
        return R.string.help_run;
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return pack.context.getString(helpRes());
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int indexNotFound) {
        return pack.context.getString(R.string.output_filenotfound);
    }
}
