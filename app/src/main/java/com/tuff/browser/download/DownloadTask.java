package com.tuff.browser.download;

import java.io.File;

public class DownloadTask {
    public enum Status {
        PENDING,
        DOWNLOADING,
        PAUSED,
        COMPLETED,
        FAILED
    }

    private final String id;
    private final String url;
    private String filename;
    private File destinationFile;
    private long totalBytes = -1;
    private long downloadedBytes = 0;
    private Status status = Status.PENDING;
    private String mimeType;

    public DownloadTask(String id, String url, String filename, File destinationFile, String mimeType) {
        this.id = id;
        this.url = url;
        this.filename = filename;
        this.destinationFile = destinationFile;
        this.mimeType = mimeType;
    }

    public String getId() { return id; }
    public String getUrl() { return url; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public File getDestinationFile() { return destinationFile; }
    public void setDestinationFile(File destinationFile) { this.destinationFile = destinationFile; }
    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
    public long getDownloadedBytes() { return downloadedBytes; }
    public void setDownloadedBytes(long downloadedBytes) { this.downloadedBytes = downloadedBytes; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public int getProgressPercentage() {
        if (totalBytes <= 0) return 0;
        return (int) ((downloadedBytes * 100) / totalBytes);
    }
}
