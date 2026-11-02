package com.tuff.browser.download;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DownloadDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "tuff_downloads.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_DOWNLOADS = "downloads";
    public static final String COL_ID = "id";
    public static final String COL_URL = "url";
    public static final String COL_USER_AGENT = "user_agent";
    public static final String COL_CONTENT_DISPOSITION = "content_disposition";
    public static final String COL_FILENAME = "filename";
    public static final String COL_DEST_PATH = "dest_path";
    public static final String COL_MIME_TYPE = "mime_type";
    public static final String COL_TOTAL_BYTES = "total_bytes";
    public static final String COL_DOWNLOADED_BYTES = "downloaded_bytes";
    public static final String COL_STATUS = "status";
    public static final String COL_TIMESTAMP = "timestamp";

    private static DownloadDbHelper instance;

    public static synchronized DownloadDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DownloadDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DownloadDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_DOWNLOADS + " (" +
                COL_ID + " TEXT PRIMARY KEY, " +
                COL_URL + " TEXT, " +
                COL_USER_AGENT + " TEXT, " +
                COL_CONTENT_DISPOSITION + " TEXT, " +
                COL_FILENAME + " TEXT, " +
                COL_DEST_PATH + " TEXT, " +
                COL_MIME_TYPE + " TEXT, " +
                COL_TOTAL_BYTES + " INTEGER, " +
                COL_DOWNLOADED_BYTES + " INTEGER, " +
                COL_STATUS + " TEXT, " +
                COL_TIMESTAMP + " INTEGER);");
        db.execSQL("CREATE INDEX idx_downloads_time ON " + TABLE_DOWNLOADS + " (" + COL_TIMESTAMP + " DESC);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DOWNLOADS);
        onCreate(db);
    }

    public synchronized void saveOrUpdateTask(DownloadTask task) {
        if (task == null || task.getId() == null) return;
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues cv = new ContentValues();
            cv.put(COL_ID, task.getId());
            cv.put(COL_URL, task.getUrl());
            cv.put(COL_USER_AGENT, task.getUserAgent());
            cv.put(COL_CONTENT_DISPOSITION, task.getContentDisposition());
            cv.put(COL_FILENAME, task.getFilename());
            cv.put(COL_DEST_PATH, task.getDestinationFile() != null ? task.getDestinationFile().getAbsolutePath() : null);
            cv.put(COL_MIME_TYPE, task.getMimeType());
            cv.put(COL_TOTAL_BYTES, task.getTotalBytes());
            cv.put(COL_DOWNLOADED_BYTES, task.getDownloadedBytes());
            cv.put(COL_STATUS, task.getStatus().name());
            cv.put(COL_TIMESTAMP, System.currentTimeMillis());

            db.insertWithOnConflict(TABLE_DOWNLOADS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception ignored) {}
    }

    public synchronized List<DownloadTask> getAllTasks() {
        List<DownloadTask> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_DOWNLOADS, null, null, null, null, null, COL_TIMESTAMP + " DESC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID));
                    String url = cursor.getString(cursor.getColumnIndexOrThrow(COL_URL));
                    String userAgent = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_AGENT));
                    String disposition = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT_DISPOSITION));
                    String filename = cursor.getString(cursor.getColumnIndexOrThrow(COL_FILENAME));
                    String destPath = cursor.getString(cursor.getColumnIndexOrThrow(COL_DEST_PATH));
                    String mimeType = cursor.getString(cursor.getColumnIndexOrThrow(COL_MIME_TYPE));
                    long totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TOTAL_BYTES));
                    long downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DOWNLOADED_BYTES));
                    String statusName = cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS));

                    File destFile = destPath != null ? new File(destPath) : null;
                    DownloadTask task = new DownloadTask(id, url, userAgent, disposition, filename, destFile, mimeType);
                    task.setTotalBytes(totalBytes);
                    task.setDownloadedBytes(downloadedBytes);

                    DownloadTask.Status status;
                    try {
                        status = DownloadTask.Status.valueOf(statusName);
                    } catch (Exception e) {
                        status = DownloadTask.Status.COMPLETED;
                    }

                    // If app died mid-download, mark as paused
                    if (status == DownloadTask.Status.DOWNLOADING || status == DownloadTask.Status.PENDING) {
                        status = DownloadTask.Status.PAUSED;
                    }
                    task.setStatus(status);

                    list.add(task);
                } while (cursor.moveToNext());
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    public synchronized void deleteTask(String id) {
        if (id == null) return;
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.delete(TABLE_DOWNLOADS, COL_ID + " = ?", new String[]{id});
        } catch (Exception ignored) {}
    }

    public synchronized void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.delete(TABLE_DOWNLOADS, null, null);
        } catch (Exception ignored) {}
    }
}
