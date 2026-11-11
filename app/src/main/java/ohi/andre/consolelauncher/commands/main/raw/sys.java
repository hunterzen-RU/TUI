package ohi.andre.consolelauncher.commands.main.raw;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.SystemClock;

import java.io.File;
import java.util.Locale;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.PackageManager;
import ohi.andre.consolelauncher.tuils.Tuils;

public class sys extends ParamCommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {
        info {
            @Override
            public int[] args() { return new int[0]; }

            @Override
            public String exec(ExecutePack pack) {
                return formatSysSummary(pack.context);
            }
        },
        graph {
            @Override
            public int[] args() { return new int[0]; }

            @Override
            public String exec(ExecutePack pack) {
                return formatAsciiVisualizer(pack.context);
            }
        },
        battery {
            @Override
            public int[] args() { return new int[0]; }

            @Override
            public String exec(ExecutePack pack) {
                return formatDetailedBattery(pack.context);
            }
        },
        net {
            @Override
            public int[] args() { return new int[0]; }

            @Override
            public String exec(ExecutePack pack) {
                return formatDetailedNetwork(pack.context);
            }
        },
        storage {
            @Override
            public int[] args() { return new int[0]; }

            @Override
            public String exec(ExecutePack pack) {
                return formatStorageBreakdown(pack.context);
            }
        };

        static Param get(String p) {
            p = p.toLowerCase();
            for (Param p1 : values()) {
                if (p.endsWith(p1.label())) return p1;
            }
            return null;
        }

        static String[] labels() {
            String[] ss = new String[values().length];
            for (int count = 0; count < values().length; count++) {
                ss[count] = values()[count].label();
            }
            return ss;
        }

        @Override
        public String label() { return Tuils.MINUS + name(); }

        @Override
        public String onNotArgEnough(ExecutePack pack, int n) { return formatSysSummary(pack.context); }

        @Override
        public String onArgNotFound(ExecutePack pack, int index) { return null; }
    }

    private static String getProgressBar(int percent, int width) {
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;
        int filled = (percent * width) / 100;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < width; i++) {
            if (i < filled) sb.append("█");
            else sb.append("░");
        }
        sb.append("] ").append(percent).append("%");
        return sb.toString();
    }

    private static String formatAsciiVisualizer(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════ SYSTEM TELEMETRY ═══════════\n");

        // CPU
        int cpu = Tuils.getCpuUsage();
        sb.append("CPU  ").append(getProgressBar(cpu, 18)).append("\n");

        // RAM
        long totalRamMb = Tuils.totalRam();
        long availRamMb = 0;
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            availRamMb = mi.availMem / 1024 / 1024;
        } catch (Exception ignored) {}
        long usedRamMb = Math.max(0, totalRamMb - availRamMb);
        int ramPercent = totalRamMb > 0 ? (int) (usedRamMb * 100 / totalRamMb) : 0;
        sb.append("RAM  ").append(getProgressBar(ramPercent, 18))
          .append(" (").append(Tuils.round(usedRamMb / 1024.0, 1)).append("G / ")
          .append(Tuils.round(totalRamMb / 1024.0, 1)).append("G)\n");

        // Storage
        long totalStorage = (long) Tuils.getTotalInternalMemorySize(Tuils.GIGA);
        long availStorage = (long) Tuils.getAvailableInternalMemorySize(Tuils.GIGA);
        long usedStorage = Math.max(0, totalStorage - availStorage);
        int storagePercent = totalStorage > 0 ? (int) (usedStorage * 100 / totalStorage) : 0;
        sb.append("DISK ").append(getProgressBar(storagePercent, 18))
          .append(" (").append(usedStorage).append("G / ").append(totalStorage).append("G)\n");

        // Battery
        Intent batIntent = context.getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int batLevel = batIntent != null ? batIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) : 0;
        int status = batIntent != null ? batIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) : -1;
        String statusStr = status == BatteryManager.BATTERY_STATUS_CHARGING ? "⚡ Charging" : "Discharging";
        sb.append("BATT ").append(getProgressBar(batLevel, 18)).append(" ").append(statusStr).append("\n");

        sb.append("────────────────────────────────────────\n");
        sb.append("Arch: ").append(PackageManager.getPrimaryAbi())
          .append(" | OS: Android ").append(Build.VERSION.RELEASE)
          .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        return sb.toString();
    }

    private static String formatSysSummary(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hardware: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append(" (").append(Build.DEVICE).append(")\n");
        sb.append("Architecture: ").append(PackageManager.getPrimaryAbi()).append("\n");
        sb.append("Android Version: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");

        long sec = SystemClock.elapsedRealtime() / 1000;
        long days = sec / 86400;
        long hours = (sec % 86400) / 3600;
        long mins = (sec % 3600) / 60;
        sb.append("Uptime: ").append(days > 0 ? days + "d " : "").append(hours).append("h ").append(mins).append("m\n");

        sb.append("CPU Load: ").append(Tuils.getCpuUsage()).append("%\n");

        long totalRam = Tuils.totalRam();
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long availRam = mi.availMem / 1024 / 1024;
            sb.append("RAM: ").append(Tuils.round((totalRam - availRam) / 1024.0, 1)).append("G used / ")
              .append(Tuils.round(totalRam / 1024.0, 1)).append("G total\n");
        } catch (Exception ignored) {}

        long totalDisk = (long) Tuils.getTotalInternalMemorySize(Tuils.GIGA);
        long availDisk = (long) Tuils.getAvailableInternalMemorySize(Tuils.GIGA);
        sb.append("Storage: ").append(totalDisk - availDisk).append("G used / ").append(totalDisk).append("G total\n");

        return sb.toString();
    }

    private static String formatDetailedBattery(Context context) {
        Intent batIntent = context.getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batIntent == null) return "Battery info unavailable";

        int level = batIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int percent = (level * 100) / (scale > 0 ? scale : 100);

        int status = batIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        String statusStr = "Unknown";
        if (status == BatteryManager.BATTERY_STATUS_CHARGING) statusStr = "Charging";
        else if (status == BatteryManager.BATTERY_STATUS_DISCHARGING) statusStr = "Discharging";
        else if (status == BatteryManager.BATTERY_STATUS_FULL) statusStr = "Full";
        else if (status == BatteryManager.BATTERY_STATUS_NOT_CHARGING) statusStr = "Not Charging";

        int plugged = batIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        String source = "Battery";
        if (plugged == BatteryManager.BATTERY_PLUGGED_AC) source = "AC Adapter";
        else if (plugged == BatteryManager.BATTERY_PLUGGED_USB) source = "USB Cable";
        else if (plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS) source = "Wireless Charger";

        int voltage = batIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        int temp = batIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        String tech = batIntent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);

        StringBuilder sb = new StringBuilder();
        sb.append("Battery Level: ").append(percent).append("%\n");
        sb.append("Status: ").append(statusStr).append("\n");
        sb.append("Power Source: ").append(source).append("\n");
        sb.append("Voltage: ").append(voltage).append(" mV\n");
        sb.append("Temperature: ").append(String.format(Locale.US, "%.1f °C", temp / 10.0)).append("\n");
        if (tech != null && !tech.isEmpty()) sb.append("Technology: ").append(tech).append("\n");
        return sb.toString();
    }

    private static String formatDetailedNetwork(Context context) {
        StringBuilder sb = new StringBuilder();
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo active = cm.getActiveNetworkInfo();
            if (active != null && active.isConnected()) {
                sb.append("Connection: ").append(active.getTypeName()).append(" (Connected)\n");
                if (active.getType() == ConnectivityManager.TYPE_WIFI) {
                    WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    WifiInfo wi = wm.getConnectionInfo();
                    if (wi != null) {
                        String ssid = wi.getSSID() != null ? wi.getSSID().replace("\"", "") : "Unknown";
                        sb.append("SSID: ").append(ssid).append("\n");
                        sb.append("Link Speed: ").append(wi.getLinkSpeed()).append(" Mbps\n");
                        sb.append("RSSI / Signal: ").append(wi.getRssi()).append(" dBm\n");
                    }
                } else {
                    sb.append("Subtype: ").append(active.getSubtypeName()).append("\n");
                }
            } else {
                sb.append("Connection: OFFLINE\n");
            }
        } catch (Exception e) {
            sb.append("Network status: Unavailable\n");
        }

        long tx = TrafficStats.getTotalTxBytes();
        long rx = TrafficStats.getTotalRxBytes();
        sb.append("Total Uploaded: ").append(Tuils.formatSize(tx, Tuils.MEGA)).append(" MB\n");
        sb.append("Total Downloaded: ").append(Tuils.formatSize(rx, Tuils.MEGA)).append(" MB\n");
        return sb.toString();
    }

    private static String formatStorageBreakdown(Context context) {
        StringBuilder sb = new StringBuilder();
        File internal = context.getFilesDir();
        long totalInternal = (long) Tuils.getTotalInternalMemorySize(Tuils.GIGA);
        long availInternal = (long) Tuils.getAvailableInternalMemorySize(Tuils.GIGA);
        long usedInternal = totalInternal - availInternal;

        sb.append("Internal Storage Partition:\n");
        sb.append("  Total: ").append(totalInternal).append(" GB\n");
        sb.append("  Used:  ").append(usedInternal).append(" GB\n");
        sb.append("  Free:  ").append(availInternal).append(" GB\n");
        sb.append("  Path:  ").append(internal.getAbsolutePath()).append("\n");

        return sb.toString();
    }

    @Override
    public String[] params() { return Param.labels(); }

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(MainPack pack, String param) {
        return Param.get(param);
    }

    @Override
    protected String doThings(ExecutePack pack) {
        return formatSysSummary(pack.context);
    }

    @Override
    public int priority() { return 3; }

    @Override
    public int helpRes() { return R.string.help_sys; }
}
