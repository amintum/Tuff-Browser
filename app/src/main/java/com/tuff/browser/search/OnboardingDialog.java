package com.tuff.browser.search;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.tuff.browser.R;
import com.tuff.browser.util.Prefs;

public class OnboardingDialog extends BottomSheetDialog {

    public interface OnEngineSelectedListener {
        void onEngineSelected(SearchEngine engine);
    }

    private final SearchEngineManager engineManager;
    private final Prefs prefs;
    private final OnEngineSelectedListener listener;

    public OnboardingDialog(@NonNull Context context, SearchEngineManager engineManager, Prefs prefs, OnEngineSelectedListener listener) {
        super(context);
        this.engineManager = engineManager;
        this.prefs = prefs;
        this.listener = listener;
        setCancelable(false);
        setCanceledOnTouchOutside(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_onboarding);

        RadioGroup rg = findViewById(R.id.rg_search_engines);
        RadioButton rbBrave = findViewById(R.id.rb_brave);
        RadioButton rbDdg = findViewById(R.id.rb_ddg);
        RadioButton rbStartpage = findViewById(R.id.rb_startpage);
        RadioButton rbCustom = findViewById(R.id.rb_custom);
        EditText etCustomUrl = findViewById(R.id.et_custom_engine_url);
        MaterialButton btnContinue = findViewById(R.id.btn_continue);

        if (rbBrave != null) rbBrave.setChecked(true);

        if (rg != null && etCustomUrl != null) {
            rg.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.rb_custom) {
                    etCustomUrl.setVisibility(View.VISIBLE);
                } else {
                    etCustomUrl.setVisibility(View.GONE);
                }
            });
        }

        if (btnContinue != null) {
            btnContinue.setOnClickListener(v -> {
                String chosenId = SearchEngine.ID_BRAVE;
                if (rbDdg != null && rbDdg.isChecked()) {
                    chosenId = SearchEngine.ID_DUCKDUCKGO;
                } else if (rbStartpage != null && rbStartpage.isChecked()) {
                    chosenId = SearchEngine.ID_STARTPAGE;
                } else if (rbCustom != null && rbCustom.isChecked()) {
                    chosenId = SearchEngine.ID_CUSTOM;
                    if (etCustomUrl != null) {
                        String url = etCustomUrl.getText().toString().trim();
                        if (!url.isEmpty()) {
                            engineManager.setCustomEngineUrl(url);
                        }
                    }
                }

                engineManager.setActiveEngine(chosenId);
                prefs.setFirstLaunchCompleted();

                if (listener != null) {
                    listener.onEngineSelected(engineManager.getActiveEngine());
                }
                dismiss();
            });
        }
    }
}
