package com.tuff.browser.tab;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.tuff.browser.R;

import java.util.List;

public class TabSheetDialog extends BottomSheetDialog {

    public interface TabActionListener {
        void onTabSelected(int index);
        void onNewTabRequested();
        void onCloseAllTabs();
    }

    private final TabManager tabManager;
    private final TabActionListener listener;
    private TabAdapter adapter;

    public TabSheetDialog(@NonNull Context context, TabManager tabManager, TabActionListener listener) {
        super(context);
        this.tabManager = tabManager;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_tabs);

        RecyclerView rv = findViewById(R.id.rv_tabs);
        View btnAdd = findViewById(R.id.btn_add_tab);
        View btnCloseAll = findViewById(R.id.btn_close_all_tabs);

        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(getContext()));
            adapter = new TabAdapter(tabManager.getTabs());
            rv.setAdapter(adapter);
        }

        if (btnAdd != null) {
            btnAdd.setOnClickListener(v -> {
                dismiss();
                if (listener != null) listener.onNewTabRequested();
            });
        }

        if (btnCloseAll != null) {
            btnCloseAll.setOnClickListener(v -> {
                dismiss();
                if (listener != null) listener.onCloseAllTabs();
            });
        }
    }

    private class TabAdapter extends RecyclerView.Adapter<TabViewHolder> {
        private final List<TabModel> items;

        TabAdapter(List<TabModel> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public TabViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tab, parent, false);
            return new TabViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TabViewHolder holder, int position) {
            TabModel tab = items.get(position);
            holder.tvTitle.setText(tab.getTitle());
            holder.tvUrl.setText(tab.getUrl());

            holder.clickArea.setOnClickListener(v -> {
                dismiss();
                if (listener != null) listener.onTabSelected(holder.getAdapterPosition());
            });

            holder.btnClose.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                tabManager.closeTab(pos);
                notifyItemRemoved(pos);
                notifyItemRangeChanged(pos, items.size());
                if (items.isEmpty()) {
                    dismiss();
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static class TabViewHolder extends RecyclerView.ViewHolder {
        View clickArea;
        TextView tvTitle;
        TextView tvUrl;
        ImageButton btnClose;

        TabViewHolder(@NonNull View itemView) {
            super(itemView);
            clickArea = itemView.findViewById(R.id.tab_click_area);
            tvTitle = itemView.findViewById(R.id.tab_title);
            tvUrl = itemView.findViewById(R.id.tab_url);
            btnClose = itemView.findViewById(R.id.btn_close_tab);
        }
    }
}
