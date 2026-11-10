package ohi.andre.consolelauncher.commands.main.raw;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.managers.HybridCommandInterpreter;

import java.util.Map;

/**
 * Native T-UI command wrapper for MacroDroid execution.
 * Usage: macro "Macro Name" var1="value" var2=123
 */
public class macro implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) throws Exception {
        MainPack info = (MainPack) pack;
        String rawArgs = info.getString();
        if (rawArgs == null || rawArgs.trim().isEmpty()) {
            return info.context.getString(helpRes());
        }

        HybridCommandInterpreter interpreter = new HybridCommandInterpreter(info.context, info);
        HybridCommandInterpreter.CommandResult result = interpreter.handleInput("macro " + rawArgs);

        if (result instanceof HybridCommandInterpreter.CommandResult.MacroTriggered) {
            HybridCommandInterpreter.CommandResult.MacroTriggered macroRes = (HybridCommandInterpreter.CommandResult.MacroTriggered) result;
            if (macroRes.getIntentSent()) {
                return "Triggered MacroDroid macro: \"" + macroRes.getMacroName() + "\" (" + macroRes.getVariables().size() + " variables attached)";
            } else {
                return "Failed to trigger MacroDroid macro: \"" + macroRes.getMacroName() + "\"";
            }
        }

        return "Invalid macro format. " + info.context.getString(helpRes());
    }

    @Override
    public int[] argType() {
        return new int[]{CommandAbstraction.TEXTLIST};
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public int helpRes() {
        return R.string.help_macro;
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
