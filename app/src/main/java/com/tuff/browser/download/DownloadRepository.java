package com.tuff.browser.download;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DownloadRepository {
    private static DownloadRepository instance;
    private final List<DownloadTask> tasks = Collections.synchronizedList(new ArrayList<>());
    private final List<DownloadObserver> observers = Collections.synchronizedList(new ArrayList<>());
    private DownloadDbHelper dbHelper;
    private boolean initialized = false;

    public interface DownloadObserver {
        void onDownloadProgress(DownloadTask task);
        void onDownloadStatusChanged(DownloadTask task);
    }

    private DownloadRepository() {}

    public static synchronized DownloadRepository getInstance() {
        if (instance == null) {
            instance = new DownloadRepository();
        }
        return instance;
    }

    public synchronized void init(Context context) {
        if (!initialized && context != null) {
            dbHelper = DownloadDbHelper.getInstance(context);
            List<DownloadTask> saved = dbHelper.getAllTasks();
            tasks.clear();
            tasks.addAll(saved);
            initialized = true;
        }
    }

    public void addTask(DownloadTask task) {
        tasks.add(0, task);
        if (dbHelper != null) {
            dbHelper.saveOrUpdateTask(task);
        }
        notifyStatusChanged(task);
    }

    public void removeTask(String id) {
        DownloadTask task = findTaskById(id);
        if (task != null) {
            tasks.remove(task);
            if (dbHelper != null) {
                dbHelper.deleteTask(id);
            }
            notifyStatusChanged(task);
        }
    }

    public void clearAll() {
        tasks.clear();
        if (dbHelper != null) {
            dbHelper.clearAll();
        }
        for (DownloadObserver o : observers) {
            o.onDownloadStatusChanged(null);
        }
    }

    public List<DownloadTask> getTasks() {
        return new ArrayList<>(tasks);
    }

    public DownloadTask findTaskById(String id) {
        synchronized (tasks) {
            for (DownloadTask task : tasks) {
                if (task.getId().equals(id)) {
                    return task;
                }
            }
        }
        return null;
    }

    public void registerObserver(DownloadObserver observer) {
        observers.add(observer);
    }

    public void unregisterObserver(DownloadObserver observer) {
        observers.remove(observer);
    }

    public void notifyProgress(DownloadTask task) {
        for (DownloadObserver o : observers) {
            o.onDownloadProgress(task);
        }
    }

    public void notifyStatusChanged(DownloadTask task) {
        if (dbHelper != null && task != null) {
            dbHelper.saveOrUpdateTask(task);
        }
        for (DownloadObserver o : observers) {
            o.onDownloadStatusChanged(task);
        }
    }
}
