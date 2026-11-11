package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Intent;
import android.graphics.Color;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.HashMap;
import java.util.Map;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.ThemeManager;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Suggestions;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;

public class theme extends ParamCommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {

        apply {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                Intent intent = new Intent(ThemeManager.ACTION_APPLY);
                intent.putExtra(ThemeManager.NAME, pack.getString());
                LocalBroadcastManager.getInstance(pack.context.getApplicationContext()).sendBroadcast(intent);
                return null;
            }
        },
        set {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.CONFIG_ENTRY, CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                Object o = pack.get();
                if (!(o instanceof Theme)) {
                    return "Invalid theme element.";
                }
                Theme element = (Theme) o;
                String color = pack.getString();

                try {
                    Color.parseColor(color);
                } catch (Exception e) {
                    return "Invalid color format. Use #RRGGBB or #AARRGGBB";
                }

                XMLPrefsManager.XMLPrefsRoot.THEME.write(element, color);

                try {
                    if (pack.context instanceof Reloadable) {
                        ((Reloadable) pack.context).reload();
                    }
                } catch (Exception e) {}

                return element.label() + " updated to " + color;
            }
        },
        preset {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.THEME_PRESET};
            }

            @Override
            public String exec(ExecutePack pack) {
                String name = pack.getString().toLowerCase();
                
                Map<Theme, String> colors = new HashMap<>();
                Map<Suggestions, String> suggestionColors = new HashMap<>();
                
                // Check if system wallpaper is enabled
                boolean isTransparent = XMLPrefsManager.getBoolean(Ui.system_wallpaper);
                Theme backgroundTarget = isTransparent ? Theme.overlay_color : Theme.bg_color;

                // Set to 100% transparent if wallpaper is active (#00), otherwise solid (#FF)
                String transPrefix = isTransparent ? "#00" : "#FF";

                switch(name) {
                    case "blue":
                        colors.put(backgroundTarget, transPrefix + "001221");
                        colors.put(Theme.input_color, "#00BFFF");
                        colors.put(Theme.output_color, "#E0FFFF");
                        colors.put(Theme.device_color, "#1E90FF");
                        colors.put(Theme.enter_color, "#00BFFF");
                        colors.put(Theme.toolbar_color, "#00BFFF");
                        colors.put(Theme.time_color, "#87CEFA");

                        suggestionColors.put(Suggestions.apps_bg_color, "#0000FF");
                        suggestionColors.put(Suggestions.alias_bg_color, "#4169E1");
                        suggestionColors.put(Suggestions.cmd_bg_color, "#00BFFF");
                        suggestionColors.put(Suggestions.file_bg_color, "#87CEFA");
                        suggestionColors.put(Suggestions.song_bg_color, "#1E90FF");
                        break;
                    case "red":
                        colors.put(backgroundTarget, transPrefix + "210000");
                        colors.put(Theme.input_color, "#FF4500");
                        colors.put(Theme.output_color, "#FFEBEE");
                        colors.put(Theme.device_color, "#B71C1C");
                        colors.put(Theme.enter_color, "#FF0000");
                        colors.put(Theme.toolbar_color, "#FF5252");
                        colors.put(Theme.time_color, "#FF8A80");

                        suggestionColors.put(Suggestions.apps_bg_color, "#FF0000");
                        suggestionColors.put(Suggestions.alias_bg_color, "#DC143C");
                        suggestionColors.put(Suggestions.cmd_bg_color, "#FF4500");
                        suggestionColors.put(Suggestions.file_bg_color, "#FA8072");
                        suggestionColors.put(Suggestions.song_bg_color, "#B22222");
                        break;
                    case "green":
                        colors.put(backgroundTarget, transPrefix + "001B00");
                        colors.put(Theme.input_color, "#00FF41");
                        colors.put(Theme.output_color, "#D5F5E3");
                        colors.put(Theme.device_color, "#2ECC71");
                        colors.put(Theme.enter_color, "#00FF41");
                        colors.put(Theme.toolbar_color, "#27AE60");
                        colors.put(Theme.time_color, "#A9DFBF");

                        suggestionColors.put(Suggestions.apps_bg_color, "#00FF00");
                        suggestionColors.put(Suggestions.alias_bg_color, "#32CD32");
                        suggestionColors.put(Suggestions.cmd_bg_color, "#00FF41");
                        suggestionColors.put(Suggestions.file_bg_color, "#90EE90");
                        suggestionColors.put(Suggestions.song_bg_color, "#228B22");
                        break;
                    case "pink":
                        colors.put(backgroundTarget, transPrefix + "1A0010");
                        colors.put(Theme.input_color, "#FF69B4");
                        colors.put(Theme.output_color, "#FCE4EC");
                        colors.put(Theme.device_color, "#AD1457");
                        colors.put(Theme.enter_color, "#FF1493");
                        colors.put(Theme.toolbar_color, "#F06292");
                        colors.put(Theme.time_color, "#F8BBD0");

                        suggestionColors.put(Suggestions.apps_bg_color, "#FF69B4");
                        suggestionColors.put(Suggestions.alias_bg_color, "#FF1493");
                        suggestionColors.put(Suggestions.cmd_bg_color, "#FFB6C1");
                        suggestionColors.put(Suggestions.file_bg_color, "#FFC0CB");
                        suggestionColors.put(Suggestions.song_bg_color, "#C71585");
                        break;
                    case "bw":
                        colors.put(backgroundTarget, transPrefix + "000000");
                        colors.put(Theme.input_color, "#FFFFFF");
                        colors.put(Theme.output_color, "#CCCCCC");
                        colors.put(Theme.device_color, "#AAAAAA");
                        colors.put(Theme.enter_color, "#FFFFFF");
                        colors.put(Theme.toolbar_color, "#FFFFFF");
                        colors.put(Theme.time_color, "#FFFFFF");

                        suggestionColors.put(Suggestions.apps_bg_color, "#FFFFFF");
                        suggestionColors.put(Suggestions.alias_bg_color, "#EEEEEE");
                        suggestionColors.put(Suggestions.cmd_bg_color, "#DDDDDD");
                        suggestionColors.put(Suggestions.file_bg_color, "#CCCCCC");
                        suggestionColors.put(Suggestions.song_bg_color, "#BBBBBB");
                        
                        suggestionColors.put(Suggestions.apps_text_color, "#000000");
                        suggestionColors.put(Suggestions.alias_text_color, "#000000");
                        suggestionColors.put(Suggestions.cmd_text_color, "#000000");
                        suggestionColors.put(Suggestions.file_text_color, "#000000");
                        suggestionColors.put(Suggestions.song_text_color, "#000000");
                        break;
                    case "cyberpunk":
                        colors.put(backgroundTarget, transPrefix + "0D0615");
                        colors.put(Theme.input_color, "#FCEE09"); 
                        colors.put(Theme.output_color, "#00F0FF"); 
                        colors.put(Theme.device_color, "#FF003C"); 
                        colors.put(Theme.enter_color, "#FCEE09");
                        colors.put(Theme.toolbar_color, "#39FF14"); 
                        colors.put(Theme.time_color, "#00F0FF");

                        suggestionColors.put(Suggestions.apps_bg_color, "#FF003C");
                        suggestionColors.put(Suggestions.alias_bg_color, "#FCEE09");
                        suggestionColors.put(Suggestions.cmd_bg_color, "#00F0FF");
                        suggestionColors.put(Suggestions.file_bg_color, "#39FF14");
                        suggestionColors.put(Suggestions.song_bg_color, "#BC00FF");
                        
                        suggestionColors.put(Suggestions.alias_text_color, "#000000");
                        break;
                    case "matrix":
                        colors.put(backgroundTarget, transPrefix + "000800");
                        colors.put(Theme.input_color, "#00FF41");
                        colors.put(Theme.output_color, "#008F11");
                        colors.put(Theme.device_color, "#00FF66");
                        colors.put(Theme.enter_color, "#00FF41");
                        colors.put(Theme.toolbar_color, "#00FF41");
                        colors.put(Theme.time_color, "#003B00");

                        suggestionColors.put(Suggestions.apps_bg_color, "#008F11");
                        suggestionColors.put(Suggestions.alias_bg_color, "#00FF41");
                        suggestionColors.put(Suggestions.cmd_bg_color, "#003B00");
                        suggestionColors.put(Suggestions.file_bg_color, "#00FF66");
                        suggestionColors.put(Suggestions.song_bg_color, "#008F11");
                        break;
                    case "dracula":
                        colors.put(backgroundTarget, transPrefix + "282A36");
                        colors.put(Theme.input_color, "#F8F8F2");
                        colors.put(Theme.output_color, "#BD93F9");
                        colors.put(Theme.device_color, "#FF79C6");
                        colors.put(Theme.enter_color, "#50FA7B");
                        colors.put(Theme.toolbar_color, "#8BE9FD");
                        colors.put(Theme.time_color, "#6272A4");

                        suggestionColors.put(Suggestions.apps_bg_color, "#BD93F9");
                        suggestionColors.put(Suggestions.alias_bg_color, "#FF79C6");
                        suggestionColors.put(Suggestions.cmd_bg_color, "#8BE9FD");
                        suggestionColors.put(Suggestions.file_bg_color, "#50FA7B");
                        suggestionColors.put(Suggestions.song_bg_color, "#FFB86C");
                        break;
                    case "catppuccin":
                        colors.put(backgroundTarget, transPrefix + "1E1E2E");
                        colors.put(Theme.input_color, "#CDD6F4");
                        colors.put(Theme.output_color, "#89B4FA");
                        colors.put(Theme.device_color, "#CBA6F7");
                        colors.put(Theme.enter_color, "#A6E3A1");
                        colors.put(Theme.toolbar_color, "#FAB387");
                        colors.put(Theme.time_color, "#6C7086");

                        suggestionColors.put(Suggestions.apps_bg_color, "#89B4FA");
                        suggestionColors.put(Suggestions.alias_bg_color, "#CBA6F7");
                        suggestionColors.put(Suggestions.cmd_bg_color, "#F38BA8");
                        suggestionColors.put(Suggestions.file_bg_color, "#A6E3A1");
                        suggestionColors.put(Suggestions.song_bg_color, "#FAB387");
                        break;
                    case "nord":
                        colors.put(backgroundTarget, transPrefix + "2E3440");
                        colors.put(Theme.input_color, "#ECEFF4");
                        colors.put(Theme.output_color, "#88C0D0");
                        colors.put(Theme.device_color, "#81A1C1");
                        colors.put(Theme.enter_color, "#A3BE8C");
                        colors.put(Theme.toolbar_color, "#88C0D0");
                        colors.put(Theme.time_color, "#4C566A");

                        suggestionColors.put(Suggestions.apps_bg_color, "#88C0D0");
                        suggestionColors.put(Suggestions.alias_bg_color, "#81A1C1");
                        suggestionColors.put(Suggestions.cmd_bg_color, "#5E81AC");
                        suggestionColors.put(Suggestions.file_bg_color, "#A3BE8C");
                        suggestionColors.put(Suggestions.song_bg_color, "#EBCB8B");
                        break;
                    case "gruvbox":
                        colors.put(backgroundTarget, transPrefix + "282828");
                        colors.put(Theme.input_color, "#EBDBB2");
                        colors.put(Theme.output_color, "#FE8019");
                        colors.put(Theme.device_color, "#FABD2F");
                        colors.put(Theme.enter_color, "#B8BB26");
                        colors.put(Theme.toolbar_color, "#8EC07C");
                        colors.put(Theme.time_color, "#928374");

                        suggestionColors.put(Suggestions.apps_bg_color, "#FE8019");
                        suggestionColors.put(Suggestions.alias_bg_color, "#FABD2F");
                        suggestionColors.put(Suggestions.cmd_bg_color, "#8EC07C");
                        suggestionColors.put(Suggestions.file_bg_color, "#B8BB26");
                        suggestionColors.put(Suggestions.song_bg_color, "#D3869B");
                        break;
                    default:
                        return "Unknown preset. Available: blue, red, green, pink, bw, cyberpunk, matrix, dracula, catppuccin, nord, gruvbox";
                }

                // Force toolbar_bg to transparent to avoid coloring the entire bar
                colors.put(Theme.toolbar_bg, "#00000000");

                for (Map.Entry<Theme, String> entry : colors.entrySet()) {
                    XMLPrefsManager.XMLPrefsRoot.THEME.write(entry.getKey(), entry.getValue());
                }
                for (Map.Entry<Suggestions, String> entry : suggestionColors.entrySet()) {
                    XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.write(entry.getKey(), entry.getValue());
                }

                try {
                    if (pack.context instanceof Reloadable) {
                        ((Reloadable) pack.context).reload();
                    }
                } catch (Exception e) {}

                return "Applied " + name + " preset!";
            }
        },
        ls {
            @Override
            public int[] args() { return new int[0]; }

            @Override
            public String exec(ExecutePack pack) {
                return "Available Theme Presets:\n" +
                       "• matrix     -> Terminal hacker phosphor green on deep black\n" +
                       "• dracula    -> Iconic purple, pink, and cyan palette\n" +
                       "• catppuccin -> Modern warm pastel mocha aesthetic\n" +
                       "• nord       -> Arctic clean frost blues & snow storm\n" +
                       "• gruvbox    -> Retro warm contrast palette\n" +
                       "• cyberpunk  -> High-voltage neon yellow, cyan, and red\n" +
                       "• blue       -> Deep navy and bright azure\n" +
                       "• red        -> Crimson & crimson glow\n" +
                       "• green      -> Emerald green\n" +
                       "• pink       -> Neon magenta & rose\n" +
                       "• bw         -> High contrast monochrome black & white\n\n" +
                       "Usage: theme -preset [name]";
            }
        },
        standard {
            @Override
            public int[] args() {
                return new int[] {};
            }

            @Override
            public String exec(ExecutePack pack) {
                LocalBroadcastManager.getInstance(pack.context.getApplicationContext()).sendBroadcast(new Intent(ThemeManager.ACTION_STANDARD));
                return null;
            }
        },
        old {
            @Override
            public String exec(ExecutePack pack) {
                LocalBroadcastManager.getInstance(pack.context.getApplicationContext()).sendBroadcast(new Intent(ThemeManager.ACTION_REVERT));
                return null;
            }
        };

        static Param get(String p) {
            p = p.toLowerCase();
            Param[] ps = values();
            for (Param p1 : ps)
                if (p.endsWith(p1.label()))
                    return p1;
            return null;
        }

        static String[] labels() {
            Param[] ps = values();
            String[] ss = new String[ps.length];

            for (int count = 0; count < ps.length; count++) {
                ss[count] = ps[count].label();
            }

            return ss;
        }

        @Override
        public String label() {
            return Tuils.MINUS + name();
        }

        @Override
        public String onNotArgEnough(ExecutePack pack, int n) {
            return pack.context.getString(R.string.help_theme);
        }

        @Override
        public String onArgNotFound(ExecutePack pack, int index) {
            return null;
        }

        @Override
        public int[] args() {
            return new int[0];
        }
    }

    @Override
    public String[] params() {
        return Param.labels();
    };

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(MainPack pack, String param) {
        return Param.get(param);
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public int helpRes() {
        return R.string.help_theme;
    }

    @Override
    protected String doThings(ExecutePack pack) {
        return null;
    }
}
