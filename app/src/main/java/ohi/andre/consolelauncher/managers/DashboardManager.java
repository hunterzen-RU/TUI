package ohi.andre.consolelauncher.managers;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Dashboard;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter;

public class DashboardManager {

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View rootContainer;
    private View gridWrapper;
    private View nativeWrapper;

    private TextView titleTv;
    private TextView timeTv;
    private TextView dotTv;

    private GridLayout gridContainer;

    private String dashboardMode = "grid";
    private boolean dashboardEnabled = true;
    private boolean enableBlur = true;

    private int rows = 2;
    private int cols = 2;
    private int refreshIntervalMs = 1000;
    private String longPressAction = "ai_chat";
    private String baseTitle = "SYS_KERNEL_MATRIX";

    private String fillChar = "█";
    private String emptyChar = "░";

    private int boxBgColor = Color.TRANSPARENT;
    private int boxBorderColor = Color.parseColor("#00FF66");

    private int cpuColor = Color.parseColor("#00FF66");
    private int ramColor = Color.parseColor("#FFD700");
    private int batteryColor = Color.parseColor("#FF5555");
    private int storageColor = Color.parseColor("#00FFFF");
    private int netColor = Color.parseColor("#FF00FF");
    private int uptimeColor = Color.parseColor("#00FF66");

    private ActivityManager activityManager;
    private ActivityManager.MemoryInfo memoryInfo;

    private boolean isRunning = false;
    private boolean dotState = true;

    private boolean isExpanded = false;
    private int originalRows = 2;
    private int originalCols = 2;

    public static DashboardManager instance;

    private static class BoxHolder {
        int index;
        DashboardItem item;
        TextView titleTv;
        TextView detailTv;
        TextView barTv;
        int accentColor;
    }

    private final List<BoxHolder> boxHolders = new ArrayList<>();

    private CommandExecuter commandExecuter;

    public void setCommandExecuter(CommandExecuter executer) {
        this.commandExecuter = executer;
    }

    public DashboardManager(Context context, View rootView) {
        this.context = context;
        instance = this;
        if (rootView == null) return;

        rootContainer = rootView.findViewById(R.id.dashboard_root_container);
        gridWrapper = rootView.findViewById(R.id.dashboard_grid_wrapper);
        nativeWrapper = rootView.findViewById(R.id.native_dashboard_wrapper);

        titleTv = rootView.findViewById(R.id.dashboard_title_tv);
        timeTv = rootView.findViewById(R.id.dashboard_time_tv);
        dotTv = rootView.findViewById(R.id.dashboard_dot_tv);
        gridContainer = rootView.findViewById(R.id.dashboard_grid_container);

        activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        memoryInfo = new ActivityManager.MemoryInfo();

        loadConfig();
        applyModeAndStyling();
        if (dashboardEnabled && "grid".equalsIgnoreCase(dashboardMode)) {
            setupGrid();
        }

        final android.view.GestureDetector swipeDetector = new android.view.GestureDetector(context, new android.view.GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 80;
            private static final int SWIPE_VELOCITY_THRESHOLD = 80;

            @Override
            public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            SessionManager.switchPreviousSession(context);
                        } else {
                            SessionManager.switchNextSession(context);
                        }
                        return true;
                    }
                }
                return false;
            }
        });

        View.OnTouchListener swipeListener = (v, event) -> swipeDetector.onTouchEvent(event);

        if (gridWrapper != null) {
            gridWrapper.setOnTouchListener(swipeListener);
            gridWrapper.setOnClickListener(v -> {
                if ("grid".equalsIgnoreCase(dashboardMode)) {
                    toggleExpand();
                }
            });
            gridWrapper.setOnLongClickListener(v -> {
                handleLongPress();
                return true;
            });
        }
        if (rootContainer != null) {
            rootContainer.setOnTouchListener(swipeListener);
        }
    }

    private void loadConfig() {
        try {
            dashboardMode = XMLPrefsManager.get(Dashboard.dashboard_mode);
            if (dashboardMode == null || dashboardMode.isEmpty()) dashboardMode = "grid";

            dashboardEnabled = XMLPrefsManager.getBoolean(Dashboard.dashboard_enabled);
            enableBlur = XMLPrefsManager.getBoolean(Dashboard.enable_blur_effect);

            String title = XMLPrefsManager.get(Dashboard.dashboard_title);
            if (title != null && !title.isEmpty()) {
                baseTitle = title;
            }
            if (titleTv != null) {
                titleTv.setText(" " + VariablesManager.resolveVariables(context, baseTitle, null));
            }

            String gridLayoutStr = XMLPrefsManager.get(Dashboard.grid_layout);
            int xmlCols = XMLPrefsManager.getInt(Dashboard.grid_columns);
            int xmlRows = XMLPrefsManager.getInt(Dashboard.grid_rows);

            if (gridLayoutStr != null && gridLayoutStr.toLowerCase().contains("x")) {
                String[] parts = gridLayoutStr.toLowerCase().split("x");
                if (parts.length >= 2) {
                    try {
                        xmlRows = Integer.parseInt(parts[0].trim());
                        xmlCols = Integer.parseInt(parts[1].trim());
                    } catch (Exception ignored) {}
                }
            }

            rows = Math.max(1, Math.min(5, xmlRows > 0 ? xmlRows : 2));
            cols = Math.max(1, Math.min(5, xmlCols > 0 ? xmlCols : 2));
            
            if (!isExpanded) {
                originalRows = rows;
                originalCols = cols;
            }

            String fill = XMLPrefsManager.get(Dashboard.bar_fill_char);
            if (fill != null && !fill.isEmpty()) fillChar = fill;

            String empty = XMLPrefsManager.get(Dashboard.bar_empty_char);
            if (empty != null && !empty.isEmpty()) emptyChar = empty;

            refreshIntervalMs = XMLPrefsManager.getInt(Dashboard.refresh_interval_ms);
            if (refreshIntervalMs < 200) refreshIntervalMs = 1000;

            try { boxBgColor = Color.parseColor(XMLPrefsManager.get(Dashboard.box_bg_color)); } catch (Exception e) { boxBgColor = Color.TRANSPARENT; }
            try { boxBorderColor = Color.parseColor(XMLPrefsManager.get(Dashboard.box_border_color)); } catch (Exception e) {}
            try { cpuColor = Color.parseColor(XMLPrefsManager.get(Dashboard.cpu_color)); } catch (Exception e) {}
            try { ramColor = Color.parseColor(XMLPrefsManager.get(Dashboard.ram_color)); } catch (Exception e) {}
            try { batteryColor = Color.parseColor(XMLPrefsManager.get(Dashboard.battery_color)); } catch (Exception e) {}
            try { storageColor = Color.parseColor(XMLPrefsManager.get(Dashboard.storage_color)); } catch (Exception e) {}
            try { netColor = Color.parseColor(XMLPrefsManager.get(Dashboard.net_color)); } catch (Exception e) {}
            try { uptimeColor = Color.parseColor(XMLPrefsManager.get(Dashboard.uptime_color)); } catch (Exception e) {}

            longPressAction = XMLPrefsManager.get(Dashboard.long_press_action);
            if (longPressAction == null) longPressAction = "ai_chat";

        } catch (Exception e) { Tuils.log(e); }
    }

    private void applyModeAndStyling() {
        if (rootContainer != null) {
            rootContainer.setVisibility(dashboardEnabled ? View.VISIBLE : View.GONE);
        }
        if (!dashboardEnabled) return;

        boolean isGrid = "grid".equalsIgnoreCase(dashboardMode);
        if (gridWrapper != null) gridWrapper.setVisibility(isGrid ? View.VISIBLE : View.GONE);
        if (nativeWrapper != null) nativeWrapper.setVisibility(isGrid ? View.GONE : View.VISIBLE);

        if (gridWrapper != null && isGrid) {
            GradientDrawable frostedGlassBackground = new GradientDrawable();
            frostedGlassBackground.setColor(Color.parseColor("#D90B0F19"));
            frostedGlassBackground.setStroke(Tuils.dpToPx(context, 1), Color.parseColor("#6000FF66"));
            frostedGlassBackground.setCornerRadius(Tuils.dpToPx(context, 8));
            gridWrapper.setBackground(frostedGlassBackground);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
                gridWrapper.setElevation(Tuils.dpToPx(context, 8));
            }
        }
    }

    public void reloadGrid() {
        DashboardItemManager.getInstance().loadFromFile();
        loadConfig();
        setupGrid();
        updateDashboardData();
    }

    private void setupGrid() {
        if (gridContainer == null) return;
        gridContainer.removeAllViews();
        boxHolders.clear();
        gridContainer.setColumnCount(cols);
        gridContainer.setRowCount(rows);

        DashboardItemManager itemManager = DashboardItemManager.getInstance();
        itemManager.loadFromFile();

        int totalBoxes = rows * cols;
        Typeface monoTypeface = Tuils.getTypeface(context);
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels - Tuils.dpToPx(context, 24);
        int cellWidth = (screenWidth / cols) - Tuils.dpToPx(context, 8);

        for (int i = 0; i < totalBoxes; i++) {
            int boxIndex = i + 1;
            DashboardItem item = itemManager.getItem(boxIndex);

            int accent = cpuColor;
            try {
                accent = Color.parseColor(item.getColor());
            } catch (Exception e) {
                accent = cpuColor;
            }
            String boxTitle = item.getTitle();

            LinearLayout boxLayout = new LinearLayout(context);
            boxLayout.setOrientation(LinearLayout.VERTICAL);
            boxLayout.setPadding(Tuils.dpToPx(context, 6), Tuils.dpToPx(context, 5), Tuils.dpToPx(context, 6), Tuils.dpToPx(context, 5));
            GradientDrawable borderDrawable = new GradientDrawable();
            borderDrawable.setColor(boxBgColor);
            borderDrawable.setStroke(Tuils.dpToPx(context, 1), accent);
            borderDrawable.setCornerRadius(Tuils.dpToPx(context, 4));
            boxLayout.setBackground(borderDrawable);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = Math.max(cellWidth, Tuils.dpToPx(context, 60));
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.setMargins(Tuils.dpToPx(context, 4), Tuils.dpToPx(context, 4), Tuils.dpToPx(context, 4), Tuils.dpToPx(context, 4));
            boxLayout.setLayoutParams(params);

            LinearLayout titleRow = new LinearLayout(context);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            TextView boxTitleTv = new TextView(context);
            boxTitleTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            boxTitleTv.setText(boxTitle);
            boxTitleTv.setTextColor(accent);
            boxTitleTv.setTextSize(9.5f);
            boxTitleTv.setTypeface(monoTypeface, Typeface.BOLD);
            TextView boxDetailTv = new TextView(context);
            boxDetailTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            boxDetailTv.setText("--");
            boxDetailTv.setTextColor(accent);
            boxDetailTv.setTextSize(9.5f);
            boxDetailTv.setTypeface(monoTypeface);
            titleRow.addView(boxTitleTv);
            titleRow.addView(boxDetailTv);

            TextView barTv = new TextView(context);
            barTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            barTv.setText("[░░░░░░░░░░]");
            barTv.setTextColor(accent);
            barTv.setTextSize(10f);
            barTv.setTypeface(monoTypeface);
            if (!item.isShowBar()) {
                barTv.setVisibility(View.GONE);
            }

            final String boxAction = item.getAction();
            boxLayout.setOnClickListener(v -> {
                if (boxAction != null && !boxAction.trim().isEmpty()) {
                    String cmdToRun = boxAction.trim();
                    Tuils.sendOutput(context, "$ " + cmdToRun);
                    if (commandExecuter != null) {
                        commandExecuter.execute(cmdToRun, null);
                    } else {
                        Intent cmdIntent = new Intent("ohi.andre.consolelauncher.EXEC_COMMAND");
                        cmdIntent.putExtra("cmd", cmdToRun);
                        context.sendBroadcast(cmdIntent);
                    }
                } else {
                    if ("grid".equalsIgnoreCase(dashboardMode)) {
                        toggleExpand();
                    }
                }
            });

            boxLayout.addView(titleRow);
            boxLayout.addView(barTv);
            gridContainer.addView(boxLayout);

            BoxHolder holder = new BoxHolder();
            holder.index = boxIndex;
            holder.item = item;
            holder.titleTv = boxTitleTv;
            holder.detailTv = boxDetailTv;
            holder.barTv = barTv;
            holder.accentColor = accent;
            boxHolders.add(holder);
        }
    }

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            if (dashboardEnabled && "grid".equalsIgnoreCase(dashboardMode)) updateDashboardData();
            if (handler != null) handler.postDelayed(this, refreshIntervalMs);
        }
    };

    public void start() {
        if (isRunning) return;
        isRunning = true;
        handler.post(tickRunnable);
    }
    public void pause() { isRunning = false; handler.removeCallbacks(tickRunnable); }
    public void resume() { start(); }

    private void handleLongPress() {
        if ("none".equalsIgnoreCase(longPressAction)) return;

        if ("ai_chat".equalsIgnoreCase(longPressAction)) {
            Intent intent = new Intent();
            intent.setComponent(new android.content.ComponentName(context, "ohi.andre.consolelauncher.managers.AiChatActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (Exception e) {
                Tuils.sendOutput(context, "AI Chat Activity not found.");
            }
        } else if ("app_launcher".equalsIgnoreCase(longPressAction)) {
            Intent intent = new Intent();
            intent.setComponent(new android.content.ComponentName(context, "ohi.andre.consolelauncher.managers.GridAppLauncherActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (Exception e) {
                Tuils.sendOutput(context, "App Launcher Activity not found.");
            }
        }
    }

    private void toggleExpand() {
        if (!isExpanded) {
            originalRows = rows;
            originalCols = cols;
            rows = 5;
            cols = 5;
            isExpanded = true;
        } else {
            rows = originalRows;
            cols = originalCols;
            isExpanded = false;
        }
        setupGrid();
        updateDashboardData();
    }

    private void updateDashboardData() {
        try {
            if (timeTv != null) timeTv.setText(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
            if (titleTv != null) titleTv.setText(" " + VariablesManager.resolveVariables(context, baseTitle, null) + " [" + SessionManager.getActiveSession().toUpperCase() + "]");
            if (dotTv != null) {
                dotState = !dotState;
                dotTv.setText(dotState ? "● LIVE" : "○ LIVE");
            }

            int barChars = Math.max(6, 16 - (cols * 2));
            for (BoxHolder holder : boxHolders) {
                if (holder.item == null) continue;
                String type = holder.item.getType().toLowerCase();
                String detail = "--";
                int percentage = 0;

                switch (type) {
                    case "cpu": {
                        percentage = Tuils.getCpuUsage();
                        detail = percentage + "%";
                        break;
                    }
                    case "ram": {
                        activityManager.getMemoryInfo(memoryInfo);
                        long totalRam = Tuils.totalRam() * 1024;
                        long availRam = memoryInfo.availMem;
                        long usedRam = totalRam - availRam;
                        percentage = totalRam > 0 ? (int) (usedRam * 100 / totalRam) : 0;
                        detail = Tuils.round(usedRam / 1024.0 / 1024.0 / 1024.0, 1) + "G";
                        break;
                    }
                    case "battery": {
                        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                        Intent batteryStatus = context.registerReceiver(null, ifilter);
                        int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : 0;
                        int scale = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : 100;
                        percentage = (int) (level * 100 / (float) scale);
                        detail = percentage + "%";
                        break;
                    }
                    case "storage": {
                        double totalSpace = Tuils.getTotalInternalMemorySize(Tuils.GIGA);
                        double availSpace = Tuils.getAvailableInternalMemorySize(Tuils.GIGA);
                        double usedSpace = totalSpace - availSpace;
                        percentage = totalSpace > 0 ? (int) (usedSpace * 100 / totalSpace) : 0;
                        detail = (int) usedSpace + "G/" + (int) totalSpace + "G";
                        break;
                    }
                    case "network": {
                        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                        if (activeNetwork != null && activeNetwork.isConnected()) {
                            detail = activeNetwork.getTypeName();
                            percentage = 100;
                        } else {
                            detail = "OFF";
                            percentage = 0;
                        }
                        break;
                    }
                    case "uptime": {
                        long uptime = SystemClock.elapsedRealtime() / 1000;
                        detail = (uptime / 3600) + "h";
                        percentage = (int) ((uptime % 86400) * 100 / 86400);
                        break;
                    }
                    case "device": {
                        detail = Build.MODEL;
                        percentage = 100;
                        break;
                    }
                    case "thermal": {
                        detail = "38\u00B0C";
                        percentage = 40;
                        break;
                    }
                    case "display": {
                        DisplayMetrics dm = context.getResources().getDisplayMetrics();
                        detail = dm.widthPixels + "x" + dm.heightPixels;
                        percentage = 100;
                        break;
                    }
                    case "kernel": {
                        detail = System.getProperty("os.version");
                        percentage = 100;
                        break;
                    }
                    case "time": {
                        detail = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                        percentage = 100;
                        break;
                    }
                    case "date": {
                        detail = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                        percentage = 100;
                        break;
                    }
                    case "network_download": {
                        detail = VariablesManager.resolveVariables(context, "{{network_download}}", null);
                        percentage = 60;
                        break;
                    }
                    case "network_upload": {
                        detail = VariablesManager.resolveVariables(context, "{{network_upload}}", null);
                        percentage = 40;
                        break;
                    }
                    case "variable": {
                        String varKey = holder.item.getVariableName();
                        if (varKey.isEmpty()) varKey = "user";
                        detail = VariablesManager.resolveVariables(context, "{{" + varKey + "}}", null);
                        percentage = 100;
                        try {
                            percentage = Math.max(0, Math.min(100, Integer.parseInt(detail.replaceAll("\\D+", ""))));
                        } catch (Exception ignored) {}
                        break;
                    }
                    case "custom": {
                        String customT = holder.item.getCustomText();
                        if (customT.isEmpty()) customT = holder.item.getFormat();
                        detail = VariablesManager.resolveVariables(context, customT, null);
                        percentage = 100;
                        break;
                    }
                    default: {
                        detail = VariablesManager.resolveVariables(context, "{{" + type + "}}", null);
                        if (detail.equals("{{" + type + "}}")) {
                            detail = type.toUpperCase();
                        }
                        percentage = 100;
                        break;
                    }
                }

                if (holder.item.getFormat() != null && !holder.item.getFormat().isEmpty()) {
                    String customFmt = holder.item.getFormat();
                    if (customFmt.contains("%v")) {
                        detail = customFmt.replace("%v", detail);
                    }
                }

                holder.detailTv.setText(detail);
                if (holder.item.isShowBar()) {
                    holder.barTv.setVisibility(View.VISIBLE);
                    holder.barTv.setText(getBarString(percentage, barChars));
                } else {
                    holder.barTv.setVisibility(View.GONE);
                }
            }
        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    private String getBarString(int percentage, int width) {
        int filled = Math.max(0, Math.min(width, (percentage * width) / 100));
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < width; i++) {
            if (i < filled) sb.append(fillChar);
            else sb.append(emptyChar);
        }
        sb.append("]");
        return sb.toString();
    }
}
