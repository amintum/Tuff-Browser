package com.tuff.browser.download;

import java.io.File;
import java.util.Locale;

public class DownloadTask {
    public enum Status {
        PENDING,
        DOWNLOADING,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private final String id;
    private final String url;
    private final String userAgent;
    private final String contentDisposition;
    private String filename;
    private File destinationFile;
    private long totalBytes = -1;
    private long downloadedBytes = 0;
    private long speedBytesPerSec = 0;
    private Status status = Status.PENDING;
    private String mimeType;

    public DownloadTask(String id, String url, String userAgent, String contentDisposition, String filename, File destinationFile, String mimeType) {
        this.id = id;
        this.url = url;
        this.userAgent = userAgent;
        this.contentDisposition = contentDisposition;
        this.filename = filename;
        this.destinationFile = destinationFile;
        this.mimeType = mimeType;
    }

    public String getId() { return id; }
    public String getUrl() { return url; }
    public String getUserAgent() { return userAgent; }
    public String getContentDisposition() { return contentDisposition; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public File getDestinationFile() { return destinationFile; }
    public void setDestinationFile(File destinationFile) { this.destinationFile = destinationFile; }
    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
    public long getDownloadedBytes() { return downloadedBytes; }
    public void setDownloadedBytes(long downloadedBytes) { this.downloadedBytes = downloadedBytes; }
    public long getSpeedBytesPerSec() { return speedBytesPerSec; }
    public void setSpeedBytesPerSec(long speedBytesPerSec) { this.speedBytesPerSec = speedBytesPerSec; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public int getProgressPercentage() {
        if (totalBytes <= 0) return 0;
        int p = (int) ((downloadedBytes * 100) / totalBytes);
        return Math.min(100, Math.max(0, p));
    }

    public String getFormattedProgress() {
        if (totalBytes > 0) {
            return formatBytes(downloadedBytes) + " / " + formatBytes(totalBytes);
        } else if (downloadedBytes > 0) {
            return formatBytes(downloadedBytes);
        } else {
            return "0 KB";
        }
    }

    public String getFormattedSpeed() {
        if (speedBytesPerSec <= 0) return "";
        return formatBytes(speedBytesPerSec) + "/s";
    }

    public static String formatBytes(long bytes) {
        if (bytes < 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024 * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
