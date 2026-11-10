package ohi.andre.consolelauncher.commands;

import android.content.Context;

import java.util.ArrayList;

import ohi.andre.consolelauncher.managers.AppsManager;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;

@SuppressWarnings("deprecation")
public abstract class ExecutePack {

    public Object[] args;
    public Context context;
    public CommandGroup commandGroup;

    public int currentIndex = 0;

    public ExecutePack(CommandGroup group) {
        this.commandGroup = group;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> c) {
        return (T) get();
    }

    public <T> T get(Class<T> c, int index) {
        if(index < args.length) return (T) args[index];
        return null;
    }

    public Object get() {
        if(currentIndex < args.length) return args[currentIndex++];
        return null;
    }

    public String getString() {
        Object o = get();
        if (o == null) return null;
        if (o instanceof String) {
            return (String) o;
        } else if (o instanceof java.util.List) {
            return ohi.andre.consolelauncher.tuils.Tuils.toPlanString((java.util.List) o, " ");
        } else if (o instanceof Object[]) {
            Object[] arr = (Object[]) o;
            StringBuilder sb = new StringBuilder();
            for (Object item : arr) {
                if (sb.length() > 0) sb.append(" ");
                if (item instanceof java.util.List) {
                    sb.append(ohi.andre.consolelauncher.tuils.Tuils.toPlanString((java.util.List) item, " "));
                } else {
                    sb.append(String.valueOf(item));
                }
            }
            return sb.toString();
        } else {
            return o.toString();
        }
    }

    public int getInt() {
        return (int) get();
    }

    public boolean getBoolean() {
        return (boolean) get();
    }

    public ArrayList getList() {
        return (ArrayList) get();
    }

    public XMLPrefsSave getPrefsSave() {
        return (XMLPrefsSave) get();
    }

    public AppsManager.LaunchInfo getLaunchInfo() {
        return (AppsManager.LaunchInfo) get();
    }

    public void set(Object[] args) {
        this.args = args;
    }

    public void clear() {
        args = null;
        currentIndex = 0;
    }
}
