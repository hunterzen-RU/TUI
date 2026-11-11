package ohi.andre.consolelauncher.managers;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import ohi.andre.consolelauncher.tuils.Tuils;

public class SessionManager {

    private static final List<String> sessions = new ArrayList<>();
    private static int activeIndex = 0;

    static {
        sessions.add("main");
    }

    public static synchronized List<String> getSessions() {
        return new ArrayList<>(sessions);
    }

    public static synchronized String getActiveSession() {
        if (sessions.isEmpty()) {
            sessions.add("main");
            activeIndex = 0;
        }
        if (activeIndex < 0 || activeIndex >= sessions.size()) {
            activeIndex = 0;
        }
        return sessions.get(activeIndex);
    }

    public static synchronized String createOrSwitchSession(Context context, String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Active Session: " + getActiveSession() + "\nSessions: " + sessions;
        }
        String cleanName = name.trim().toLowerCase();

        int existingIdx = sessions.indexOf(cleanName);
        if (existingIdx >= 0) {
            activeIndex = existingIdx;
            notifySessionChange(context);
            return "Switched to session '" + cleanName + "'";
        } else {
            sessions.add(cleanName);
            activeIndex = sessions.size() - 1;
            notifySessionChange(context);
            return "Created and switched to new session '" + cleanName + "'";
        }
    }

    public static synchronized String closeSession(Context context, String name) {
        if (name == null || name.trim().isEmpty()) return "Specify session name to close.";
        String cleanName = name.trim().toLowerCase();

        if ("main".equals(cleanName) && sessions.size() == 1) {
            return "Cannot close primary 'main' session.";
        }

        int idx = sessions.indexOf(cleanName);
        if (idx >= 0) {
            sessions.remove(idx);
            if (activeIndex >= sessions.size()) {
                activeIndex = sessions.size() - 1;
            }
            notifySessionChange(context);
            return "Closed session '" + cleanName + "'. Active session: " + getActiveSession();
        } else {
            return "Session '" + cleanName + "' not found.";
        }
    }

    public static synchronized String switchNextSession(Context context) {
        if (sessions.size() <= 1) {
            Tuils.sendOutput(context, "Active Session: [" + getActiveSession() + "]. Use 'tui -session <name>' to create more sessions.");
            return getActiveSession();
        }
        activeIndex = (activeIndex + 1) % sessions.size();
        notifySessionChange(context);
        return getActiveSession();
    }

    public static synchronized String switchPreviousSession(Context context) {
        if (sessions.size() <= 1) {
            Tuils.sendOutput(context, "Active Session: [" + getActiveSession() + "]. Use 'tui -session <name>' to create more sessions.");
            return getActiveSession();
        }
        activeIndex = (activeIndex - 1 + sessions.size()) % sessions.size();
        notifySessionChange(context);
        return getActiveSession();
    }

    private static void notifySessionChange(Context context) {
        String active = getActiveSession();
        Tuils.sendOutput(context, "► Switched Session: [" + active + "] (" + (activeIndex + 1) + "/" + sessions.size() + ")");
        if (DashboardManager.instance != null) {
            DashboardManager.instance.reloadGrid();
        }
    }
}
