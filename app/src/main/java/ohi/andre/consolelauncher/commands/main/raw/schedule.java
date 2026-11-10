package ohi.andre.consolelauncher.commands.main.raw;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.cron.CronExpression;
import ohi.andre.consolelauncher.managers.cron.CronWorker;
import ohi.andre.consolelauncher.managers.cron.ScheduledTasksDbHelper;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * Command 'schedule' or 'cron' to configure and manage background WorkManager tasks.
 * Usage:
 * schedule -add "cron_expr" <action>
 * schedule -ls
 * schedule -rm <tag>
 * schedule -clear
 */
public class schedule extends ParamCommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {

        add {
            @Override
            public String exec(ExecutePack pack) {
                ArrayList<String> args = pack.getList();
                if (args.size() < 2) return "Usage: schedule -add \"<cron_expr>\" <action>";

                String cronOrArgs = Tuils.toPlanString(args, " ");
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("^(?:\"([^\"]+)\"|'([^']+)'|(\\S+\\s+\\S+\\s+\\S+\\s+\\S+\\s+\\S+))\\s+(.+)$");
                java.util.regex.Matcher m = p.matcher(cronOrArgs);
                if (!m.matches()) {
                    return "Invalid schedule syntax. Example:\nschedule -add \"*/15 * * * *\" notify \"Alert\" \"Time to check logs\"";
                }

                String cronExpr = m.group(1) != null ? m.group(1) : (m.group(2) != null ? m.group(2) : m.group(3));
                String action = m.group(4);

                try {
                    String tag = "task_" + (System.currentTimeMillis() % 100000);
                    long nextRun = CronWorker.Companion.scheduleNextRun(pack.context, tag, cronExpr.trim(), action.trim());

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    return "Scheduled task [" + tag + "]\nPattern: " + cronExpr + "\nNext run: " + sdf.format(new Date(nextRun)) + "\nAction: " + action;
                } catch (Exception e) {
                    return "Schedule error: " + e.getMessage();
                }
            }

            @Override
            public int[] args() {
                return new int[]{CommandAbstraction.TEXTLIST};
            }
        },
        ls {
            @Override
            public String exec(ExecutePack pack) {
                ScheduledTasksDbHelper db = new ScheduledTasksDbHelper(pack.context);
                List<ScheduledTasksDbHelper.ScheduledTask> tasks = db.getAllTasks();
                if (tasks.isEmpty()) {
                    return "No scheduled tasks in WorkManager queue.";
                }

                StringBuilder sb = new StringBuilder("=== Scheduled Tasks (" + tasks.size() + ") ===\n");
                for (ScheduledTasksDbHelper.ScheduledTask t : tasks) {
                    sb.append("[").append(t.getTag()).append("] Cron: \"").append(t.getCron()).append("\"\n")
                            .append("  Action: ").append(t.getAction()).append("\n")
                            .append("  Next run: ").append(t.formattedNextRun()).append(" | Last: ").append(t.formattedLastRun())
                            .append(" | Runs: ").append(t.getRunCount()).append("\n\n");
                }
                return sb.toString().trim();
            }

            @Override
            public int[] args() {
                return new int[0];
            }
        },
        rm {
            @Override
            public String exec(ExecutePack pack) {
                ArrayList<String> args = pack.getList();
                if (args.isEmpty()) return "Usage: schedule -rm <tag>";

                String tag = args.get(0);
                boolean removed = CronWorker.Companion.cancelTask(pack.context, tag);
                return removed ? "Removed scheduled task [" + tag + "]" : "Task [" + tag + "] not found.";
            }

            @Override
            public int[] args() {
                return new int[]{CommandAbstraction.TEXTLIST};
            }
        },
        clear {
            @Override
            public String exec(ExecutePack pack) {
                ScheduledTasksDbHelper db = new ScheduledTasksDbHelper(pack.context);
                List<ScheduledTasksDbHelper.ScheduledTask> tasks = db.getAllTasks();
                for (ScheduledTasksDbHelper.ScheduledTask t : tasks) {
                    androidx.work.WorkManager.getInstance(pack.context).cancelUniqueWork(t.getTag());
                }
                int count = db.clearAll();
                return "Cleared " + count + " scheduled tasks from WorkManager queue.";
            }

            @Override
            public int[] args() {
                return new int[0];
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
        public String onNotArgEnough(ExecutePack pack, int index) {
            return pack.context.getString(R.string.help_schedule);
        }

        @Override
        public String onArgNotFound(ExecutePack pack, int index) {
            return null;
        }
    }

    @Override
    public String[] params() {
        return Param.labels();
    }

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(MainPack pack, String param) {
        return Param.get(param);
    }

    @Override
    protected String doThings(ExecutePack pack) {
        return null;
    }

    @Override
    public int helpRes() {
        return R.string.help_schedule;
    }

    @Override
    public int priority() {
        return 3;
    }
}
