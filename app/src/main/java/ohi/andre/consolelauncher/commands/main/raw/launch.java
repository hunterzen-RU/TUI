package ohi.andre.consolelauncher.commands.main.raw;

import java.util.ArrayList;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.managers.HybridCommandInterpreter;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * Live action command: 'launch <package_name> [<activity_name>]'
 */
public class launch implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) throws Exception {
        MainPack info = (MainPack) pack;
        ArrayList<String> args = pack.getList();
        if (args == null || args.isEmpty()) {
            return "Usage: launch <package_name> [<activity_name>]";
        }

        String full = "launch " + Tuils.toPlanString(args, " ");
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
        return R.string.help_launch;
    }

    @Override
    public String onNotArgEnough(ExecutePack info, int nArgs) {
        return "Usage: launch <package_name> [<activity_name>]";
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int index) {
        return null;
    }
}
