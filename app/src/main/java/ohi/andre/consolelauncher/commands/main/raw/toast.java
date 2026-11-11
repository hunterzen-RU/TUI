package ohi.andre.consolelauncher.commands.main.raw;

import android.widget.Toast;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.tuils.Tuils;

public class toast implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) throws Exception {
        String msg = pack.getString();
        if (msg == null || msg.trim().isEmpty()) {
            return pack.context.getString(R.string.output_lessarg);
        }

        int duration = Toast.LENGTH_SHORT;
        String text = msg.trim();
        if (text.startsWith("-long ")) {
            duration = Toast.LENGTH_LONG;
            text = text.substring(6).trim();
        } else if (text.startsWith("-short ")) {
            duration = Toast.LENGTH_SHORT;
            text = text.substring(7).trim();
        }

        final String toastMsg = text;
        final int toastLen = duration;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
            Toast.makeText(pack.context.getApplicationContext(), toastMsg, toastLen).show()
        );

        return "Toast: " + toastMsg;
    }

    @Override
    public int[] argType() {
        return new int[] {CommandAbstraction.PLAIN_TEXT};
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public int helpRes() {
        return R.string.help_toast;
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return pack.context.getString(helpRes());
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int indexNotFound) {
        return pack.context.getString(helpRes());
    }
}
