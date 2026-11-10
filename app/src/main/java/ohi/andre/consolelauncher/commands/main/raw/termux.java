package ohi.andre.consolelauncher.commands.main.raw;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.RedirectCommand;
import ohi.andre.consolelauncher.managers.TermuxSessionManager;

/**
 * Termux shell session command.
 * Commands:
 *  - termux login : Opens dedicated Termux shell session mode
 *  - termux status : Displays shell availability and environment paths
 */
public class termux extends RedirectCommand {

    @Override
    public String exec(ExecutePack pack) throws Exception {
        MainPack info = (MainPack) pack;
        String rawArgs = info.getString();
        TermuxSessionManager manager = new TermuxSessionManager(info.context);

        if (rawArgs == null || rawArgs.trim().isEmpty()) {
            return info.context.getString(helpRes());
        }

        String subCommand = rawArgs.trim().toLowerCase();

        if (subCommand.startsWith("status")) {
            TermuxSessionManager.ShellStatus status = manager.checkStatus();
            StringBuilder sb = new StringBuilder();
            sb.append("--- Termux Shell Status ---\n");
            sb.append("Shell Available : ").append(status.isAvailable() ? "YES" : "NO").append("\n");
            sb.append("Active Session  : ").append(status.getActiveSession() ? "LOGGED IN" : "INACTIVE").append("\n");
            sb.append("Shell Binary    : ").append(status.getShellPath()).append("\n");
            sb.append("HOME Directory  : ").append(status.getHomeDir()).append("\n");
            sb.append("PREFIX Directory: ").append(status.getPrefixDir()).append("\n");
            sb.append("PATH Environment: ").append(status.getPathEnv());
            return sb.toString();
        } else if (subCommand.startsWith("login")) {
            TermuxSessionManager.Companion.setSessionState(true);
            beforeObjects.clear();
            afterObjects.clear();
            info.redirectator.prepareRedirection(this);
            return "Logged into dedicated Termux shell session.\nType shell commands directly. Type 'exit' or 'logout' to quit termux session.";
        }

        return info.context.getString(helpRes());
    }

    @Override
    public String onRedirect(ExecutePack pack) {
        MainPack info = (MainPack) pack;
        if (afterObjects.isEmpty()) {
            return null;
        }

        String input = (String) afterObjects.remove(0);
        String trimmed = input.trim();

        if (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("logout")) {
            TermuxSessionManager.Companion.setSessionState(false);
            info.redirectator.cleanup();
            cleanup();
            return "Exited dedicated Termux shell session.";
        }

        TermuxSessionManager manager = new TermuxSessionManager(info.context);
        String result = manager.executeDirectShellCommand(input, 15);

        // Keep redirection active for continuous shell session
        info.redirectator.prepareRedirection(this);
        return result;
    }

    @Override
    public int getHint() {
        return 0;
    }

    @Override
    public boolean isWaitingPermission() {
        return false;
    }

    @Override
    public int[] argType() {
        return new int[]{COMMAND};
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public int helpRes() {
        return R.string.help_termux;
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
