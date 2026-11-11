package ohi.andre.consolelauncher.commands.main.raw;

import java.util.List;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.managers.TermuxSessionManager;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * Native T-UI command 'shell <command>' to execute shell commands directly,
 * bypassing the command interpreter and MacroDroid evaluation.
 */
public class shell implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) throws Exception {
        MainPack info = (MainPack) pack;
        String rawCommand = null;

        Object argObj = pack.get();
        if (argObj instanceof String) {
            rawCommand = (String) argObj;
        } else if (argObj instanceof List) {
            rawCommand = Tuils.toPlanString((List) argObj, " ");
        } else if (argObj instanceof Object[]) {
            Object[] arr = (Object[]) argObj;
            StringBuilder sb = new StringBuilder();
            for (Object item : arr) {
                if (sb.length() > 0) sb.append(" ");
                if (item instanceof List) {
                    sb.append(Tuils.toPlanString((List) item, " "));
                } else {
                    sb.append(String.valueOf(item));
                }
            }
            rawCommand = sb.toString();
        } else if (pack.args != null && pack.args.length > 0) {
            Object firstArg = pack.args[0];
            if (firstArg instanceof String) {
                rawCommand = (String) firstArg;
            } else if (firstArg instanceof List) {
                rawCommand = Tuils.toPlanString((List) firstArg, " ");
            } else if (firstArg != null) {
                rawCommand = firstArg.toString();
            }
        }

        if (rawCommand == null || rawCommand.trim().isEmpty()) {
            return info.context.getString(helpRes());
        }

        TermuxSessionManager manager = new TermuxSessionManager(info.context);
        String result = manager.executeDirectShellCommand(rawCommand.trim(), 15);
        return result != null ? result : "";
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
        return R.string.help_shell;
    }

    @Override
    public String onNotArgEnough(ExecutePack info, int nArgs) {
        return info.context.getString(helpRes());
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int index) {
        return null;
    }
}
