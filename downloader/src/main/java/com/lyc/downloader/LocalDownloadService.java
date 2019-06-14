package com.lyc.downloader;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.annotation.Nullable;
import com.lyc.downloader.db.DownloadInfo;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Liu Yuchuan on 2019/5/19.
 */
public class LocalDownloadService extends Service implements DownloadListener, DownloadTasksChangeListener {
    private final Set<IDownloadCallback> downloadCallbacks = new LinkedHashSet<>();
    private final Set<IDownloadTasksChangeListener> downloadTasksChangeListeners = new LinkedHashSet<>();
    private LocalDownloadServiceBinder binder = new LocalDownloadServiceBinder();
    private DownloadManager downloadManager;

    public static IDownloadService asInterface(IBinder binder) {
        if (!(binder instanceof LocalDownloadServiceBinder)) {
            throw new IllegalArgumentException("only local download service binder can be transferred to download service!");
        }
        return (IDownloadService) binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        DownloadManager.init(this);
        downloadManager = DownloadManager.instance();
        downloadManager.setUserDownloadListener(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onPreparing(long id) {
        synchronized (downloadCallbacks) {
            for (IDownloadCallback downloadCallback : downloadCallbacks) {
                try {
                    downloadCallback.onPreparing(id);
                } catch (RemoteException e) {
                    // do nothing
                }
            }
        }
    }

    @Override
    public void onProgressUpdate(long id, long total, long cur, double bps) {
        synchronized (downloadCallbacks) {
            for (IDownloadCallback downloadCallback : downloadCallbacks) {
                try {
                    downloadCallback.onProgressUpdate(id, total, cur, bps);
                } catch (RemoteException e) {
                    // do nothing
                }
            }
        }

    }

    @Override
    public void onUpdateInfo(DownloadInfo downloadInfo) {
        synchronized (downloadCallbacks) {
            for (IDownloadCallback downloadCallback : downloadCallbacks) {
                try {
                    downloadCallback.onUpdateInfo(downloadInfo);
                } catch (RemoteException e) {
                    // do nothing
                }
            }
        }

    }

    @Override
    public void onDownloadError(long id, int code, boolean fatal) {
        synchronized (downloadCallbacks) {
            for (IDownloadCallback downloadCallback : downloadCallbacks) {
                try {
                    downloadCallback.onDownloadError(id, code, fatal);
                } catch (RemoteException e) {
                    // do nothing
                }
            }
        }

    }

    @Override
    public void onDownloadStart(DownloadInfo downloadInfo) {
        synchronized (downloadCallbacks) {
            for (IDownloadCallback downloadCallback : downloadCallbacks) {
                try {
                    downloadCallback.onDownloadStart(downloadInfo);
                } catch (RemoteException e) {
                    // do nothing
                }
            }
        }

    }

    @Override
    public void onDownloadPausing(long id) {
        synchronized (downloadCallbacks) {
            for (IDownloadCallback downloadCallback : downloadCallbacks) {
                try {
                    downloadCallback.onDownloadPausing(id);
                } catch (RemoteException e) {
                    // do nothing
                }
            }
        }

    }

    @Override
    public void onDownloadPaused(long id) {
        synchronized (downloadCallbacks) {
            for (IDownloadCallback downloadCallback : downloadCallbacks) {
                try {
                    downloadCallback.onDownloadPaused(id);
                } catch (RemoteException e) {
                    // do nothing
                }
            }
        }

    }

    @Override
    public void onDownloadTaskWait(long id) {
        synchronized (downloadCallbacks) {
            for (IDownloadCallback downloadCallback : downloadCallbacks) {
                try {
                    downloadCallback.onDownloadTaskWait(id);
                } catch (RemoteException e) {
                    // do nothing
                }
            }
        }

    }

    @Override
    public void onDownloadCanceled(long id) {
        synchronized (downloadCallbacks) {
            for (IDownloadCallback downloadCallback : downloadCallbacks) {
                try {
                    downloadCallback.onDownloadCanceled(id);
                } catch (RemoteException e) {
                    // do nothing
                }
            }
        }

    }

    @Override
    public void onDownloadFinished(DownloadInfo downloadInfo) {
        synchronized (downloadCallbacks) {
            for (IDownloadCallback downloadCallback : downloadCallbacks) {
                try {
                    downloadCallback.onDownloadFinished(downloadInfo);
                } catch (RemoteException e) {
                    // do nothing
                }
            }
        }
    }

    @Override
    public void onNewDownloadTaskArrive(DownloadInfo downloadInfo) {
        for (IDownloadTasksChangeListener downloadTasksChangeListener : downloadTasksChangeListeners) {
            try {
                downloadTasksChangeListener.onNewDownloadTaskArrive(downloadInfo);
            } catch (RemoteException e) {
                // do nothing
            }
        }
    }

    @Override
    public void onDownloadTaskRemove(long id) {
        for (IDownloadTasksChangeListener downloadTasksChangeListener : downloadTasksChangeListeners) {
            try {
                downloadTasksChangeListener.onDownloadTaskRemove(id);
            } catch (RemoteException e) {
                // do nothing
            }
        }
    }

    class LocalDownloadServiceBinder extends Binder implements IDownloadService {
        @Override
        public void registerDownloadCallback(IDownloadCallback callback) {
            synchronized (downloadCallbacks) {
                downloadCallbacks.add(callback);
            }
        }

        @Override
        public void removeDownloadCallback(IDownloadCallback callback) {
            synchronized (downloadCallbacks) {
                downloadCallbacks.remove(callback);
            }
        }

        @Override
        public void registerDownloadTasksChangeListener(IDownloadTasksChangeListener callback) {
            synchronized (downloadTasksChangeListeners) {
                downloadTasksChangeListeners.add(callback);
            }
        }

        @Override
        public void removeDownloadTasksChangeListener(IDownloadTasksChangeListener callback) {
            synchronized (downloadTasksChangeListeners) {
                downloadTasksChangeListeners.remove(callback);
            }
        }

        @Override
        public void submit(String url, String path, String filename, ISubmitCallback callback) {
            downloadManager.submit(url, path, filename, new SubmitListener() {
                @Override
                public void submitSuccess(DownloadInfo downloadInfo) {
                    try {
                        callback.submitSuccess(downloadInfo);
                    } catch (RemoteException e) {
                        // not gonna happen
                        // asInterface will not be called
                    }
                }

                @Override
                public void submitFail(Exception e) {
                    try {
                        callback.submitFail(e.getMessage());
                    } catch (RemoteException e1) {
                        // not gonna happen
                        // asInterface will not be called
                    }
                }
            });
        }

        @Override
        public DownloadInfo queryDownloadInfo(long id) {
            return downloadManager.queryDownloadInfo(id);
        }

        @Override
        public List<DownloadInfo> queryActiveDownloadInfoList() {
            return downloadManager.queryActiveDownloadInfoList();
        }

        @Override
        public List<DownloadInfo> queryDeletedDownloadInfoList() {
            return downloadManager.queryDeletedDownloadInfoList();
        }

        @Override
        public List<DownloadInfo> queryFinishedDownloadInfoList() {
            return downloadManager.queryFinishedDownloadInfoList();
        }

        @Override
        public void startAll() {
            downloadManager.startAll();
        }

        @Override
        public void pauseAll() {
            downloadManager.pauseAll();
        }

        @Override
        public void startOrResume(long id, boolean restart) {
            downloadManager.startOrResume(id, restart);
        }

        @Override
        public void pause(long id) {
            downloadManager.pause(id);
        }

        @Override
        public void cancel(long id) {
            downloadManager.cancel(id);
        }

        @Override
        public void delete(long id, boolean deleteFile) {
            downloadManager.delete(id, deleteFile);
        }

        @Override
        public int getMaxRunningTask() {
            return downloadManager.getMaxRunningTask();
        }

        @Override
        public void setMaxRunningTask(int count) {
            downloadManager.setMaxRunningTask(count);
        }

        @Override
        public boolean isAvoidFrameDrop() {
            return downloadManager.isAvoidFrameDrop();
        }

        @Override
        public void setAvoidFrameDrop(boolean avoidFrameDrop) {
            downloadManager.setAvoidFrameDrop(avoidFrameDrop);
        }

        @Override
        public long getSendMessageIntervalNanos() {
            return downloadManager.getSendMessageIntervalNanos();
        }

        @Override
        public void setSendMessageIntervalNanos(long time) {
            downloadManager.setSendMessageIntervalNanos(time);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}
