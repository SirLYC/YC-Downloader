package com.lyc.downloader;

import android.annotation.SuppressLint;
import com.lyc.downloader.db.DownloadInfo;

import java.util.*;

/**
 * @author liuyuchuan
 * @date 2019-05-10
 * @email kevinliu.sir@qq.com
 */
public class DownloadListenerDispatcher extends IDownloadCallback.Stub {
    // if id == null: register for all id
    @SuppressLint("UseSparseArrays")
    private final Map<Long, Set<DownloadListener>> downloadListenerMap = new HashMap<>();

    void registerDownloadListener(Long id, DownloadListener downloadListener) {
        synchronized (downloadListenerMap) {
            Set<DownloadListener> downloadListeners = downloadListenerMap.get(id);
            if (downloadListeners == null) {
                downloadListeners = new LinkedHashSet<>();
                downloadListenerMap.put(id, downloadListeners);
            }
            downloadListeners.add(downloadListener);
        }
    }

    void registerDownloadListener(Set<Long> ids, DownloadListener downloadListener) {
        if (ids.contains(null)) {
            // register for all
            registerDownloadListener((Long) null, downloadListener);
        } else {
            synchronized (downloadListenerMap) {
                for (Long id : ids) {
                    Set<DownloadListener> downloadListeners = downloadListenerMap.get(id);
                    if (downloadListeners == null) {
                        downloadListeners = new LinkedHashSet<>();
                        downloadListenerMap.put(id, downloadListeners);
                    }
                    downloadListeners.add(downloadListener);
                }
            }
        }
    }

    void unregisterDownloadListener(Set<Long> ids, DownloadListener downloadListener) {
        if (ids.contains(null)) {
            // unregister for all
            unregisterDownloadListener((Long) null, downloadListener);
        } else {
            synchronized (downloadListenerMap) {
                for (Long id : ids) {
                    Set<DownloadListener> downloadListeners = downloadListenerMap.get(id);
                    if (downloadListeners != null) {
                        downloadListeners.remove(downloadListener);
                    }
                }
            }
        }
    }

    void unregisterDownloadListener(Long id, DownloadListener downloadListener) {
        synchronized (downloadListenerMap) {
            if (id == null) {
                unregisterDownloadListener(downloadListener);
            } else {
                Set<DownloadListener> downloadListeners = downloadListenerMap.get(id);
                if (downloadListeners != null) {
                    downloadListeners.remove(downloadListener);
                }
            }
        }
    }

    void unregisterDownloadListener(DownloadListener downloadListener) {
        synchronized (downloadListenerMap) {
            for (Set<DownloadListener> value : downloadListenerMap.values()) {
                if (value != null) {
                    value.remove(downloadListener);
                }
            }
        }
    }

    @Override
    public void onPreparing(long id) {
        Collection<DownloadListener> downloadListeners = getDispatchListeners(id);
        if (downloadListeners.isEmpty()) {
            return;
        }
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onPreparing(id);
            }
        });
    }

    @Override
    public void onProgressUpdate(long id, long total, long cur, double bps) {
        Collection<DownloadListener> downloadListeners = getDispatchListeners(id);
        if (downloadListeners.isEmpty()) {
            return;
        }
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onProgressUpdate(id, total, cur, bps);
            }
        });
    }

    @Override
    public void onUpdateInfo(DownloadInfo downloadInfo) {
        Collection<DownloadListener> downloadListeners = getDispatchListeners(downloadInfo.getId());
        if (downloadListeners.isEmpty()) {
            return;
        }
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onUpdateInfo(downloadInfo);
            }
        });
    }

    @Override
    public void onDownloadError(long id, String reason, boolean fatal) {
        Collection<DownloadListener> downloadListeners = getDispatchListeners(id);
        if (downloadListeners.isEmpty()) {
            return;
        }
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onDownloadError(id, reason, fatal);
            }
        });
    }

    @Override
    public void onDownloadStart(DownloadInfo downloadInfo) {
        Collection<DownloadListener> downloadListeners = getDispatchListeners(downloadInfo.getId());
        if (downloadListeners.isEmpty()) {
            return;
        }
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onDownloadStart(downloadInfo);
            }
        });
    }

    @Override
    public void onDownloadPausing(long id) {
        Collection<DownloadListener> downloadListeners = getDispatchListeners(id);
        if (downloadListeners.isEmpty()) {
            return;
        }
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onDownloadPausing(id);
            }
        });
    }

    @Override
    public void onDownloadPaused(long id) {
        Collection<DownloadListener> downloadListeners = getDispatchListeners(id);
        if (downloadListeners.isEmpty()) {
            return;
        }
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onDownloadPaused(id);
            }
        });
    }

    @Override
    public void onDownloadTaskWait(long id) {
        Collection<DownloadListener> downloadListeners = getDispatchListeners(id);
        if (downloadListeners.isEmpty()) {
            return;
        }
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onDownloadTaskWait(id);
            }
        });
    }

    @Override
    public void onDownloadCanceled(long id) {
        Collection<DownloadListener> downloadListeners = getDispatchListeners(id);
        if (downloadListeners.isEmpty()) {
            return;
        }
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onDownloadCanceled(id);
            }
        });
    }

    @Override
    public void onDownloadFinished(DownloadInfo downloadInfo) {
        Collection<DownloadListener> downloadListeners = getDispatchListeners(downloadInfo.getId());
        if (downloadListeners.isEmpty()) {
            return;
        }
        DownloadExecutors.androidMain.execute(() -> {
            for (DownloadListener downloadListener : downloadListeners) {
                downloadListener.onDownloadFinished(downloadInfo);
            }
        });
    }

    private Collection<DownloadListener> getDispatchListeners(long id) {
        synchronized (downloadListenerMap) {
            LinkedList<DownloadListener> downloadListenerList = new LinkedList<>();
            Set<DownloadListener> allIdDownloadListeners = downloadListenerMap.get(null);
            if (allIdDownloadListeners != null) {
                downloadListenerList.addAll(allIdDownloadListeners);
            }
            Set<DownloadListener> downloadListeners = downloadListenerMap.get(id);
            if (downloadListeners != null) {
                downloadListenerList.addAll(downloadListeners);
            }
            return downloadListenerList;
        }
    }
}
