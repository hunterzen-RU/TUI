package ohi.andre.consolelauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Parcelable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.Map;

import ohi.andre.consolelauncher.commands.Command;
import ohi.andre.consolelauncher.commands.CommandGroup;
import ohi.andre.consolelauncher.commands.CommandTuils;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.raw.location;
import ohi.andre.consolelauncher.commands.main.specific.RedirectCommand;
import ohi.andre.consolelauncher.managers.AliasManager;
import ohi.andre.consolelauncher.managers.AptPackageResolver;
import ohi.andre.consolelauncher.managers.AppsManager;
import ohi.andre.consolelauncher.managers.HybridCommandInterpreter;
import ohi.andre.consolelauncher.managers.ChangelogManager;
import ohi.andre.consolelauncher.managers.ContactManager;
import ohi.andre.consolelauncher.managers.HTMLExtractManager;
import ohi.andre.consolelauncher.managers.PackageHelper;
import ohi.andre.consolelauncher.managers.PackageManager;
import ohi.andre.consolelauncher.managers.ProotDistroManager;
import ohi.andre.consolelauncher.managers.RssManager;
import ohi.andre.consolelauncher.managers.TermuxSessionManager;
import ohi.andre.consolelauncher.managers.TerminalManager;
import ohi.andre.consolelauncher.managers.ThemeManager;
import ohi.andre.consolelauncher.managers.TimeManager;
import ohi.andre.consolelauncher.managers.TuiLocationManager;
import ohi.andre.consolelauncher.managers.music.MusicManager2;
import ohi.andre.consolelauncher.managers.music.MusicService;
import ohi.andre.consolelauncher.managers.notifications.KeeperService;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver;
import ohi.andre.consolelauncher.tuils.StoppableThread;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter;
import ohi.andre.consolelauncher.tuils.interfaces.OnRedirectionListener;
import ohi.andre.consolelauncher.tuils.interfaces.Redirectator;
import ohi.andre.consolelauncher.tuils.libsuperuser.Shell;
import ohi.andre.consolelauncher.tuils.libsuperuser.ShellHolder;
import okhttp3.Cache;
import okhttp3.OkHttpClient;

/*Copyright Francesco Andreuzzi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/

public class MainManager {

    public static String ACTION_EXEC = BuildConfig.APPLICATION_ID + ".main_exec";
    public static String CMD = "cmd", NEED_WRITE_INPUT = "writeInput", ALIAS_NAME = "aliasName", PARCELABLE = "parcelable", CMD_COUNT = "cmdCount", MUSIC_SERVICE = "musicService";

    private RedirectCommand redirect;
    private Redirectator redirectator = new Redirectator() {
        @Override
        public void prepareRedirection(RedirectCommand cmd) {
            redirect = cmd;

            if(redirectionListener != null) {
                redirectionListener.onRedirectionRequest(cmd);
            }
        }

        @Override
        public void cleanup() {
            if(redirect != null) {
                redirect.beforeObjects.clear();
                redirect.afterObjects.clear();

                if(redirectionListener != null) {
                    redirectionListener.onRedirectionEnd(redirect);
                }

                redirect = null;
            }
        }
    };
    private OnRedirectionListener redirectionListener;
    public void setRedirectionListener(OnRedirectionListener redirectionListener) {
        this.redirectionListener = redirectionListener;
    }

    private final String COMMANDS_PKG = "ohi.andre.consolelauncher.commands.main.raw";

    private CmdTrigger[] triggers = new CmdTrigger[] {
            new MacroTrigger(),
            new GroupTrigger(),
            new AliasTrigger(),
            new PkgCmdTrigger(),
            new TuiCommandTrigger(),
            new StandalonePackageTrigger(),
            new AppTrigger(),
            new ShellCommandTrigger()
    };
    private MainPack mainPack;

    private LauncherActivity mContext;

    private boolean showAliasValue;
    private boolean showAppHistory;
    private int aliasContentColor;

    private String multipleCmdSeparator;

    public static Shell.Interactive interactive;

    private AliasManager aliasManager;
    private RssManager rssManager;
    private AppsManager appsManager;
    private ContactManager contactManager;
    private MusicManager2 musicManager2;
    private ThemeManager themeManager;
    private HTMLExtractManager htmlExtractManager;

    private BroadcastReceiver receiver;

    public static int commandCount = 0;

    private boolean keeperServiceRunning;

    protected MainManager(LauncherActivity c) {
        mContext = c;

        keeperServiceRunning = XMLPrefsManager.getBoolean(Behavior.tui_notification);

        showAliasValue = XMLPrefsManager.getBoolean(Behavior.show_alias_content);
        showAppHistory = XMLPrefsManager.getBoolean(Behavior.show_launch_history);
        aliasContentColor = XMLPrefsManager.getColor(Theme.alias_content_color);

        multipleCmdSeparator = XMLPrefsManager.get(Behavior.multiple_cmd_separator);

        CommandGroup group = new CommandGroup(mContext, COMMANDS_PKG);

        try {
            contactManager = new ContactManager(mContext);
        } catch (NullPointerException e) {
            Tuils.log(e);
        }

        appsManager = new AppsManager(c);
        aliasManager = new AliasManager(mContext);

        final OkHttpClient client = new OkHttpClient.Builder()
                .cache(new Cache(mContext.getCacheDir(), 10*1024*1024))
                .build();

//        new Thread() {
//            @Override
//            public void run() {
//                super.run();
//
//                int lat = -90, lon = 0;
//
//                for(int j = 0; j < 120; j++) {
//                    Tuils.log("----------------" + j + "----------------");
//
//                    try {
//                        Request.Builder builder = new Request.Builder()
//                                .url("http://api.openweathermap.org/data/2.5/weather?lat=" + lat++ + "&lon=" + lon++ + "&appid=1f798f99228596c20ccfda51b9771a86&units=metric")
//                                .cacheControl(CacheControl.FORCE_NETWORK)
//                                .get();
//
//                        Response response = client.newCall(builder.build()).execute();
//
//                        Tuils.log("code", response.code());
//                        if (!response.isSuccessful()) {
//                            Tuils.log("not succesfull");
//                            return;
//                        }
//
//                        InputStream inputStream = response.body().byteStream();
//                        String json = Tuils.inputStreamToString(inputStream);
//                        Tuils.log(json);
//                    } catch (Exception e) {
//                        Tuils.log(e);
//                    }
//                }
//            }
//        }.start();

        rssManager = new RssManager(mContext, client);
        themeManager = new ThemeManager(client, mContext, c);
        musicManager2 = XMLPrefsManager.getBoolean(Behavior.enable_music) ? new MusicManager2(mContext) : null;
        htmlExtractManager = new HTMLExtractManager(mContext, client);

        mainPack = new MainPack(mContext, group, aliasManager, appsManager, musicManager2, contactManager, redirectator, rssManager, client);

        ShellHolder shellHolder = new ShellHolder(mContext);
        interactive = shellHolder.build();
        mainPack.shellHolder = shellHolder;

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_EXEC);
        filter.addAction(location.ACTION_LOCATION_CMD_GOT);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(ACTION_EXEC)) {
                    String cmd = intent.getStringExtra(CMD);
                    if (cmd == null) cmd = intent.getStringExtra(PrivateIOReceiver.TEXT);

                    if (cmd == null) {
                        return;
                    }

                    int cmdCount = intent.getIntExtra(CMD_COUNT, -1);
                    if (cmdCount < commandCount) return;
                    commandCount++;

                    String aliasName = intent.getStringExtra(ALIAS_NAME);
                    boolean needWriteInput = intent.getBooleanExtra(NEED_WRITE_INPUT, false);
                    Parcelable p = intent.getParcelableExtra(PARCELABLE);

                    if(needWriteInput) {
                        Intent i = new Intent(PrivateIOReceiver.ACTION_INPUT);
                        i.putExtra(PrivateIOReceiver.TEXT, cmd);
                        LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(i);
                    }

                    if(p != null && p instanceof AppsManager.LaunchInfo) {
                        onCommand(cmd, (AppsManager.LaunchInfo) p, intent.getBooleanExtra(MainManager.MUSIC_SERVICE, false));
                    } else {
                        onCommand(cmd, aliasName, intent.getBooleanExtra(MainManager.MUSIC_SERVICE, false));
                    }
                } else if(action.equals(location.ACTION_LOCATION_CMD_GOT)) {
                    Tuils.sendOutput(context, "Lat: " + intent.getDoubleExtra(TuiLocationManager.LATITUDE, 0) + "; Long: " + intent.getDoubleExtra(TuiLocationManager.LONGITUDE, 0));
                    TuiLocationManager.instance(context).rm(location.ACTION_LOCATION_CMD_GOT);
                }
            }
        };

        LocalBroadcastManager.getInstance(mContext.getApplicationContext()).registerReceiver(receiver, filter);
    }

    private void updateServices(String cmd, boolean wasMusicService) {

        if(keeperServiceRunning) {
            Intent i = new Intent(mContext, KeeperService.class);
            i.putExtra(KeeperService.CMD_KEY, cmd);
            i.putExtra(KeeperService.PATH_KEY, mainPack.currentDirectory.getAbsolutePath());
            mContext.startService(i);
        }

        if(wasMusicService) {
            Intent i = new Intent(mContext, MusicService.class);
            mContext.startService(i);
        }
    }

    public void onCommand(String input, AppsManager.LaunchInfo launchInfo, boolean wasMusicService) {
        if(launchInfo == null) {
            onCommand(input, (String) null, wasMusicService);
            return;
        }

        updateServices(input, wasMusicService);

        if(launchInfo.unspacedLowercaseLabel.equals(Tuils.removeSpaces(input.toLowerCase()))) {
            performLaunch(mainPack, launchInfo, input);
        } else {
            onCommand(input, (String) null, wasMusicService);
        }
    }

    Pattern colorExtractor = Pattern.compile("(#[^(]{6})\\[([^\\)]*)\\]", Pattern.CASE_INSENSITIVE);

//    command manager
    public void onCommand(String input, String alias, boolean wasMusicService) {
        input = Tuils.removeUnncesarySpaces(input);
        
        // Resolve variables for all input
        input = ohi.andre.consolelauncher.managers.VariablesManager.resolveVariables(mContext, input, null);

        // Redirect 'set var=val' to 'variables -set var=val'
        if (input.toLowerCase().startsWith("set ") && !input.contains(" -")) {
            input = "variables -set " + input.substring(4).trim();
        }

        if(alias == null) updateServices(input, wasMusicService);

        if(redirect != null) {
            if(!redirect.isWaitingPermission()) {
                redirect.afterObjects.add(input);
            }
            String output = redirect.onRedirect(mainPack);
            Tuils.sendOutput(mContext, output);

            return;
        }

        if(alias != null && showAliasValue) {
           Tuils.sendOutput(aliasContentColor, mContext, aliasManager.formatLabel(alias, input));
        }

        String[] cmds;
        if(multipleCmdSeparator.length() > 0) {
            cmds = input.split(multipleCmdSeparator);
        } else {
            cmds = new String[] {input};
        }

        int[] colors = new int[cmds.length];
        for(int c = 0; c < colors.length; c++) {
            Matcher m = colorExtractor.matcher(cmds[c]);
            if(m.matches()) {
                try {
                    colors[c] = Color.parseColor(m.group(1));
                    cmds[c] = m.group(2);
                } catch (Exception e) {
                    colors[c] = TerminalManager.NO_COLOR;
                }
            } else colors[c] = TerminalManager.NO_COLOR;
        }

        for(int c = 0; c < cmds.length; c++) {
            mainPack.clear();
            mainPack.commandColor = colors[c];

            for (CmdTrigger trigger : triggers) {
                boolean r;
                try {
                    r = trigger.trigger(mainPack, cmds[c]);
                } catch (Exception e) {
                    Tuils.sendOutput(mContext, Tuils.getStackTrace(e));
                    break;
                }
                if (r) {
                    break;
                }
            }
        }
    }

    public void onLongBack() {
        Tuils.sendInput(mContext, Tuils.EMPTYSTRING);
    }

    public void sendPermissionNotGrantedWarning() {
        redirectator.cleanup();
    }

    public void dispose() {
        mainPack.dispose();
    }

    public void destroy() {
        mainPack.destroy();
        TuiLocationManager.disposeStatic();

        themeManager.dispose();
        htmlExtractManager.dispose(mContext);
        aliasManager.dispose();
        LocalBroadcastManager.getInstance(mContext.getApplicationContext()).unregisterReceiver(receiver);

        new StoppableThread() {
            @Override
            public void run() {
                super.run();

                try {
                    interactive.kill();
                    interactive.close();
                } catch (Exception e) {
                    Tuils.log(e);
                    Tuils.toFile(e);
                }
            }
        }.start();
    }

    public MainPack getMainPack() {
        return mainPack;
    }

    public CommandExecuter executer() {
        return (input, obj) -> {
            AppsManager.LaunchInfo li = obj instanceof AppsManager.LaunchInfo ? (AppsManager.LaunchInfo) obj : null;

            onCommand(input, li, false);
        };
    }

//
    String appFormat;
    int outputColor;

    Pattern pa = Pattern.compile("%a", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    Pattern pp = Pattern.compile("%p", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    Pattern pl = Pattern.compile("%l", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);

    public boolean performLaunch(MainPack mainPack, AppsManager.LaunchInfo i, String input) {
        Intent intent = appsManager.getIntent(i);
        if (intent == null) {
            return false;
        }

        if(showAppHistory) {
            if(appFormat == null) {
                appFormat = XMLPrefsManager.get(Behavior.app_launch_format);
                outputColor = XMLPrefsManager.getColor(Theme.output_color);
            }

            String a = new String(appFormat);
            a = pa.matcher(a).replaceAll(Matcher.quoteReplacement(intent.getComponent().getClassName()));
            a = pp.matcher(a).replaceAll(Matcher.quoteReplacement(intent.getComponent().getPackageName()));
            a = pl.matcher(a).replaceAll(Matcher.quoteReplacement(i.publicLabel));
            a = Tuils.patternNewline.matcher(a).replaceAll(Matcher.quoteReplacement(Tuils.NEWLINE));

            SpannableString text = new SpannableString(a);
            text.setSpan(new ForegroundColorSpan(outputColor), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            CharSequence s = TimeManager.instance.replace(text);

            Tuils.sendOutput(mainPack, s, TerminalManager.CATEGORY_OUTPUT);
        }

        mainPack.context.startActivity(intent);

        return true;
    }
//

    public interface CmdTrigger {
        boolean trigger(MainPack info, String input) throws Exception;
    }

    private class MacroTrigger implements CmdTrigger {
        @Override
        public boolean trigger(MainPack info, String input) throws Exception {
            // Check if recording is active, capture input unless it's a macro management command
            ohi.andre.consolelauncher.managers.MacroEngine macroEngine = ohi.andre.consolelauncher.managers.MacroEngine.Companion.getInstance(info.context);
            if (macroEngine.isRecording()) {
                macroEngine.recordCommand(input);
            }

            HybridCommandInterpreter interpreter = new HybridCommandInterpreter(info.context, info);
            HybridCommandInterpreter.CommandResult result = interpreter.handleInput(input);
            if (result instanceof HybridCommandInterpreter.CommandResult.Scheduled) {
                HybridCommandInterpreter.CommandResult.Scheduled sched = (HybridCommandInterpreter.CommandResult.Scheduled) result;
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                String nextStr = sdf.format(new java.util.Date(sched.getNextRunMillis()));
                Tuils.sendOutput(mContext, "Scheduled job [" + sched.getTag() + "] with cron \"" + sched.getCronExpr() + "\"\nNext run: " + nextStr + "\nAction: " + sched.getAction());
                return true;
            } else if (result instanceof HybridCommandInterpreter.CommandResult.ActionExecuted) {
                HybridCommandInterpreter.CommandResult.ActionExecuted act = (HybridCommandInterpreter.CommandResult.ActionExecuted) result;
                Tuils.sendOutput(mContext, act.getOutput());
                return true;
            } else if (result instanceof HybridCommandInterpreter.CommandResult.MacroTriggered) {
                HybridCommandInterpreter.CommandResult.MacroTriggered macroRes = (HybridCommandInterpreter.CommandResult.MacroTriggered) result;
                if (!macroEngine.hasMacro(macroRes.getMacroName())) {
                    if (macroRes.getIntentSent()) {
                        Tuils.sendOutput(mContext, "Macro: Triggered \"" + macroRes.getMacroName() + "\" (" + macroRes.getVariables().size() + " vars)");
                    } else {
                        Tuils.sendOutput(mContext, "Macro: Failed to dispatch \"" + macroRes.getMacroName() + "\"");
                    }
                }
                return true;
            }
            return false;
        }
    }

    private class AliasTrigger implements CmdTrigger {

        @Override
        public boolean trigger(MainPack info, String input) {
            String alias[] = aliasManager.getAlias(input, true);

            String aliasValue = alias[0];
            if (alias[0] == null) {
                return false;
            }

            String aliasName = alias[1];
            String residual = alias[2];

            aliasValue = aliasManager.format(aliasValue, residual);

            onCommand(aliasValue, aliasName, false);

            return true;
        }
    }

    private class GroupTrigger implements CmdTrigger {

        @Override
        public boolean trigger(MainPack info, String input) throws Exception {
            int index = input.indexOf(Tuils.SPACE);
            String name;

            if(index != -1) {
                name = input.substring(0,index);
                input = input.substring(index + 1);
            } else {
                name = input;
                input = null;
            }

            List<? extends Group> appGroups = info.appsManager.groups;
            if(appGroups != null) {
                for(Group g : appGroups) {
                    if(name.equals(g.name())) {
                        if(input == null) {
                            Tuils.sendOutput(mContext, AppsManager.AppUtils.printApps(AppsManager.AppUtils.labelList((List<AppsManager.LaunchInfo>) g.members(), false)));
                            return true;
                        } else {
                            return g.use(mainPack, input);
                        }
                    }
                }
            }

            return false;
        }
    }

    private class ShellCommandTrigger implements CmdTrigger {

        final int CD_CODE = 10;
        final int PWD_CODE = 11;

        final Shell.OnCommandResultListener result = new Shell.OnCommandResultListener() {
            @Override
            public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                if(commandCode == CD_CODE) {
                    interactive.addCommand("pwd", PWD_CODE, result);
                } else if(commandCode == PWD_CODE && output.size() == 1) {
                    File f = new File(output.get(0));
                    if(f.exists()) {
                        mainPack.currentDirectory = f;

                        LocalBroadcastManager.getInstance(mContext.getApplicationContext()).sendBroadcast(new Intent(UIManager.ACTION_UPDATE_HINT));
                    }
                }
            }
        };

        @Override
        public boolean trigger(final MainPack info, final String input) throws Exception {
            final String trimmed = input.trim();
            final String cmd = trimmed.split(" ")[0];

            new StoppableThread() {
                @Override
                public void run() {
                    if(input.trim().equalsIgnoreCase("su")) {
                        if(Shell.SU.available()) LocalBroadcastManager.getInstance(mContext.getApplicationContext()).sendBroadcast(new Intent(UIManager.ACTION_ROOT));
                        interactive.addCommand("su");

                    } else if(input.contains("cd ")) {
                        interactive.addCommand(input, CD_CODE, result);
                    } else interactive.addCommand(input);

                }
            }.start();

            return true;
        }
    }

    private class PkgCmdTrigger implements CmdTrigger {
        @Override
        public boolean trigger(final MainPack info, final String input) throws Exception {
            final String trimmed = input.trim();
            if (!trimmed.startsWith("pkg") && !trimmed.startsWith("apt")) {
                return false;
            }

            String[] parts = trimmed.split("\\s+");
            if (parts.length == 0) return false;

            String cmdName = parts[0].toLowerCase();
            if (!cmdName.equals("pkg") && !cmdName.equals("apt") && !cmdName.equals("apt-get")) {
                return false;
            }

            File termuxPkg = new File("/data/data/com.termux/files/usr/bin/pkg");
            File termuxApt = new File("/data/data/com.termux/files/usr/bin/apt");

            if (termuxPkg.exists() || termuxApt.exists()) {
                new StoppableThread() {
                    @Override
                    public void run() {
                        String result = PackageHelper.executeViaTermuxWrapper(info.context, trimmed);
                        Tuils.sendOutput(info, result, TerminalManager.CATEGORY_OUTPUT);
                    }
                }.start();
                return true;
            }

            // Standalone mode (Termux NOT installed)
            if (parts.length >= 2) {
                String subCmd = parts[1].toLowerCase();
                if (subCmd.equals("install") || subCmd.equals("add")) {
                    if (parts.length >= 3) {
                        String pkgName = parts[2];
                        Tuils.sendOutput(info.context, "T-UI Standalone Package Manager: Installing '" + pkgName + "'...");
                        PackageManager.installPackage(info.context, pkgName, new PackageManager.InstallCallback() {
                            @Override
                            public void onSuccess(String message) {
                                Tuils.sendOutput(info, message, TerminalManager.CATEGORY_OUTPUT);
                            }
                            @Override
                            public void onError(String error) {
                                Tuils.sendOutput(android.graphics.Color.RED, info.context, "Install Error: " + error);
                            }
                        });
                        return true;
                    } else {
                        Tuils.sendOutput(info, "Usage: " + cmdName + " install <package>", TerminalManager.CATEGORY_OUTPUT);
                        return true;
                    }
                } else if (subCmd.equals("remove") || subCmd.equals("uninstall") || subCmd.equals("purge")) {
                    if (parts.length >= 3) {
                        String pkgName = parts[2];
                        boolean removed = PackageManager.removeInstalledPackage(info.context, pkgName);
                        if (removed) {
                            Tuils.sendOutput(info, "Successfully removed '" + pkgName + "'", TerminalManager.CATEGORY_OUTPUT);
                        } else {
                            Tuils.sendOutput(info, "Package '" + pkgName + "' is not installed.", TerminalManager.CATEGORY_OUTPUT);
                        }
                        return true;
                    }
                } else if (subCmd.equals("update") || subCmd.equals("upgrade") || subCmd.equals("refresh")) {
                    Tuils.sendOutput(info.context, "Refreshing repository indices...");
                    PackageManager.fetchRepoIndex(info.context, new PackageManager.RepoCallback() {
                        @Override
                        public void onSuccess(List<PackageManager.PackageInfo> packages) {
                            Tuils.sendOutput(info, "Custom package repository updated (" + packages.size() + " packages indexed).", TerminalManager.CATEGORY_OUTPUT);
                        }
                        @Override
                        public void onError(String error) {
                            Tuils.sendOutput(android.graphics.Color.RED, info.context, "Custom repo update notice: " + error);
                        }
                    });

                    AptPackageResolver.fetchPackageIndex(info.context, true, new AptPackageResolver.IndexCallback() {
                        @Override
                        public void onSuccess(File indexFile) {
                            Tuils.sendOutput(info, "Official Termux APT index updated (" + (indexFile.length() / 1024) + " KB).", TerminalManager.CATEGORY_OUTPUT);
                        }
                        @Override
                        public void onError(String error) {
                            Tuils.sendOutput(android.graphics.Color.RED, info.context, "APT index update failed: " + error);
                        }
                    });
                    return true;
                } else if (subCmd.equals("list") || subCmd.equals("show")) {
                    Map<String, String> installed = PackageManager.getInstalledPackagesMap(info.context);
                    if (installed.isEmpty()) {
                        Tuils.sendOutput(info, "No standalone packages installed.", TerminalManager.CATEGORY_OUTPUT);
                    } else {
                        StringBuilder sb = new StringBuilder("Installed Standalone Packages:\n");
                        for (Map.Entry<String, String> entry : installed.entrySet()) {
                            sb.append("• ").append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n");
                        }
                        Tuils.sendOutput(info, sb.toString(), TerminalManager.CATEGORY_OUTPUT);
                    }
                    return true;
                }
            }

            Tuils.sendOutput(info, "T-UI Standalone Package Manager v1.0\nCommands: " + cmdName + " install <pkg> | " + cmdName + " remove <pkg> | " + cmdName + " update | " + cmdName + " list", TerminalManager.CATEGORY_OUTPUT);
            return true;
        }
    }

    private class StandalonePackageTrigger implements CmdTrigger {
        @Override
        public boolean trigger(final MainPack info, final String input) throws Exception {
            final String trimmed = input.trim();
            if (trimmed.isEmpty()) return false;

            if (ProotDistroManager.isDistroCommand(trimmed)) {
                ProotDistroManager.executeDistroCommand(info.context, trimmed, new ProotDistroManager.OutputCallback() {
                    @Override
                    public void onOutput(String text) {
                        Tuils.sendOutput(info, text, TerminalManager.CATEGORY_OUTPUT);
                    }
                });
                return true;
            }

            if (ProotDistroManager.hasActiveDistro()) {
                if (trimmed.equals("exit") || trimmed.equals("logout")) {
                    ProotDistroManager.setActiveDistro(null);
                    Tuils.sendOutput(info, "Exited container. Returned to host shell.", TerminalManager.CATEGORY_OUTPUT);
                    return true;
                }
                ProotDistroManager.runInActiveDistro(info.context, trimmed, new ProotDistroManager.OutputCallback() {
                    @Override
                    public void onOutput(String text) {
                        Tuils.sendOutput(info, text, TerminalManager.CATEGORY_OUTPUT);
                    }
                });
                return true;
            }

            String binaryName = trimmed.split("\\s+")[0];

            TermuxSessionManager sessionMgr = new TermuxSessionManager(info.context);
            TermuxSessionManager.SessionConfig config = sessionMgr.createSessionConfig();

            String nativeLibDir = info.context.getApplicationInfo().nativeLibraryDir;
            File nativeLib1 = new File(nativeLibDir, "lib" + binaryName + "_exec.so");
            File nativeLib2 = new File(nativeLibDir, "lib" + binaryName + ".so");
            File nativeLib3 = new File(nativeLibDir, binaryName);

            File tuiBin = new File(config.getHomePath(), "tui/bin/" + binaryName);
            File prefixBin = new File(config.getPrefixPath(), "bin/" + binaryName);
            File termuxBin = new File("/data/data/com.termux/files/usr/bin/" + binaryName);

            final boolean isNativeLib = nativeLib1.exists() || nativeLib2.exists() || nativeLib3.exists();

            if (isNativeLib || tuiBin.exists() || prefixBin.exists() || termuxBin.exists()) {
                new StoppableThread() {
                    @Override
                    public void run() {
                        PackageManager.ensurePackageDependencies(info.context, binaryName);
                        String output;
                        if (isNativeLib) {
                            String[] cmdParts = trimmed.split("\\s+");
                            java.util.List<String> args = new java.util.ArrayList<>();
                            for (int k = 1; k < cmdParts.length; k++) {
                                args.add(cmdParts[k]);
                            }
                            output = sessionMgr.executeNativeLibBinary(binaryName, args, 300);
                        } else {
                            output = sessionMgr.executeDirectShellCommand(trimmed, 300);
                        }
                        if (output != null && !output.isEmpty()) {
                            Tuils.sendOutput(info, output, TerminalManager.CATEGORY_OUTPUT);
                        }
                    }
                }.start();
                return true;
            }
            return false;
        }
    }

    private class AppTrigger implements CmdTrigger {

        @Override
        public boolean trigger(MainPack info, String input) {
            AppsManager.LaunchInfo i = appsManager.findLaunchInfoWithLabel(input, AppsManager.SHOWN_APPS);
            return i != null && performLaunch(info, i, input);
        }
    }

    private class TuiCommandTrigger implements CmdTrigger {

        @Override
        public boolean trigger(final MainPack info, final String input) throws Exception {

            if (input != null && input.contains("|")) {
                String[] stages = input.split("\\|");
                if (stages.length > 1) {
                    final Command firstCmd = CommandTuils.parse(stages[0].trim(), info);
                    if (firstCmd != null) {
                        mainPack.lastCommand = input;
                        new StoppableThread() {
                            @Override
                            public void run() {
                                super.run();
                                try {
                                    String result = processPipeline(info, input);
                                    if (result != null && !result.isEmpty()) {
                                        Tuils.sendOutput(info, result, TerminalManager.CATEGORY_OUTPUT);
                                    }
                                } catch (Exception e) {
                                    Tuils.sendOutput(mContext, Tuils.getStackTrace(e));
                                    Tuils.log(e);
                                }
                            }
                        }.start();
                        return true;
                    }
                }
            }

            final Command command = CommandTuils.parse(input, info);
            if(command == null) {
                android.util.Log.d("MainManager", "Command not parsed: " + input);
                return false;
            }

            mainPack.lastCommand = input;

            new StoppableThread() {
                @Override
                public void run() {
                    super.run();

                    try {
                        Object resultObj = command.exec(info);
                        android.util.Log.d("MainManager", "Command: " + input + ", Result: " + resultObj + " Type: " + (resultObj != null ? resultObj.getClass().getName() : "null"));
                        String output = null;
                        if (resultObj instanceof String) {
                            output = (String) resultObj;
                        } else if (resultObj instanceof List) {
                            output = Tuils.toPlanString((List) resultObj, "\n");
                        } else if (resultObj instanceof CharSequence) {
                            output = ((CharSequence) resultObj).toString();
                        } else if (resultObj != null) {
                            output = String.valueOf(resultObj);
                        }

                        android.util.Log.d("MainManager", "Parsed Output: " + output);
                        if(output != null && !output.isEmpty()) {
                            android.util.Log.d("MainManager", "Sending output: " + output);
                            Tuils.sendOutput(info, output, TerminalManager.CATEGORY_OUTPUT);
                        } else {
                            android.util.Log.d("MainManager", "Result was null or empty, not sending output");
                        }
                    } catch (Exception e) {
                        Tuils.sendOutput(mContext, Tuils.getStackTrace(e));
                        Tuils.log(e);
                    }
                }
            }.start();

            return true;
        }

        private String processPipeline(MainPack info, String rawInput) throws Exception {
            String[] stages = rawInput.split("\\|");
            if (stages.length <= 1) return null;

            String firstCmdStr = stages[0].trim();
            Command firstCommand = CommandTuils.parse(firstCmdStr, info);
            if (firstCommand == null) return null;

            Object resultObj = firstCommand.exec(info);
            String currentOutput = "";
            if (resultObj instanceof String) currentOutput = (String) resultObj;
            else if (resultObj instanceof List) currentOutput = Tuils.toPlanString((List) resultObj, "\n");
            else if (resultObj instanceof CharSequence) currentOutput = ((CharSequence) resultObj).toString();
            else if (resultObj != null) currentOutput = String.valueOf(resultObj);

            for (int i = 1; i < stages.length; i++) {
                String stage = stages[i].trim();
                if (stage.isEmpty()) continue;
                currentOutput = applyPipeFilter(currentOutput, stage);
            }

            return currentOutput;
        }

        private String applyPipeFilter(String text, String filterCmd) {
            if (text == null) text = "";
            String[] tokens = filterCmd.trim().split("\\s+");
            if (tokens.length == 0 || tokens[0].isEmpty()) return text;
            String filterName = tokens[0].toLowerCase();

            String[] lines = text.split("\r?\n");

            if (filterName.equals("grep")) {
                boolean ignoreCase = true;
                boolean invert = false;
                String pattern = "";
                for (int i = 1; i < tokens.length; i++) {
                    if (tokens[i].equals("-v")) invert = true;
                    else if (tokens[i].equals("-i")) ignoreCase = true;
                    else if (tokens[i].equals("-s")) ignoreCase = false;
                    else pattern = tokens[i];
                }
                if (pattern.isEmpty()) return text;
                StringBuilder sb = new StringBuilder();
                for (String line : lines) {
                    boolean match;
                    if (ignoreCase) {
                        match = line.toLowerCase().contains(pattern.toLowerCase());
                    } else {
                        match = line.contains(pattern);
                    }
                    if (invert) match = !match;
                    if (match) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(line);
                    }
                }
                return sb.toString();
            } else if (filterName.equals("head")) {
                int count = 10;
                if (tokens.length > 1) {
                    try { count = Integer.parseInt(tokens[1]); } catch (Exception ignored) {}
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(lines.length, count); i++) {
                    if (i > 0) sb.append("\n");
                    sb.append(lines[i]);
                }
                return sb.toString();
            } else if (filterName.equals("tail")) {
                int count = 10;
                if (tokens.length > 1) {
                    try { count = Integer.parseInt(tokens[1]); } catch (Exception ignored) {}
                }
                int start = Math.max(0, lines.length - count);
                StringBuilder sb = new StringBuilder();
                for (int i = start; i < lines.length; i++) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(lines[i]);
                }
                return sb.toString();
            } else if (filterName.equals("sort")) {
                boolean reverse = tokens.length > 1 && tokens[1].equals("-r");
                java.util.List<String> list = new java.util.ArrayList<>(java.util.Arrays.asList(lines));
                java.util.Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
                if (reverse) java.util.Collections.reverse(list);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append("\n");
                    sb.append(list.get(i));
                }
                return sb.toString();
            } else if (filterName.equals("wc")) {
                int lineCount = lines.length;
                int wordCount = 0;
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        wordCount += trimmed.split("\\s+").length;
                    }
                }
                int charCount = text.length();
                if (tokens.length > 1 && tokens[1].equals("-l")) return String.valueOf(lineCount);
                if (tokens.length > 1 && tokens[1].equals("-w")) return String.valueOf(wordCount);
                if (tokens.length > 1 && tokens[1].equals("-c")) return String.valueOf(charCount);
                return lineCount + " lines, " + wordCount + " words, " + charCount + " chars";
            } else if (filterName.equals("upper")) {
                return text.toUpperCase();
            } else if (filterName.equals("lower")) {
                return text.toLowerCase();
            }

            return text;
        }
    }

    public interface Group {
        List<? extends Object> members();
        boolean use(MainPack mainPack, String input);
        String name();
    }
}
