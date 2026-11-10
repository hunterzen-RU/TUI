package ohi.andre.consolelauncher.commands.main.raw;

import java.util.ArrayList;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.managers.HybridCommandInterpreter;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * Live action command: 'notify "<title>" "<body_text>"'
 */
public class notify implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) throws Exception {
        MainPack info = (MainPack) pack;
        ArrayList<String> args = pack.getList();
        if (args == null || args.isEmpty()) {
            return "Usage: notify \"<title>\" \"<message>\"";
        }

        String full = "notify " + Tuils.toPlanString(args, " ");
        HybridCommandInterpreter interpreter = new HybridCommandInterpreter(info.context, info);
        return interpreter.executeAction(full);
    }

    @Override
    public int[] argType() {
        return new int[]{CommandAbstraction.TEXTLIST};
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_notify;
    }

    @Override
    public String onNotArgEnough(ExecutePack info, int nArgs) {
        return "Usage: notify \"<title>\" \"<message>\"";
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int index) {
        return null;
    }
}
