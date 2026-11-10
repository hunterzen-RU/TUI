package ohi.andre.consolelauncher.commands.main.raw;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.managers.TermuxSessionManager;
import ohi.andre.consolelauncher.tuils.Tuils;

import java.util.List;

public class tui_run implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) throws Exception {
        MainPack info = (MainPack) pack;
        String rawCommand = null;

        Object argObj = pack.get();
        if (argObj instanceof String) {
            rawCommand = (String) argObj;
        } else if (argObj instanceof List) {
            rawCommand = Tuils.toPlanString((List) argObj, " ");
        }

        if (rawCommand == null || rawCommand.trim().isEmpty()) {
            return info.context.getString(helpRes());
        }

        String cmd = rawCommand.trim();
        
        // Wrapper logic: /data/data/com.termux/files/usr/bin/login -d /data/data/com.termux/files/usr/bin/"$@"
        String fullCmd = "/data/data/com.termux/files/usr/bin/login -d /data/data/com.termux/files/usr/bin/" + cmd;

        TermuxSessionManager manager = new TermuxSessionManager(info.context);
        return manager.executeDirectShellCommand(fullCmd, 60);
    }

    @Override
    public int[] argType() {
        return new int[]{CommandAbstraction.TEXTLIST};
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public int helpRes() {
        return R.string.help_tui_run;
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return pack.context.getString(helpRes());
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int index) {
        return null;
    }
}
