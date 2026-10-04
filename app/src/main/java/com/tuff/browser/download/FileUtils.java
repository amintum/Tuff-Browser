package com.tuff.browser.download;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.tuff.browser.R;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class FileUtils {

    /**
     * Resolves a unique filename by appending (1), (2), etc. if the file
     * already exists on disk or in active downloads.
     */
    public static String resolveUniqueFilename(File internalDir, File publicDir, String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            filename = "download";
        }
        filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");

        String name;
        String extension;
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            name = filename.substring(0, dotIndex);
            extension = filename.substring(dotIndex);
        } else {
            name = filename;
            extension = "";
        }

        String candidate = filename;
        int count = 1;

        while (isFilenameTaken(internalDir, publicDir, candidate)) {
            candidate = name + " (" + count + ")" + extension;
            count++;
        }

        return candidate;
    }

    private static boolean isFilenameTaken(File internalDir, File publicDir, String candidate) {
        if (internalDir != null && new File(internalDir, candidate).exists()) {
            return true;
        }
        if (publicDir != null && new File(publicDir, candidate).exists()) {
            return true;
        }
        for (DownloadTask task : DownloadRepository.getInstance().getTasks()) {
            if (task.getStatus() == DownloadTask.Status.DOWNLOADING || task.getStatus() == DownloadTask.Status.PENDING) {
                if (candidate.equalsIgnoreCase(task.getFilename())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Accurately determines the MIME type, ensuring APKs always get
     * "application/vnd.android.package-archive".
     */
    public static String getEffectiveMimeType(String filename, String fallbackMimeType) {
        if (filename != null) {
            String lower = filename.toLowerCase(Locale.US);
            if (lower.endsWith(".apk")) {
                return "application/vnd.android.package-archive";
            }
            int dot = lower.lastIndexOf('.');
            if (dot >= 0 && dot < lower.length() - 1) {
                String ext = lower.substring(dot + 1);
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                if (mime != null && !mime.isEmpty()) {
                    return mime;
                }
            }
        }

        if (fallbackMimeType != null && !fallbackMimeType.isEmpty()
                && !"application/octet-stream".equalsIgnoreCase(fallbackMimeType)
                && !"binary/octet-stream".equalsIgnoreCase(fallbackMimeType)
                && !"*/*".equals(fallbackMimeType)) {
            return fallbackMimeType;
        }

        return "*/*";
    }

    public static boolean isApk(String filename, String mimeType) {
        if (filename != null && filename.toLowerCase(Locale.US).endsWith(".apk")) {
            return true;
        }
        return "application/vnd.android.package-archive".equalsIgnoreCase(mimeType);
    }

    /**
     * Opens or installs a downloaded file with full support for Package Manager
     * and external view applications.
     */
    public static void openDownloadedFile(Context context, File file, String fallbackMimeType) {
        if (file == null || !file.exists()) {
            Toast.makeText(context, R.string.file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        String mimeType = getEffectiveMimeType(file.getName(), fallbackMimeType);

        // Check Unknown App Install permission for APKs on Android 8.0+
        if (isApk(file.getName(), mimeType)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.getPackageManager().canRequestPackageInstalls()) {
                    Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    settingsIntent.setData(Uri.parse("package:" + context.getPackageName()));
                    if (!(context instanceof Activity)) {
                        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    context.startActivity(settingsIntent);
                    Toast.makeText(context, R.string.allow_install_permission, Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }

        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            grantUriPermissions(context, intent, uri);
            context.startActivity(intent);

        } catch (Exception e) {
            try {
                // Fallback to generic viewer
                Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
                Intent genericIntent = new Intent(Intent.ACTION_VIEW);
                genericIntent.setDataAndType(uri, "*/*");
                genericIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (!(context instanceof Activity)) {
                    genericIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                grantUriPermissions(context, genericIntent, uri);
                context.startActivity(genericIntent);
            } catch (Exception ex) {
                Toast.makeText(context, context.getString(R.string.cannot_open_file) + ": " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Builds a PendingIntent to open a downloaded file from system notifications.
     */
    public static PendingIntent createViewPendingIntent(Context context, DownloadTask task, int requestCode) {
        File file = task.getDestinationFile();
        if (file == null || !file.exists()) {
            return null;
        }

        try {
            String mimeType = getEffectiveMimeType(task.getFilename(), task.getMimeType());
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);

            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(uri, mimeType);
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

            grantUriPermissions(context, viewIntent, uri);

            return PendingIntent.getActivity(context, requestCode, viewIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void grantUriPermissions(Context context, Intent intent, Uri uri) {
        try {
            List<ResolveInfo> resInfoList = context.getPackageManager().queryIntentActivities(
                    intent, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        } catch (Exception ignored) {}
    }
}
