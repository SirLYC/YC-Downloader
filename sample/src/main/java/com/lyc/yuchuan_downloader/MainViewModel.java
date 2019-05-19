package com.lyc.yuchuan_downloader;

import androidx.collection.LongSparseArray;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.lyc.downloader.DownloadListener;
import com.lyc.downloader.SubmitListener;
import com.lyc.downloader.YCDownloader;
import com.lyc.downloader.db.DownloadInfo;

import java.util.ArrayList;
import java.util.List;

import static com.lyc.downloader.DownloadTask.*;

/**
 * @author liuyuchuan
 * @date 2019-04-23
 * @email kevinliu.sir@qq.com
 */
public class MainViewModel extends ViewModel implements SubmitListener, DownloadListener {
    public final ObservableList<DownloadItem> itemList = new ObservableList<>(new ArrayList<>());
    final MutableLiveData<String> failLiveData = new MutableLiveData<>();
    private final LongSparseArray<DownloadItem> idToItem = new LongSparseArray<>();
    private String path;

    public String getPath() {
        return path;
    }

    public void setup(String path) {
        this.path = path;
        YCDownloader.registerDownloadListener(this);
        Async.cache.execute(() -> {
            List<DownloadInfo> downloadInfoList = YCDownloader.queryActiveDownloadInfoList();
            Async.main.execute(() -> {
                for (DownloadInfo downloadInfo : downloadInfoList) {
                    DownloadItem item = downloadInfoToItem(downloadInfo);
                    idToItem.put(downloadInfo.getId(), item);
                    itemList.add(item);
                }
            });
        });
    }

    void submit(String url) {
        YCDownloader.submit(url, path, null, null, this);
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
    public void onProgressUpdate(long id, long total, long cur, double bps) {
        DownloadItem item = idToItem.get(id);
        int index = itemList.indexOf(item);
        if (item != null && index != -1) {
            item.setTotalSize(total);
            item.setDownloadedSize(cur);
            item.setBps(bps);
            itemList.onChange(index, 1, null);
        }
    }

    @Override
    public void onUpdateInfo(DownloadInfo info) {
        DownloadItem item = idToItem.get(info.getId());
        int index = itemList.indexOf(item);
        if (item != null && index != -1) {
            DownloadItem newItem = downloadInfoToItem(info);
            idToItem.put(info.getId(), newItem);
            itemList.set(index, newItem);
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
    public void onDownloadStart(DownloadInfo info) {
        DownloadItem item = idToItem.get(info.getId());
        int index = itemList.indexOf(item);
        if (item != null && index != -1) {
            DownloadItem newItem = downloadInfoToItem(info);
            idToItem.put(info.getId(), newItem);
            itemList.set(index, newItem);
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
    public void onDownloadFinished(DownloadInfo downloadInfo) {
        DownloadItem item = idToItem.get(downloadInfo.getId());
        int index = itemList.indexOf(item);
        if (item != null && index != -1) {
            itemList.set(index, downloadInfoToItem(downloadInfo));
        }
    }

    @Override
    public void submitSuccess(DownloadInfo downloadInfo) {
        if (downloadInfo != null) {
            DownloadItem item = downloadInfoToItem(downloadInfo);
            itemList.add(0, item);
            idToItem.put(item.getId(), item);
        }
    }

    @Override
    public void submitFail(Exception e) {
        e.printStackTrace();
        failLiveData.postValue(e.getMessage());
        failLiveData.postValue(null);
    }

    private DownloadItem downloadInfoToItem(DownloadInfo info) {
        return new DownloadItem(
                info.getId(),
                info.getPath(),
                info.getFilename(),
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

    void pause(long id) {
        YCDownloader.pause(id);
    }

    void start(long id) {
        YCDownloader.startOrResume(id);
    }

    void cancel(long id) {
        YCDownloader.cancel(id);
    }

    @Override
    protected void onCleared() {
        YCDownloader.unregisterDownloadListener(this);
        super.onCleared();
    }
}
