package com.tuff.browser.search;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.tuff.browser.R;

public class CornerEngineSwitcherDialog extends BottomSheetDialog {

    public interface OnEngineChangedListener {
        void onEngineChanged(SearchEngine newEngine);
    }

    private final SearchEngineManager engineManager;
    private final OnEngineChangedListener listener;

    public CornerEngineSwitcherDialog(@NonNull Context context, SearchEngineManager engineManager, OnEngineChangedListener listener) {
        super(context);
        this.engineManager = engineManager;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_corner_search_switch);

        View itemBrave = findViewById(R.id.item_engine_brave);
        View itemDdg = findViewById(R.id.item_engine_ddg);
        View itemStartpage = findViewById(R.id.item_engine_startpage);
        View itemCustom = findViewById(R.id.item_engine_custom);

        ImageView checkBrave = findViewById(R.id.check_brave);
        ImageView checkDdg = findViewById(R.id.check_ddg);
        ImageView checkStartpage = findViewById(R.id.check_startpage);
        ImageView checkCustom = findViewById(R.id.check_custom);

        String activeId = engineManager.getActiveEngine().getId();
        if (checkBrave != null) checkBrave.setVisibility(SearchEngine.ID_BRAVE.equals(activeId) ? View.VISIBLE : View.GONE);
        if (checkDdg != null) checkDdg.setVisibility(SearchEngine.ID_DUCKDUCKGO.equals(activeId) ? View.VISIBLE : View.GONE);
        if (checkStartpage != null) checkStartpage.setVisibility(SearchEngine.ID_STARTPAGE.equals(activeId) ? View.VISIBLE : View.GONE);
        if (checkCustom != null) checkCustom.setVisibility(SearchEngine.ID_CUSTOM.equals(activeId) ? View.VISIBLE : View.GONE);

        if (itemBrave != null) {
            itemBrave.setOnClickListener(v -> selectEngine(SearchEngine.ID_BRAVE));
        }
        if (itemDdg != null) {
            itemDdg.setOnClickListener(v -> selectEngine(SearchEngine.ID_DUCKDUCKGO));
        }
        if (itemStartpage != null) {
            itemStartpage.setOnClickListener(v -> selectEngine(SearchEngine.ID_STARTPAGE));
        }
        if (itemCustom != null) {
            itemCustom.setOnClickListener(v -> promptCustomEngineUrl());
        }
    }

    private void selectEngine(String id) {
        engineManager.setActiveEngine(id);
        if (listener != null) {
            listener.onEngineChanged(engineManager.getActiveEngine());
        }
        dismiss();
    }

    private void promptCustomEngineUrl() {
        Context context = getContext();
        final EditText input = new EditText(context);
        input.setHint(R.string.custom_engine_hint);
        input.setText(engineManager.getActiveEngine().getId().equals(SearchEngine.ID_CUSTOM) 
                ? engineManager.getActiveEngine().getSearchUrlTemplate() : "");

        new AlertDialog.Builder(context)
                .setTitle(R.string.search_custom)
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    engineManager.setCustomEngineUrl(url);
                    selectEngine(SearchEngine.ID_CUSTOM);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
