package com.lyc.yuchuan_downloader;

import androidx.annotation.MainThread;
import androidx.collection.LongSparseArray;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.lyc.downloader.DownloadListener;
import com.lyc.downloader.DownloadManager;
import com.lyc.downloader.db.DownloadInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.lyc.downloader.DownloadTask.*;

/**
 * @author liuyuchuan
 * @date 2019-04-23
 * @email kevinliu.sir@qq.com
 */
public class MainViewModel extends ViewModel implements DownloadManager.SubmitListener, DownloadManager.RecoverListener, DownloadListener {
    public final ObservableList<DownloadItem> itemList = new ObservableList<>(new ArrayList<>());
    final MutableLiveData<String> failLiveData = new MutableLiveData<>();
    private final LongSparseArray<DownloadItem> idToItem = new LongSparseArray<>();
    private String path;
    private DownloadManager downloadManager;

    public String getPath() {
        return path;
    }

    public void setup(String path) {
        this.path = path;
        downloadManager = DownloadManager.instance();
        downloadManager.setUserDownloadListener(this);
        downloadManager.addUserRecoverListener(this);
    }

    @MainThread
    public void submit(String url) {
        downloadManager.submit(url, new File(path, FilenameUtil.parseFileName(url)).getAbsolutePath(), null, this);
    }

    @Override
    public void onPreparing(long id) {
        DownloadItem item = idToItem.get(id);
        int index = itemList.indexOf(item);
        if (item != null && index != -1) {
            item.setDownloadState(PREPARING);
            itemList.onChange(index, 1, null);
        }
    }

    @Override
    public void onProgressUpdate(long id, long total, long cur) {
        DownloadItem item = idToItem.get(id);
        int index = itemList.indexOf(item);
        if (item != null && index != -1) {
            item.setTotalSize(total);
            item.setDownloadedSize(cur);
            itemList.onChange(index, 1, null);
        }
    }

    @Override
    public void onSpeedChange(long id, double bps) {
        DownloadItem item = idToItem.get(id);
        int index = itemList.indexOf(item);
        if (item != null && index != -1) {
            item.setBps(bps);
            itemList.onChange(index, 1, null);
        }
    }

    @Override
    public void onDownloadError(long id, String reason, boolean fatal) {
        DownloadItem item = idToItem.get(id);
        int index = itemList.indexOf(item);
        if (item != null && index != -1) {
            item.setDownloadState(ERROR);
            item.setErrorMessage(reason);
            itemList.onChange(index, 1, null);
        }
    }

    @Override
    public void onDownloadStart(long id) {
        DownloadItem item = idToItem.get(id);
        int index = itemList.indexOf(item);
        if (item != null && index != -1) {
            item.setDownloadState(RUNNING);
            itemList.onChange(index, 1, null);
        }
    }

    @Override
    public void onDownloadPausing(long id) {
        DownloadItem item = idToItem.get(id);
        int index = itemList.indexOf(item);
        if (item != null && index != -1) {
            item.setDownloadState(PAUSING);
            itemList.onChange(index, 1, null);
        }
    }

    @Override
    public void onDownloadPaused(long id) {
        DownloadItem item = idToItem.get(id);
        int index = itemList.indexOf(item);
        if (item != null && index != -1) {
            item.setDownloadState(PAUSED);
            itemList.onChange(index, 1, null);
        }
    }

    @Override
    public void onDownloadTaskWait(long id) {
        DownloadItem item = idToItem.get(id);
        int index = itemList.indexOf(item);
        if (item != null && index != -1) {
            item.setDownloadState(WAITING);
            itemList.onChange(index, 1, null);
        }
    }

    @Override
    public void onDownloadCanceled(long id) {
        DownloadItem item = idToItem.get(id);
        if (itemList.remove(item)) {
            idToItem.remove(item.getId(), item);
        }
    }

    @Override
    public void onDownloadFinished(long id) {
        DownloadItem item = idToItem.get(id);
        int index = itemList.indexOf(item);
        if (item != null && index != -1) {
            item.setDownloadState(FINISH);
            itemList.onChange(index, 1, null);
        }
    }

    @Override
    public void submitSuccess(long id) {
        DownloadInfo downloadInfo = downloadManager.queryDownloadInfo(id);
        if (downloadInfo != null) {
            DownloadItem item = downloadInfoToItem(downloadInfo);
            itemList.add(item);
            idToItem.put(item.getId(), item);
        }
    }

    @Override
    public void submitFail(Exception e) {
        failLiveData.setValue(e.getMessage());
        failLiveData.setValue(null);
    }

    @Override
    public void recoverReady(List<DownloadInfo> recoveredTasks) {
        itemList.clear();
        idToItem.clear();
        for (DownloadInfo recoveredTask : recoveredTasks) {
            DownloadItem item = downloadInfoToItem(recoveredTask);
            itemList.add(item);
            idToItem.put(recoveredTask.getId(), item);
        }
    }

    private DownloadItem downloadInfoToItem(DownloadInfo info) {
        return new DownloadItem(
                info.getId(),
                info.getPath(),
                info.getUrl(),
                0,
                info.getTotalSize(),
                info.getDownloadedSize(),
                info.getCreatedTime(),
                info.getFinishedTime(),
                info.getDownloadItemState(),
                info.getErrorMsg()
        );
    }

    public void pause(long id) {
        downloadManager.pause(id);
    }

    public void start(long id) {
        downloadManager.startOrResume(id);
    }

    public void cancel(long id) {
        downloadManager.cancel(id);
    }

    @Override
    protected void onCleared() {
        downloadManager.setUserDownloadListener(null);
        super.onCleared();
    }
}
