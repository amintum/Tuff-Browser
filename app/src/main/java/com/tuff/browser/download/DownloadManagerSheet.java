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
    public void onDownloadStatusChanged(DownloadTask task) {
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

            DownloadTask.Status status = task.getStatus();

            if (status == DownloadTask.Status.COMPLETED) {
                holder.progressBar.setVisibility(View.GONE);
                holder.tvStatus.setText(R.string.download_completed);
                holder.tvInfo.setText(task.getFormattedProgress());
                holder.btnPauseResume.setVisibility(View.GONE);
                holder.btnCancel.setImageResource(R.drawable.ic_delete);
                holder.btnCancel.setContentDescription(getContext().getString(R.string.delete));

            } else if (status == DownloadTask.Status.PAUSED) {
                holder.progressBar.setVisibility(View.VISIBLE);
                holder.progressBar.setProgress(task.getProgressPercentage());
                holder.tvStatus.setText(R.string.download_paused);
                holder.tvInfo.setText(task.getFormattedProgress());
                holder.btnPauseResume.setVisibility(View.VISIBLE);
                holder.btnPauseResume.setImageResource(R.drawable.ic_play);
                holder.btnCancel.setImageResource(R.drawable.ic_close);

            } else if (status == DownloadTask.Status.FAILED) {
                holder.progressBar.setVisibility(View.GONE);
                holder.tvStatus.setText(R.string.download_failed);
                holder.tvInfo.setText(task.getFormattedProgress());
                holder.btnPauseResume.setVisibility(View.VISIBLE);
                holder.btnPauseResume.setImageResource(R.drawable.ic_play);
                holder.btnCancel.setImageResource(R.drawable.ic_close);

            } else { // DOWNLOADING or PENDING
                holder.progressBar.setVisibility(View.VISIBLE);
                holder.progressBar.setProgress(task.getProgressPercentage());
                holder.tvStatus.setText(task.getProgressPercentage() + "%");
                String speed = task.getFormattedSpeed();
                holder.tvInfo.setText(task.getFormattedProgress() + (speed.isEmpty() ? "" : " • " + speed));
                holder.btnPauseResume.setVisibility(View.VISIBLE);
                holder.btnPauseResume.setImageResource(R.drawable.ic_pause);
                holder.btnCancel.setImageResource(R.drawable.ic_close);
            }

            // Pause / Resume action
            holder.btnPauseResume.setOnClickListener(v -> {
                Context context = getContext();
                if (task.getStatus() == DownloadTask.Status.DOWNLOADING) {
                    DownloadService.pause(context, task.getId());
                } else {
                    DownloadService.resume(context, task.getId());
                }
            });

            // Cancel / Delete action
            holder.btnCancel.setOnClickListener(v -> {
                Context context = getContext();
                if (task.getStatus() == DownloadTask.Status.COMPLETED) {
                    File file = task.getDestinationFile();
                    if (file != null && file.exists()) {
                        file.delete();
                    }
                    repository.removeTask(task.getId());
                    updateData(repository.getTasks());
                    updateEmptyState();
                } else {
                    DownloadService.cancel(context, task.getId());
                }
            });

            // Open completed download
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
        TextView tvInfo;
        TextView tvStatus;
        ProgressBar progressBar;
        ImageButton btnPauseResume;
        ImageButton btnCancel;

        DownloadViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFilename = itemView.findViewById(R.id.tv_download_filename);
            tvInfo = itemView.findViewById(R.id.tv_download_info);
            tvStatus = itemView.findViewById(R.id.tv_download_status);
            progressBar = itemView.findViewById(R.id.pb_download);
            btnPauseResume = itemView.findViewById(R.id.btn_download_pause_resume);
            btnCancel = itemView.findViewById(R.id.btn_download_cancel);
        }
    }
}
