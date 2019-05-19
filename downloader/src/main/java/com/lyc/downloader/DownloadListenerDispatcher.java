package com.lyc.downloader;

import com.lyc.downloader.db.DownloadInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author liuyuchuan
 * @date 2019-05-10
 * @email kevinliu.sir@qq.com
 */
public class DownloadListenerDispatcher extends IDownloadCallback.Stub {
    private final List<DownloadListener> downloadListeners = new ArrayList<>();

    void registerDownloadListener(DownloadListener downloadListener) {
        synchronized (downloadListeners) {
            if (!downloadListeners.contains(downloadListener)) {
                downloadListeners.add(downloadListener);
            }
        }
    }

    void unregisterDownloadListener(DownloadListener downloadListener) {
        synchronized (downloadListeners) {
            downloadListeners.remove(downloadListener);
        }
    }

    @Override
    public void onPreparing(long id) {
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onPreparing(id);
            }
        });
    }

    @Override
    public void onProgressUpdate(long id, long total, long cur, double bps) {
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onProgressUpdate(id, total, cur, bps);
            }
        });
    }

    @Override
    public void onUpdateInfo(DownloadInfo downloadInfo) {
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onUpdateInfo(downloadInfo);
            }
        });
    }

    @Override
    public void onDownloadError(long id, String reason, boolean fatal) {
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onDownloadError(id, reason, fatal);
            }
        });
    }

    @Override
    public void onDownloadStart(DownloadInfo downloadInfo) {
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onDownloadStart(downloadInfo);
            }
        });
    }

    @Override
    public void onDownloadPausing(long id) {
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onDownloadPausing(id);
            }
        });
    }

    @Override
    public void onDownloadPaused(long id) {
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onDownloadPaused(id);
            }
        });
    }

    @Override
    public void onDownloadTaskWait(long id) {
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onDownloadTaskWait(id);
            }
        });
    }

    @Override
    public void onDownloadCanceled(long id) {
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onDownloadCanceled(id);
            }
        });
    }

    @Override
    public void onDownloadFinished(DownloadInfo downloadInfo) {
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onDownloadFinished(downloadInfo);
            }
        });
    }
}
