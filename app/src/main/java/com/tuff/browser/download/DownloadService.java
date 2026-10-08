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
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.URLUtil;

import android.content.ContentValues;
import android.media.MediaScannerConnection;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import java.io.FileInputStream;
import java.io.OutputStream;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;

import com.tuff.browser.MainActivity;
import com.tuff.browser.R;
import com.tuff.browser.TuffApp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class DownloadService extends Service {

    private static final String TAG = "DownloadService";

    public static final String ACTION_START_DOWNLOAD = "com.tuff.browser.START_DOWNLOAD";
    public static final String ACTION_PAUSE_DOWNLOAD = "com.tuff.browser.PAUSE_DOWNLOAD";
    public static final String ACTION_RESUME_DOWNLOAD = "com.tuff.browser.RESUME_DOWNLOAD";
    public static final String ACTION_CANCEL_DOWNLOAD = "com.tuff.browser.CANCEL_DOWNLOAD";

    public static final String EXTRA_TASK_ID = "extra_task_id";
    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_CONTENT_DISPOSITION = "extra_content_disposition";
    public static final String EXTRA_MIMETYPE = "extra_mimetype";
    public static final String EXTRA_USER_AGENT = "extra_user_agent";

    private static final int NOTIFICATION_ID_BASE = 1000;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    private final Map<String, AtomicBoolean> pauseFlags = new ConcurrentHashMap<>();
    private final Map<String, HttpURLConnection> activeConnections = new ConcurrentHashMap<>();

    public static void enqueue(Context context, String url, String userAgent, String contentDisposition, String mimeType) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_START_DOWNLOAD);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_USER_AGENT, userAgent);
        intent.putExtra(EXTRA_CONTENT_DISPOSITION, contentDisposition);
        intent.putExtra(EXTRA_MIMETYPE, mimeType);
        startServiceInternal(context, intent);
    }

    public static void pause(Context context, String taskId) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_PAUSE_DOWNLOAD);
        intent.putExtra(EXTRA_TASK_ID, taskId);
        startServiceInternal(context, intent);
    }

    public static void resume(Context context, String taskId) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_RESUME_DOWNLOAD);
        intent.putExtra(EXTRA_TASK_ID, taskId);
        startServiceInternal(context, intent);
    }

    public static void cancel(Context context, String taskId) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_CANCEL_DOWNLOAD);
        intent.putExtra(EXTRA_TASK_ID, taskId);
        startServiceInternal(context, intent);
    }

    private static void startServiceInternal(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_START_DOWNLOAD.equals(action)) {
            String url = intent.getStringExtra(EXTRA_URL);
            String userAgent = intent.getStringExtra(EXTRA_USER_AGENT);
            String contentDisposition = intent.getStringExtra(EXTRA_CONTENT_DISPOSITION);
            String mimeType = intent.getStringExtra(EXTRA_MIMETYPE);

            String rawFilename = URLUtil.guessFileName(url, contentDisposition, mimeType);

            File downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloadDir == null) downloadDir = getFilesDir();
            if (!downloadDir.exists()) downloadDir.mkdirs();

            File publicDir = null;
            try {
                publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            } catch (Exception ignored) {}

            String filename = FileUtils.resolveUniqueFilename(downloadDir, publicDir, rawFilename);
            File destFile = new File(downloadDir, filename);
            if (destFile.exists()) {
                destFile.delete();
            }

            DownloadTask task = new DownloadTask(
                    UUID.randomUUID().toString(),
                    url,
                    userAgent,
                    contentDisposition,
                    filename,
                    destFile,
                    FileUtils.getEffectiveMimeType(filename, mimeType)
            );
            DownloadRepository.getInstance().addTask(task);

            startForeground(getNotificationId(task.getId()), buildProgressNotification(task));
            runDownloadTask(task);

        } else if (ACTION_PAUSE_DOWNLOAD.equals(action)) {
            String taskId = intent.getStringExtra(EXTRA_TASK_ID);
            handlePause(taskId);

        } else if (ACTION_RESUME_DOWNLOAD.equals(action)) {
            String taskId = intent.getStringExtra(EXTRA_TASK_ID);
            handleResume(taskId);

        } else if (ACTION_CANCEL_DOWNLOAD.equals(action)) {
            String taskId = intent.getStringExtra(EXTRA_TASK_ID);
            handleCancel(taskId);
        }

        return START_NOT_STICKY;
    }

    private void handlePause(String taskId) {
        if (taskId == null) return;
        DownloadTask task = DownloadRepository.getInstance().findTaskById(taskId);
        if (task == null) return;

        AtomicBoolean pauseFlag = pauseFlags.get(taskId);
        if (pauseFlag != null) {
            pauseFlag.set(true);
        }

        HttpURLConnection conn = activeConnections.remove(taskId);
        if (conn != null) {
            try { conn.disconnect(); } catch (Exception ignored) {}
        }

        task.setStatus(DownloadTask.Status.PAUSED);
        task.setSpeedBytesPerSec(0);
        DownloadRepository.getInstance().notifyStatusChanged(task);

        int notifId = getNotificationId(taskId);
        boolean hasOtherActive = false;
        DownloadTask nextActive = null;
        for (DownloadTask t : DownloadRepository.getInstance().getTasks()) {
            if (t.getStatus() == DownloadTask.Status.DOWNLOADING) {
                hasOtherActive = true;
                nextActive = t;
                break;
            }
        }

        if (hasOtherActive && nextActive != null) {
            startForeground(getNotificationId(nextActive.getId()), buildProgressNotification(nextActive));
            NotificationManagerCompat.from(this).notify(notifId, buildPausedNotification(task));
        } else {
            stopForeground(false);
            NotificationManagerCompat.from(this).notify(notifId, buildPausedNotification(task));
        }
    }

    private void handleResume(String taskId) {
        if (taskId == null) return;
        DownloadTask task = DownloadRepository.getInstance().findTaskById(taskId);
        if (task == null) return;

        if (task.getStatus() == DownloadTask.Status.PAUSED || task.getStatus() == DownloadTask.Status.FAILED) {
            task.setStatus(DownloadTask.Status.DOWNLOADING);
            DownloadRepository.getInstance().notifyStatusChanged(task);
            startForeground(getNotificationId(taskId), buildProgressNotification(task));
            runDownloadTask(task);
        }
    }

    private void handleCancel(String taskId) {
        if (taskId == null) return;
        DownloadTask task = DownloadRepository.getInstance().findTaskById(taskId);

        AtomicBoolean pauseFlag = pauseFlags.get(taskId);
        if (pauseFlag != null) {
            pauseFlag.set(true);
        }

        HttpURLConnection conn = activeConnections.remove(taskId);
        if (conn != null) {
            try { conn.disconnect(); } catch (Exception ignored) {}
        }

        if (task != null) {
            File file = task.getDestinationFile();
            if (file != null && file.exists()) {
                file.delete();
            }

            try {
                File publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (publicDir != null) {
                    File pubFile = new File(publicDir, task.getFilename());
                    if (pubFile.exists()) pubFile.delete();
                }
            } catch (Exception ignored) {}

            task.setStatus(DownloadTask.Status.CANCELLED);
            task.setSpeedBytesPerSec(0);
            DownloadRepository.getInstance().removeTask(taskId);
        }

        int notifId = getNotificationId(taskId);
        boolean hasOtherActive = false;
        DownloadTask nextActive = null;
        for (DownloadTask t : DownloadRepository.getInstance().getTasks()) {
            if (t.getStatus() == DownloadTask.Status.DOWNLOADING) {
                hasOtherActive = true;
                nextActive = t;
                break;
            }
        }

        if (hasOtherActive && nextActive != null) {
            startForeground(getNotificationId(nextActive.getId()), buildProgressNotification(nextActive));
            NotificationManagerCompat.from(this).cancel(notifId);
        } else {
            stopForeground(true);
            NotificationManagerCompat.from(this).cancel(notifId);
            stopSelf();
        }
    }

    private void runDownloadTask(DownloadTask task) {
        AtomicBoolean pauseFlag = new AtomicBoolean(false);
        pauseFlags.put(task.getId(), pauseFlag);

        executor.execute(() -> {
            HttpURLConnection connection = null;
            InputStream in = null;
            FileOutputStream out = null;

            try {
                task.setStatus(DownloadTask.Status.DOWNLOADING);
                DownloadRepository.getInstance().notifyStatusChanged(task);

                String currentUrl = task.getUrl();
                File destFile = task.getDestinationFile();
                int redirects = 0;

                while (redirects < 5 && !pauseFlag.get()) {
                    URL url = new URL(currentUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    activeConnections.put(task.getId(), connection);

                    connection.setConnectTimeout(20000);
                    connection.setReadTimeout(20000);
                    connection.setInstanceFollowRedirects(false);

                    if (task.getUserAgent() != null) {
                        connection.setRequestProperty("User-Agent", task.getUserAgent());
                    }

                    String cookies = CookieManager.getInstance().getCookie(currentUrl);
                    if (cookies != null) {
                        connection.setRequestProperty("Cookie", cookies);
                    }

                    long existingLength = (destFile.exists()) ? destFile.length() : 0;
                    if (existingLength > 0) {
                        connection.setRequestProperty("Range", "bytes=" + existingLength + "-");
                    }

                    connection.connect();
                    int code = connection.getResponseCode();

                    if (code == HttpURLConnection.HTTP_MOVED_PERM 
                            || code == HttpURLConnection.HTTP_MOVED_TEMP 
                            || code == HttpURLConnection.HTTP_SEE_OTHER 
                            || code == 307 || code == 308) {
                        String location = connection.getHeaderField("Location");
                        if (location != null) {
                            if (!location.startsWith("http://") && !location.startsWith("https://")) {
                                URL base = new URL(currentUrl);
                                location = new URL(base, location).toExternalForm();
                            }
                            currentUrl = location;
                            redirects++;
                            connection.disconnect();
                            continue;
                        }
                    }

                    // If range was rejected (416 Range Not Satisfiable), reset file and download from beginning
                    if (code == 416 && existingLength > 0) {
                        if (destFile.exists()) destFile.delete();
                        connection.disconnect();
                        connection = (HttpURLConnection) new URL(currentUrl).openConnection();
                        activeConnections.put(task.getId(), connection);
                        connection.setConnectTimeout(20000);
                        connection.setReadTimeout(20000);
                        if (task.getUserAgent() != null) {
                            connection.setRequestProperty("User-Agent", task.getUserAgent());
                        }
                        if (cookies != null) {
                            connection.setRequestProperty("Cookie", cookies);
                        }
                        connection.connect();
                        code = connection.getResponseCode();
                    }

                    if (code >= 400) {
                        throw new java.io.IOException("Server returned HTTP " + code);
                    }

                    boolean isRangeAccepted = (code == HttpURLConnection.HTTP_PARTIAL);
                    long contentLength = connection.getContentLength();
                    long existing = (destFile.exists()) ? destFile.length() : 0;

                    if (isRangeAccepted) {
                        task.setTotalBytes(existing + contentLength);
                        task.setDownloadedBytes(existing);
                        out = new FileOutputStream(destFile, true);
                    } else {
                        task.setTotalBytes(contentLength);
                        task.setDownloadedBytes(0);
                        out = new FileOutputStream(destFile, false);
                    }
                    break;
                }

                if (connection == null || pauseFlag.get()) {
                    return;
                }

                in = connection.getInputStream();
                byte[] buffer = new byte[16384];
                int read;

                long lastUpdate = System.currentTimeMillis();
                long bytesSinceLastUpdate = 0;

                while (!pauseFlag.get() && (read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    long newDownloaded = task.getDownloadedBytes() + read;
                    task.setDownloadedBytes(newDownloaded);
                    bytesSinceLastUpdate += read;

                    long now = System.currentTimeMillis();
                    long delta = now - lastUpdate;
                    if (delta >= 400) {
                        long speed = (bytesSinceLastUpdate * 1000) / delta;
                        task.setSpeedBytesPerSec(speed);
                        lastUpdate = now;
                        bytesSinceLastUpdate = 0;

                        if (!pauseFlag.get() && task.getStatus() == DownloadTask.Status.DOWNLOADING) {
                            NotificationManagerCompat.from(this).notify(getNotificationId(task.getId()), buildProgressNotification(task));
                            DownloadRepository.getInstance().notifyProgress(task);
                        }
                    }
                }

                if (pauseFlag.get() || task.getStatus() == DownloadTask.Status.CANCELLED) {
                    return;
                }

                out.flush();
                try { out.close(); } catch (Exception ignored) {}
                out = null;

                exportToPublicDownloads(task);

                task.setStatus(DownloadTask.Status.COMPLETED);
                task.setSpeedBytesPerSec(0);
                DownloadRepository.getInstance().notifyStatusChanged(task);
                showCompletedNotification(task);

            } catch (Exception e) {
                if (!pauseFlag.get() && task.getStatus() != DownloadTask.Status.CANCELLED) {
                    Log.e(TAG, "Download error: " + e.getMessage(), e);
                    task.setStatus(DownloadTask.Status.FAILED);
                    task.setSpeedBytesPerSec(0);
                    DownloadRepository.getInstance().notifyStatusChanged(task);
                    showFailedNotification(task);
                }
            } finally {
                try { if (in != null) in.close(); } catch (Exception ignored) {}
                try { if (out != null) out.close(); } catch (Exception ignored) {}
                if (connection != null) connection.disconnect();
                activeConnections.remove(task.getId());
                pauseFlags.remove(task.getId());

                if (task.getStatus() == DownloadTask.Status.PAUSED) {
                    NotificationManagerCompat.from(this).notify(getNotificationId(task.getId()), buildPausedNotification(task));
                }
                checkStopForeground();
            }
        });
    }

    private void checkStopForeground() {
        boolean hasActive = false;
        DownloadTask nextActive = null;
        for (DownloadTask t : DownloadRepository.getInstance().getTasks()) {
            if (t.getStatus() == DownloadTask.Status.DOWNLOADING) {
                hasActive = true;
                nextActive = t;
                break;
            }
        }
        if (!hasActive) {
            stopForeground(false);
        } else if (nextActive != null) {
            startForeground(getNotificationId(nextActive.getId()), buildProgressNotification(nextActive));
        }
    }

    private Notification buildProgressNotification(DownloadTask task) {
        String info = task.getFormattedProgress();
        String speed = task.getFormattedSpeed();
        String contentText = info + (speed.isEmpty() ? "" : " • " + speed);

        Intent pauseIntent = new Intent(this, DownloadService.class);
        pauseIntent.setAction(ACTION_PAUSE_DOWNLOAD);
        pauseIntent.putExtra(EXTRA_TASK_ID, task.getId());
        PendingIntent piPause = PendingIntent.getService(this, getNotificationId(task.getId()) + 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent cancelIntent = new Intent(this, DownloadService.class);
        cancelIntent.setAction(ACTION_CANCEL_DOWNLOAD);
        cancelIntent.putExtra(EXTRA_TASK_ID, task.getId());
        PendingIntent piCancel = PendingIntent.getService(this, getNotificationId(task.getId()) + 2, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent openAppIntent = new Intent(this, MainActivity.class);
        PendingIntent piApp = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, TuffApp.CHANNEL_DOWNLOADS)
                .setContentTitle(task.getFilename())
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_download)
                .setProgress(100, task.getProgressPercentage(), task.getTotalBytes() <= 0)
                .setContentIntent(piApp)
                .addAction(R.drawable.ic_pause, getString(R.string.pause), piPause)
                .addAction(R.drawable.ic_close, getString(R.string.cancel), piCancel)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build();
    }

    private Notification buildPausedNotification(DownloadTask task) {
        String contentText = getString(R.string.download_paused) + " • " + task.getFormattedProgress();

        Intent resumeIntent = new Intent(this, DownloadService.class);
        resumeIntent.setAction(ACTION_RESUME_DOWNLOAD);
        resumeIntent.putExtra(EXTRA_TASK_ID, task.getId());
        PendingIntent piResume;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            piResume = PendingIntent.getForegroundService(this, getNotificationId(task.getId()) + 3, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            piResume = PendingIntent.getService(this, getNotificationId(task.getId()) + 3, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        Intent cancelIntent = new Intent(this, DownloadService.class);
        cancelIntent.setAction(ACTION_CANCEL_DOWNLOAD);
        cancelIntent.putExtra(EXTRA_TASK_ID, task.getId());
        PendingIntent piCancel = PendingIntent.getService(this, getNotificationId(task.getId()) + 2, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, TuffApp.CHANNEL_DOWNLOADS)
                .setContentTitle(task.getFilename())
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_download)
                .setProgress(100, task.getProgressPercentage(), task.getTotalBytes() <= 0)
                .addAction(R.drawable.ic_play, getString(R.string.resume), piResume)
                .addAction(R.drawable.ic_close, getString(R.string.cancel), piCancel)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(false)
                .setAutoCancel(true)
                .build();
    }

    private void showCompletedNotification(DownloadTask task) {
        PendingIntent piOpen = FileUtils.createViewPendingIntent(this, task, getNotificationId(task.getId()) + 4);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, TuffApp.CHANNEL_DOWNLOADS)
                .setContentTitle(task.getFilename())
                .setContentText(getString(R.string.download_completed) + " • " + task.getFormattedProgress())
                .setSmallIcon(R.drawable.ic_download)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        if (piOpen != null) {
            builder.setContentIntent(piOpen);
        }

        NotificationManagerCompat.from(this).notify(getNotificationId(task.getId()), builder.build());
    }

    private void showFailedNotification(DownloadTask task) {
        Intent retryIntent = new Intent(this, DownloadService.class);
        retryIntent.setAction(ACTION_RESUME_DOWNLOAD);
        retryIntent.putExtra(EXTRA_TASK_ID, task.getId());
        PendingIntent piRetry = PendingIntent.getService(this, getNotificationId(task.getId()) + 5, retryIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, TuffApp.CHANNEL_DOWNLOADS)
                .setContentTitle(task.getFilename())
                .setContentText(getString(R.string.download_failed))
                .setSmallIcon(R.drawable.ic_download)
                .addAction(R.drawable.ic_refresh, getString(R.string.resume), piRetry)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build();

        NotificationManagerCompat.from(this).notify(getNotificationId(task.getId()), notification);
    }

    private void exportToPublicDownloads(DownloadTask task) {
        File source = task.getDestinationFile();
        if (source == null || !source.exists() || source.length() == 0) return;

        String mime = FileUtils.getEffectiveMimeType(task.getFilename(), task.getMimeType());

        // 1. On Android 10+ (API 29+), insert into MediaStore.Downloads
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, task.getFilename());
                values.put(MediaStore.Downloads.MIME_TYPE, mime);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                Uri itemUri = getContentResolver().insert(collection, values);

                if (itemUri != null) {
                    try (OutputStream os = getContentResolver().openOutputStream(itemUri);
                         InputStream is = new FileInputStream(source)) {
                        byte[] buffer = new byte[32768];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            os.write(buffer, 0, len);
                        }
                        os.flush();
                    }
                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(itemUri, values, null, null);
                }
            } catch (Exception e) {
                Log.e(TAG, "MediaStore export error: " + e.getMessage(), e);
            }
        } else {
            // Older Android (<= API 28): direct public Downloads file copy
            try {
                File publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!publicDir.exists()) publicDir.mkdirs();
                File publicFile = new File(publicDir, task.getFilename());
                if (!publicFile.equals(source)) {
                    try (InputStream is = new FileInputStream(source);
                         OutputStream os = new FileOutputStream(publicFile)) {
                        byte[] buffer = new byte[32768];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            os.write(buffer, 0, len);
                        }
                        os.flush();
                    }
                }
                MediaScannerConnection.scanFile(this, new String[]{publicFile.getAbsolutePath()}, new String[]{mime}, null);
            } catch (Exception e) {
                Log.e(TAG, "Direct public file copy error: " + e.getMessage(), e);
            }
        }
    }

    public static int getNotificationId(String taskId) {
        return NOTIFICATION_ID_BASE + Math.abs(taskId.hashCode() % 10000);
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
