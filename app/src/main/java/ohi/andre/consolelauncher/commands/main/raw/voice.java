package ohi.andre.consolelauncher.commands.main.raw;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.managers.TerminalManager;
import ohi.andre.consolelauncher.managers.VoiceRecognitionManager;
import ohi.andre.consolelauncher.tuils.Tuils;

public class voice implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) {
        final MainPack info = (MainPack) pack;
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        mainHandler.post(() -> {
            Tuils.sendOutput(info.context, "🎙️ Listening for speech input... Speak your command.");
            VoiceRecognitionManager.getInstance().startListening(info.context, null, new VoiceRecognitionManager.VoiceCallback() {
                @Override
                public void onSpeechStart() {
                    Tuils.sendOutput(info.context, "• Recording voice...");
                }

                @Override
                public void onPartialResult(String partialText) {
                    if (info.terminal != null) {
                        info.terminal.setInput(partialText);
                    }
                }

                @Override
                public void onFinalResult(String recognizedCommand) {
                    if (info.terminal != null) {
                        info.terminal.setInput(recognizedCommand);
                        Tuils.sendOutput(info.context, "✓ Recognized: \"" + recognizedCommand + "\"");
                        // Execute recognized command
                        info.terminal.simulateEnter();
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    Tuils.sendOutput(android.graphics.Color.RED, info.context, "Voice input: " + errorMessage);
                }
            });
        });

        return null;
    }

    @Override
    public int[] argType() {
        return new int[0];
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_voice;
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int indexNotFound) {
        return null;
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return null;
    }
}
