package com.tuff.browser.download;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.tuff.browser.R;

import java.io.File;
import java.util.List;

public class DownloadManagerSheet extends BottomSheetDialog implements DownloadRepository.DownloadObserver {

    private final DownloadRepository repository;
    private DownloadAdapter adapter;
    private TextView tvNoDownloads;
    private RecyclerView rvDownloads;

    public DownloadManagerSheet(@NonNull Context context) {
        super(context);
        this.repository = DownloadRepository.getInstance();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_downloads);

        tvNoDownloads = findViewById(R.id.tv_no_downloads);
        rvDownloads = findViewById(R.id.rv_downloads);

        if (rvDownloads != null) {
            rvDownloads.setLayoutManager(new LinearLayoutManager(getContext()));
            adapter = new DownloadAdapter(repository.getTasks());
            rvDownloads.setAdapter(adapter);
        }

        updateEmptyState();
        repository.registerObserver(this);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        repository.unregisterObserver(this);
    }

    private void updateEmptyState() {
        List<DownloadTask> tasks = repository.getTasks();
        if (tvNoDownloads != null && rvDownloads != null) {
            if (tasks.isEmpty()) {
                tvNoDownloads.setVisibility(View.VISIBLE);
                rvDownloads.setVisibility(View.GONE);
            } else {
                tvNoDownloads.setVisibility(View.GONE);
                rvDownloads.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onDownloadProgress(DownloadTask task) {
        if (rvDownloads != null) {
            rvDownloads.post(() -> {
                if (adapter != null) adapter.updateData(repository.getTasks());
                updateEmptyState();
            });
        }
    }

    @Override
    public void onDownloadCompleted(DownloadTask task) {
        onDownloadProgress(task);
    }

    @Override
    public void onDownloadFailed(DownloadTask task) {
        onDownloadProgress(task);
    }

    private class DownloadAdapter extends RecyclerView.Adapter<DownloadViewHolder> {
        private List<DownloadTask> items;

        DownloadAdapter(List<DownloadTask> items) {
            this.items = items;
        }

        void updateData(List<DownloadTask> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public DownloadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download, parent, false);
            return new DownloadViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DownloadViewHolder holder, int position) {
            DownloadTask task = items.get(position);
            holder.tvFilename.setText(task.getFilename());
            holder.progressBar.setProgress(task.getProgressPercentage());

            if (task.getStatus() == DownloadTask.Status.COMPLETED) {
                holder.tvStatus.setText(R.string.download_completed);
                holder.progressBar.setVisibility(View.GONE);
            } else if (task.getStatus() == DownloadTask.Status.FAILED) {
                holder.tvStatus.setText(R.string.download_failed);
                holder.progressBar.setVisibility(View.GONE);
            } else {
                holder.tvStatus.setText(task.getProgressPercentage() + "%");
                holder.progressBar.setVisibility(View.VISIBLE);
            }

            holder.itemView.setOnClickListener(v -> {
                if (task.getStatus() == DownloadTask.Status.COMPLETED) {
                    openFile(task);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private void openFile(DownloadTask task) {
            File file = task.getDestinationFile();
            if (file != null && file.exists()) {
                Context context = getContext();
                try {
                    Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, task.getMimeType() != null ? task.getMimeType() : "*/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(context, "Cannot open file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private static class DownloadViewHolder extends RecyclerView.ViewHolder {
        TextView tvFilename;
        TextView tvStatus;
        ProgressBar progressBar;
        ImageButton btnAction;

        DownloadViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFilename = itemView.findViewById(R.id.tv_download_filename);
            tvStatus = itemView.findViewById(R.id.tv_download_status);
            progressBar = itemView.findViewById(R.id.pb_download);
            btnAction = itemView.findViewById(R.id.btn_download_action);
        }
    }
}
