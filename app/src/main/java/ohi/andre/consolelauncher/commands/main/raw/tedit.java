package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Intent;

import java.io.File;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.tuils.TuiEditorActivity;
import ohi.andre.consolelauncher.tuils.Tuils;

public class tedit implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) throws Exception {
        MainPack info = (MainPack) pack;
        String pathStr = pack.getString();
        
        if (pathStr == null || pathStr.trim().isEmpty()) {
            return info.context.getString(helpRes());
        }

        File file = new File(pathStr.trim());
        if (!file.isAbsolute()) {
            file = new File(Tuils.getFolder(), pathStr.trim());
        }

        // Ensure .tui extension if not provided, or just allow editing anything
        if (!file.getName().contains(".")) {
            file = new File(file.getParent(), file.getName() + ".tui");
        }

        if (!file.exists()) {
            file.createNewFile();
        }

        Intent intent = new Intent(info.context, TuiEditorActivity.class);
        intent.putExtra(TuiEditorActivity.FILE_PATH_KEY, file.getAbsolutePath());
        info.context.startActivity(intent);

        return "Opening editor for: " + file.getName();
    }

    @Override
    public int[] argType() {
        return new int[]{CommandAbstraction.FILE};
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public int helpRes() {
        return R.string.help_tedit;
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return pack.context.getString(helpRes());
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int index) {
        return pack.context.getString(R.string.output_filenotfound);
    }
}
