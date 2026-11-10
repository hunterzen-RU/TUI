package ohi.andre.consolelauncher.commands.main.raw;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.managers.MacroHistoryDbHelper;

import java.util.List;

/**
 * Native T-UI command to view the last 20 MacroDroid execution logs from SQLite storage.
 * Usage: history [clear]
 */
public class history implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) throws Exception {
        MainPack info = (MainPack) pack;
        String argsString = info.getString();
        MacroHistoryDbHelper dbHelper = new MacroHistoryDbHelper(info.context);

        if (argsString != null && argsString.trim().equalsIgnoreCase("clear")) {
            dbHelper.clearHistory();
            return "MacroDroid execution history cleared.";
        }

        List<MacroHistoryDbHelper.MacroLogEntry> history = dbHelper.getRecentHistory(20);
        if (history == null || history.isEmpty()) {
            return "No MacroDroid execution history found.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("--- MacroDroid Execution Log (Last ").append(history.size()).append(") ---\n");
        for (MacroHistoryDbHelper.MacroLogEntry entry : history) {
            sb.append("[").append(entry.formattedTime()).append("] ")
              .append("\"").append(entry.getMacroName()).append("\" ")
              .append("(").append(entry.formattedVariables()).append(") ")
              .append("- ").append(entry.getStatus()).append("\n");
        }

        return sb.toString().trim();
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
        return R.string.help_history;
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
