package com.tuff.browser.download;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.tuff.browser.MainActivity;
import com.tuff.browser.R;
import com.tuff.browser.TuffApp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadService extends Service {

    public static final String ACTION_START_DOWNLOAD = "com.tuff.browser.START_DOWNLOAD";
    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_CONTENT_DISPOSITION = "extra_content_disposition";
    public static final String EXTRA_MIMETYPE = "extra_mimetype";
    public static final String EXTRA_USER_AGENT = "extra_user_agent";

    private static final int NOTIFICATION_ID = 1001;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public static void enqueue(Context context, String url, String userAgent, String contentDisposition, String mimeType) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_START_DOWNLOAD);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_USER_AGENT, userAgent);
        intent.putExtra(EXTRA_CONTENT_DISPOSITION, contentDisposition);
        intent.putExtra(EXTRA_MIMETYPE, mimeType);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START_DOWNLOAD.equals(intent.getAction())) {
            String url = intent.getStringExtra(EXTRA_URL);
            String userAgent = intent.getStringExtra(EXTRA_USER_AGENT);
            String contentDisposition = intent.getStringExtra(EXTRA_CONTENT_DISPOSITION);
            String mimeType = intent.getStringExtra(EXTRA_MIMETYPE);

            startForeground(NOTIFICATION_ID, buildInitialNotification());
            startDownloadTask(url, userAgent, contentDisposition, mimeType);
        }
        return START_NOT_STICKY;
    }

    private void startDownloadTask(String urlString, String userAgent, String contentDisposition, String mimeType) {
        executor.execute(() -> {
            String filename = URLUtil.guessFileName(urlString, contentDisposition, mimeType);
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }
            File destFile = new File(downloadDir, filename);

            DownloadTask task = new DownloadTask(UUID.randomUUID().toString(), urlString, filename, destFile, mimeType);
            DownloadRepository.getInstance().addTask(task);

            HttpURLConnection connection = null;
            InputStream in = null;
            FileOutputStream out = null;

            try {
                task.setStatus(DownloadTask.Status.DOWNLOADING);
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(true);

                if (userAgent != null) {
                    connection.setRequestProperty("User-Agent", userAgent);
                }

                // Attach cookies
                String cookies = CookieManager.getInstance().getCookie(urlString);
                if (cookies != null) {
                    connection.setRequestProperty("Cookie", cookies);
                }

                // Check for HTTP Range resume
                long existingLength = 0;
                if (destFile.exists()) {
                    existingLength = destFile.length();
                    connection.setRequestProperty("Range", "bytes=" + existingLength + "-");
                }

                connection.connect();
                int responseCode = connection.getResponseCode();

                boolean isRangeAccepted = (responseCode == HttpURLConnection.HTTP_PARTIAL);
                long contentLength = connection.getContentLength();
                if (isRangeAccepted) {
                    task.setTotalBytes(existingLength + contentLength);
                    task.setDownloadedBytes(existingLength);
                    out = new FileOutputStream(destFile, true);
                } else {
                    task.setTotalBytes(contentLength);
                    task.setDownloadedBytes(0);
                    out = new FileOutputStream(destFile, false);
                }

                in = connection.getInputStream();
                byte[] buffer = new byte[8192];
                int read;
                long lastNotificationUpdate = System.currentTimeMillis();

                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    task.setDownloadedBytes(task.getDownloadedBytes() + read);

                    long now = System.currentTimeMillis();
                    if (now - lastNotificationUpdate > 500) {
                        lastNotificationUpdate = now;
                        updateProgressNotification(task);
                        DownloadRepository.getInstance().notifyProgress(task);
                    }
                }

                out.flush();
                task.setStatus(DownloadTask.Status.COMPLETED);
                DownloadRepository.getInstance().notifyCompleted(task);
                showCompletedNotification(task);

            } catch (Exception e) {
                task.setStatus(DownloadTask.Status.FAILED);
                DownloadRepository.getInstance().notifyFailed(task);
            } finally {
                try { if (in != null) in.close(); } catch (Exception ignored) {}
                try { if (out != null) out.close(); } catch (Exception ignored) {}
                if (connection != null) connection.disconnect();
            }
        });
    }

    private Notification buildInitialNotification() {
        return new NotificationCompat.Builder(this, TuffApp.CHANNEL_DOWNLOADS)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.downloading))
                .setSmallIcon(R.drawable.ic_download)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void updateProgressNotification(DownloadTask task) {
        Notification notification = new NotificationCompat.Builder(this, TuffApp.CHANNEL_DOWNLOADS)
                .setContentTitle(task.getFilename())
                .setContentText(task.getProgressPercentage() + "%")
                .setSmallIcon(R.drawable.ic_download)
                .setProgress(100, task.getProgressPercentage(), task.getTotalBytes() <= 0)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void showCompletedNotification(DownloadTask task) {
        stopForeground(false);
        Notification notification = new NotificationCompat.Builder(this, TuffApp.CHANNEL_DOWNLOADS)
                .setContentTitle(task.getFilename())
                .setContentText(getString(R.string.download_completed))
                .setSmallIcon(R.drawable.ic_download)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build();
        androidx.core.app.NotificationManagerCompat.from(this).notify(task.getId().hashCode(), notification);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
