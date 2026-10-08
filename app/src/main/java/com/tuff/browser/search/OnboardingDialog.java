package com.tuff.browser.search;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.RadioButton;

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

        getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        getBehavior().setSkipCollapsed(true);

        RadioButton rbBrave = findViewById(R.id.rb_brave);
        RadioButton rbDdg = findViewById(R.id.rb_ddg);
        RadioButton rbGoogle = findViewById(R.id.rb_google);
        RadioButton rbCustom = findViewById(R.id.rb_custom);
        View rowGoogle = findViewById(R.id.row_google);
        View btnWhy = findViewById(R.id.btn_google_why);
        EditText etCustomUrl = findViewById(R.id.et_custom_engine_url);
        MaterialButton btnContinue = findViewById(R.id.btn_continue);

        if (rbGoogle != null) {
            rbGoogle.setText(Html.fromHtml("Google <font color='#FF5252'>(Unsafe · Not recommended)</font>", Html.FROM_HTML_MODE_COMPACT));
        }

        if (rbBrave != null) rbBrave.setChecked(true);

        View.OnClickListener selectListener = v -> {
            int id = v.getId();
            boolean isBrave = (id == R.id.rb_brave);
            boolean isDdg = (id == R.id.rb_ddg);
            boolean isGoogle = (id == R.id.rb_google || id == R.id.row_google);
            boolean isCustom = (id == R.id.rb_custom);

            if (rbBrave != null) rbBrave.setChecked(isBrave);
            if (rbDdg != null) rbDdg.setChecked(isDdg);
            if (rbGoogle != null) rbGoogle.setChecked(isGoogle);
            if (rbCustom != null) rbCustom.setChecked(isCustom);

            if (etCustomUrl != null) {
                etCustomUrl.setVisibility(isCustom ? View.VISIBLE : View.GONE);
            }
        };

        if (rbBrave != null) rbBrave.setOnClickListener(selectListener);
        if (rbDdg != null) rbDdg.setOnClickListener(selectListener);
        if (rbGoogle != null) rbGoogle.setOnClickListener(selectListener);
        if (rowGoogle != null) rowGoogle.setOnClickListener(selectListener);
        if (rbCustom != null) rbCustom.setOnClickListener(selectListener);

        if (btnWhy != null) {
            btnWhy.setOnClickListener(v -> showWhyGoogleDialog());
        }

        if (btnContinue != null) {
            btnContinue.setOnClickListener(v -> {
                String chosenId = SearchEngine.ID_BRAVE;
                if (rbDdg != null && rbDdg.isChecked()) {
                    chosenId = SearchEngine.ID_DUCKDUCKGO;
                } else if (rbGoogle != null && rbGoogle.isChecked()) {
                    chosenId = SearchEngine.ID_GOOGLE;
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

    private void showWhyGoogleDialog() {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_why_google);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.90);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        View btnClose = dialog.findViewById(R.id.btn_why_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }
        dialog.show();
    }
}
