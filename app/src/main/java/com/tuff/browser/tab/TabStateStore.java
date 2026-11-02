package com.tuff.browser.tab;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Parcel;

import com.tuff.browser.web.TuffWebView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class TabStateStore {

    private static final String PREVIEW_FILENAME = "tab_preview.jpg";
    private static final String STATE_FILENAME = "tab_state.bundle";

    public static void saveTabPreview(Context context, TuffWebView webView) {
        if (context == null || webView == null) return;
        int w = webView.getWidth();
        int h = webView.getHeight();
        if (w <= 0 || h <= 0) return;

        try {
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            webView.draw(canvas);

            File cacheDir = context.getCacheDir();
            File previewFile = new File(cacheDir, PREVIEW_FILENAME);
            FileOutputStream fos = new FileOutputStream(previewFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            fos.flush();
            fos.close();
            bitmap.recycle();
        } catch (Throwable ignored) {}
    }

    public static Bitmap getTabPreview(Context context) {
        if (context == null) return null;
        try {
            File previewFile = new File(context.getCacheDir(), PREVIEW_FILENAME);
            if (previewFile.exists() && previewFile.length() > 0) {
                return BitmapFactory.decodeFile(previewFile.getAbsolutePath());
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static void saveWebViewState(Context context, TuffWebView webView) {
        if (context == null || webView == null) return;
        try {
            Bundle bundle = new Bundle();
            webView.saveState(bundle);

            Parcel parcel = Parcel.obtain();
            parcel.writeBundle(bundle);
            byte[] bytes = parcel.marshall();
            parcel.recycle();

            File stateFile = new File(context.getCacheDir(), STATE_FILENAME);
            FileOutputStream fos = new FileOutputStream(stateFile);
            fos.write(bytes);
            fos.flush();
            fos.close();
        } catch (Throwable ignored) {}
    }

    public static boolean restoreWebViewState(Context context, TuffWebView webView) {
        if (context == null || webView == null) return false;
        try {
            File stateFile = new File(context.getCacheDir(), STATE_FILENAME);
            if (!stateFile.exists() || stateFile.length() == 0) return false;

            FileInputStream fis = new FileInputStream(stateFile);
            byte[] bytes = new byte[(int) stateFile.length()];
            fis.read(bytes);
            fis.close();

            Parcel parcel = Parcel.obtain();
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);
            Bundle bundle = parcel.readBundle(context.getClassLoader());
            parcel.recycle();

            if (bundle != null) {
                webView.restoreState(bundle);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static void clearState(Context context) {
        if (context == null) return;
        try {
            File previewFile = new File(context.getCacheDir(), PREVIEW_FILENAME);
            if (previewFile.exists()) previewFile.delete();

            File stateFile = new File(context.getCacheDir(), STATE_FILENAME);
            if (stateFile.exists()) stateFile.delete();
        } catch (Throwable ignored) {}
    }
}
