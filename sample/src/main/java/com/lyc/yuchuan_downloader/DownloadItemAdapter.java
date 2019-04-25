package com.lyc.yuchuan_downloader;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;
import com.lyc.downloader.utils.StringUtil;
import com.lyc.yuchuan_downloader.DownloadItemAdapter.ViewHolder;

import java.util.List;

import static com.lyc.downloader.DownloadTask.*;

/**
 * @author liuyuchuan
 * @date 2019-04-24
 * @email kevinliu.sir@qq.com
 */
public class DownloadItemAdapter extends RecyclerView.Adapter<ViewHolder> implements ListUpdateCallback {
    private final List<DownloadItem> downloadItemList;
    private final OnItemButtonClickListener onItemButtonClickListener;

    public DownloadItemAdapter(List<DownloadItem> downloadItemList, OnItemButtonClickListener onItemButtonClickListener) {
        this.downloadItemList = downloadItemList;
        this.onItemButtonClickListener = onItemButtonClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download, parent, false), onItemButtonClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(downloadItemList.get(position));
    }

    @Override
    public int getItemCount() {
        return downloadItemList.size();
    }

    @Override
    public void onInserted(int position, int count) {
        notifyItemRangeInserted(position, count);
    }

    @Override
    public void onRemoved(int position, int count) {
        notifyItemRangeRemoved(position, count);
    }

    @Override
    public void onMoved(int fromPosition, int toPosition) {
        notifyItemMoved(fromPosition, toPosition);
    }

    @Override
    public void onChanged(int position, int count, @Nullable Object payload) {
        notifyItemRangeChanged(position, count, payload);
    }

    interface OnItemButtonClickListener {
        void pauseItem(DownloadItem item);

        void startItem(DownloadItem item);

        void deleteItem(DownloadItem item);
    }

    static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private final OnItemButtonClickListener onItemButtonClickListener;
        private Button button;
        private ProgressBar progressBar;
        private TextView name;
        private TextView progress;
        private TextView state;
        private TextView speed;
        private Button delete;
        private DownloadItem item;

        ViewHolder(@NonNull View itemView, OnItemButtonClickListener onItemButtonClickListener) {
            super(itemView);
            button = itemView.findViewById(R.id.button);
            progressBar = itemView.findViewById(R.id.progress_bar);
            name = itemView.findViewById(R.id.name);
            progress = itemView.findViewById(R.id.progress_tv);
            state = itemView.findViewById(R.id.state);
            speed = itemView.findViewById(R.id.speed);
            delete = itemView.findViewById(R.id.delete_button);
            this.onItemButtonClickListener = onItemButtonClickListener;
            delete.setOnClickListener(this);
            button.setOnClickListener(this);
            progressBar.setMax(100);
        }

        private void bind(DownloadItem item) {
            this.item = item;
            name.setText(FilenameUtil.parseFileName(item.getPath()));
            int progress;
            long cur = item.getDownloadedSize();
            double total = item.getTotalSize();
            if (total <= 0) {
                progress = 0;
            } else {
                progress = Math.max((int) (cur / total * 100), 0);
            }
            progressBar.setProgress(progress);
            this.progress.setText((StringUtil.byteToString(cur) + "/" + StringUtil.byteToString(total)));
            switch (item.getDownloadState()) {
                case PENDING:
                case PREPARING:
                    state.setText("连接中");
                    speed.setText("");
                    button.setText("暂停");
                    button.setEnabled(true);
                    break;
                case RUNNING:
                    state.setText("下载中");
                    speed.setText(StringUtil.bpsToString(item.getBps()));
                    button.setText("暂停");
                    button.setEnabled(true);
                    break;
                case PAUSING:
                    state.setText("正在暂停");
                    speed.setText(StringUtil.bpsToString(item.getBps()));
                    button.setText("暂停中");
                    button.setEnabled(false);
                    break;
                case PAUSED:
                    state.setText("暂停中");
                    speed.setText("");
                    button.setText("继续");
                    button.setEnabled(true);
                    break;
                case FINISH:
                    state.setText("已完成");
                    speed.setText("");
                    button.setText("已完成");
                    button.setEnabled(false);
                    this.progress.setText(StringUtil.byteToString(total));
                    break;
                case WAITING:
                    state.setText(("等待中"));
                    speed.setText("");
                    button.setText("暂停");
                    button.setEnabled(true);
                    break;
                case CANCELED:
                    state.setText("已取消");
                    speed.setText("");
                    button.setText("已取消");
                    button.setEnabled(false);
                    break;
                case ERROR:
                case FATAL_ERROR:
                    String errorMessage = item.getErrorMessage();
                    if (errorMessage == null) {
                        errorMessage = "下载失败";
                    }
                    state.setText(errorMessage);
                    speed.setText("");
                    button.setText("开始");
                    button.setEnabled(true);
                    break;
            }
        }

        @Override
        public void onClick(View v) {
            final DownloadItem item = this.item;
            if (item == null) return;
            int state = item.getDownloadState();
            switch (v.getId()) {
                case R.id.button:
                    if (state == PAUSED || state == ERROR) {
                        onItemButtonClickListener.startItem(item);
                    } else if (state == RUNNING || state == PREPARING || state == WAITING) {
                        onItemButtonClickListener.pauseItem(item);
                    }
                    break;
                case R.id.delete_button:
                    if (state != CANCELED) {
                        onItemButtonClickListener.deleteItem(item);
                    }
                    break;
            }
        }
    }
}
