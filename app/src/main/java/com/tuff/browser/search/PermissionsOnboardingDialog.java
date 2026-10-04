package com.tuff.browser.search;

import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.tuff.browser.R;

public class PermissionsOnboardingDialog extends BottomSheetDialog {

    public interface OnPermissionRequestListener {
        void onRequestPermissions();
        void onDismissed();
    }

    private final OnPermissionRequestListener listener;

    public PermissionsOnboardingDialog(@NonNull Context context, OnPermissionRequestListener listener) {
        super(context);
        this.listener = listener;
        setCancelable(false);
        setCanceledOnTouchOutside(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_permissions_onboarding);

        MaterialButton btnGrant = findViewById(R.id.btn_grant_permissions);
        TextView btnSkip = findViewById(R.id.btn_skip_permissions);

        if (btnGrant != null) {
            btnGrant.setOnClickListener(v -> {
                dismiss();
                if (listener != null) listener.onRequestPermissions();
            });
        }

        if (btnSkip != null) {
            btnSkip.setOnClickListener(v -> {
                dismiss();
                if (listener != null) listener.onDismissed();
            });
        }
    }
}
