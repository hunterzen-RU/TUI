package ohi.andre.consolelauncher.managers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import ohi.andre.consolelauncher.LauncherActivity;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.tuils.Tuils;

public class TuiDownloadService extends Service {

    public static final String ACTION_START_DOWNLOAD = "ohi.andre.consolelauncher.ACTION_START_DOWNLOAD";
    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_DEST_PATH = "extra_dest_path";
    public static final String EXTRA_PKG_NAME = "extra_pkg_name";

    private static final String CHANNEL_ID = "tui_download_channel";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Package Downloads",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START_DOWNLOAD.equals(intent.getAction())) {
            String url = intent.getStringExtra(EXTRA_URL);
            String destPath = intent.getStringExtra(EXTRA_DEST_PATH);
            String pkgName = intent.getStringExtra(EXTRA_PKG_NAME);

            startForeground(NOTIFICATION_ID, createNotification(pkgName, 0));
            new Thread(() -> performDownload(url, destPath, pkgName)).start();
        }
        return START_NOT_STICKY;
    }

    private Notification createNotification(String pkgName, int progress) {
        Intent intent = new Intent(this, LauncherActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Downloading " + pkgName)
                .setContentText(progress + "% completed")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, progress, false)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void performDownload(String urlStr, String destPath, String pkgName) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.connect();

            int fileLength = conn.getContentLength();
            InputStream input = conn.getInputStream();
            FileOutputStream output = new FileOutputStream(new File(destPath));

            byte[] data = new byte[4096];
            long total = 0;
            int count;
            int lastProgress = -1;

            while ((count = input.read(data)) != -1) {
                total += count;
                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    if (progress != lastProgress) {
                        manager.notify(NOTIFICATION_ID, createNotification(pkgName, progress));
                        lastProgress = progress;
                        
                        // Send broadcast or update static progress for CLI
                        Intent progressIntent = new Intent("ohi.andre.consolelauncher.DOWNLOAD_PROGRESS");
                        progressIntent.putExtra("progress", progress);
                        progressIntent.putExtra("pkgName", pkgName);
                        sendBroadcast(progressIntent);
                    }
                }
                output.write(data, 0, count);
            }

            output.flush();
            output.close();
            input.close();

            // Notify success
            Tuils.sendOutput(this, "Downloaded " + pkgName + " successfully.");
            
            // Set executable
            new File(destPath).setExecutable(true, false);

        } catch (Exception e) {
            Tuils.sendOutput(this, "Download failed for " + pkgName + ": " + e.getMessage());
        } finally {
            stopForeground(true);
            stopSelf();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
