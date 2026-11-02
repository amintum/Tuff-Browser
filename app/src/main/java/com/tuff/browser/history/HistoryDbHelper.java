package com.tuff.browser.history;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class HistoryDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "tuff_history.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_HISTORY = "history";
    public static final String COL_ID = "id";
    public static final String COL_TITLE = "title";
    public static final String COL_URL = "url";
    public static final String COL_TIMESTAMP = "timestamp";

    private static HistoryDbHelper instance;

    public static synchronized HistoryDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new HistoryDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private HistoryDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_HISTORY + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TITLE + " TEXT, " +
                COL_URL + " TEXT NOT NULL, " +
                COL_TIMESTAMP + " INTEGER NOT NULL);");
        db.execSQL("CREATE INDEX idx_history_time ON " + TABLE_HISTORY + " (" + COL_TIMESTAMP + " DESC);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
        onCreate(db);
    }

    public synchronized void addOrUpdateHistory(String title, String url) {
        if (url == null || url.trim().isEmpty() || url.startsWith("about:") || url.startsWith("file:///android_asset/")) {
            return;
        }

        String safeTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : url;
        long now = System.currentTimeMillis();

        SQLiteDatabase db = getWritableDatabase();
        try {
            // If the latest visited page is the same URL, simply update timestamp and title
            Cursor cursor = db.rawQuery("SELECT " + COL_ID + ", " + COL_URL + " FROM " + TABLE_HISTORY + " ORDER BY " + COL_TIMESTAMP + " DESC LIMIT 1", null);
            if (cursor != null && cursor.moveToFirst()) {
                long lastId = cursor.getLong(0);
                String lastUrl = cursor.getString(1);
                cursor.close();

                if (url.equals(lastUrl)) {
                    ContentValues cv = new ContentValues();
                    cv.put(COL_TITLE, safeTitle);
                    cv.put(COL_TIMESTAMP, now);
                    db.update(TABLE_HISTORY, cv, COL_ID + " = ?", new String[]{String.valueOf(lastId)});
                    return;
                }
            } else if (cursor != null) {
                cursor.close();
            }

            ContentValues cv = new ContentValues();
            cv.put(COL_TITLE, safeTitle);
            cv.put(COL_URL, url);
            cv.put(COL_TIMESTAMP, now);
            db.insert(TABLE_HISTORY, null, cv);
        } catch (Exception ignored) {}
    }

    public synchronized List<HistoryItem> getHistory(int limit) {
        List<HistoryItem> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_HISTORY,
                    new String[]{COL_ID, COL_TITLE, COL_URL, COL_TIMESTAMP},
                    null, null, null, null,
                    COL_TIMESTAMP + " DESC",
                    String.valueOf(limit));

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(0);
                    String title = cursor.getString(1);
                    String url = cursor.getString(2);
                    long timestamp = cursor.getLong(3);
                    list.add(new HistoryItem(id, title, url, timestamp));
                } while (cursor.moveToNext());
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    public synchronized void clearHistory() {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.delete(TABLE_HISTORY, null, null);
        } catch (Exception ignored) {}
    }

    public synchronized void deleteItem(long id) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.delete(TABLE_HISTORY, COL_ID + " = ?", new String[]{String.valueOf(id)});
        } catch (Exception ignored) {}
    }
}
