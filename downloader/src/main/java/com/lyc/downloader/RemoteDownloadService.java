package com.lyc.downloader;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import androidx.annotation.Nullable;
import com.lyc.downloader.db.DownloadInfo;
import com.lyc.downloader.utils.Logger;

import java.util.List;
import java.util.Map;

/**
 * @author liuyuchuan
 * @date 2019-05-10
 * @email kevinliu.sir@qq.com
 */
public class RemoteDownloadService extends Service implements DownloadListener {

    public static final String TAG = "RemoteDownloadService";
    private static DownloadManager downloadManager;
    private final RemoteCallbackList<IDownloadCallback> downloadCallbackList = new RemoteCallbackList<>();
    private final IDownloadService.Stub downloadService = new IDownloadService.Stub() {
        @Override
        public void registerDownloadCallback(IDownloadCallback callback) {
            downloadCallbackList.register(callback);
        }

        @Override
        public void removeDownloadCallback(IDownloadCallback callback) {
            downloadCallbackList.unregister(callback);
        }

        @Override
        public void submit(String url, String path, String filename, Map customerHeaders, ISubmitCallback callback) {
            Log.d("RemoteDownloadService", "submit " + url);
            //noinspection unchecked
            downloadManager.submit(url, path, filename, customerHeaders, new SubmitListener() {
                @Override
                public void submitSuccess(DownloadInfo downloadInfo) {
                    try {
                        Log.d("RemoteDownloadService", "submit " + url);
                        callback.submitSuccess(downloadInfo);
                    } catch (RemoteException e) {
                        try {
                            callback.submitFail(e.getMessage());
                        } catch (RemoteException e1) {
                            Logger.d("RemoteDownloadService", "cannot call submit fail", e);
                        }
                    }
                }

                @Override
                public void submitFail(Exception e) {
                    try {
                        callback.submitFail(e.getMessage());
                    } catch (RemoteException ex) {
                        Logger.e(TAG, "cannot report submit fail: ", ex);
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
        public void startOrResume(long id) {
            downloadManager.startOrResume(id);
        }

        @Override
        public void pause(long id) {
            downloadManager.pause(id);
        }

        @Override
        public void cancel(long id) {
            downloadManager.cancel(id);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        DownloadManager.init(getApplicationContext());
        downloadManager = DownloadManager.instance();
        downloadManager.setUserDownloadListener(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return downloadService.asBinder();
    }

    @Override
    public void onPreparing(long id) {
        int n = downloadCallbackList.beginBroadcast();
        for (int i = 0; i < n; i++) {
            IDownloadCallback broadcastItem = downloadCallbackList.getBroadcastItem(i);
            try {
                broadcastItem.onPreparing(id);
            } catch (RemoteException e) {
                Logger.e(TAG, "send prepare event for task#" + id + " failed.", e);
            }
        }
        downloadCallbackList.finishBroadcast();
    }

    @Override
    public void onProgressUpdate(long id, long total, long cur, double bps) {
        int n = downloadCallbackList.beginBroadcast();
        for (int i = 0; i < n; i++) {
            IDownloadCallback broadcastItem = downloadCallbackList.getBroadcastItem(i);
            try {
                broadcastItem.onProgressUpdate(id, total, cur, bps);
            } catch (RemoteException e) {
                Logger.e(TAG, "send progressUpdate event for task#" + id + " failed.", e);
            }
        }
        downloadCallbackList.finishBroadcast();
    }

    @Override
    public void onUpdateInfo(DownloadInfo downloadInfo) {
        int n = downloadCallbackList.beginBroadcast();
        for (int i = 0; i < n; i++) {
            IDownloadCallback broadcastItem = downloadCallbackList.getBroadcastItem(i);
            try {
                broadcastItem.onUpdateInfo(downloadInfo);
            } catch (RemoteException e) {
                Logger.e(TAG, "send updateInfo event for task#" + downloadInfo.getId() + " failed.", e);
            }
        }
        downloadCallbackList.finishBroadcast();
    }

    @Override
    public void onDownloadError(long id, String reason, boolean fatal) {
        int n = downloadCallbackList.beginBroadcast();
        for (int i = 0; i < n; i++) {
            IDownloadCallback broadcastItem = downloadCallbackList.getBroadcastItem(i);
            try {
                broadcastItem.onDownloadError(id, reason, fatal);
            } catch (RemoteException e) {
                Logger.e(TAG, "send downloadError event for task#" + id + " failed.", e);
            }
        }
        downloadCallbackList.finishBroadcast();
    }

    @Override
    public void onDownloadStart(DownloadInfo downloadInfo) {
        int n = downloadCallbackList.beginBroadcast();
        for (int i = 0; i < n; i++) {
            IDownloadCallback broadcastItem = downloadCallbackList.getBroadcastItem(i);
            try {
                broadcastItem.onDownloadStart(downloadInfo);
            } catch (RemoteException e) {
                Logger.e(TAG, "send downloadStart event for task#" + downloadInfo.getId() + " failed.", e);
            }
        }
        downloadCallbackList.finishBroadcast();
    }

    @Override
    public void onDownloadPausing(long id) {
        int n = downloadCallbackList.beginBroadcast();
        for (int i = 0; i < n; i++) {
            IDownloadCallback broadcastItem = downloadCallbackList.getBroadcastItem(i);
            try {
                broadcastItem.onDownloadPausing(id);
            } catch (RemoteException e) {
                Logger.e(TAG, "send downloadPausing event for task#" + id + " failed.", e);
            }
        }
        downloadCallbackList.finishBroadcast();
    }

    @Override
    public void onDownloadPaused(long id) {
        int n = downloadCallbackList.beginBroadcast();
        for (int i = 0; i < n; i++) {
            IDownloadCallback broadcastItem = downloadCallbackList.getBroadcastItem(i);
            try {
                broadcastItem.onDownloadPaused(id);
            } catch (RemoteException e) {
                Logger.e(TAG, "send downloadPaused event for task#" + id + " failed.", e);
            }
        }
        downloadCallbackList.finishBroadcast();
    }

    @Override
    public void onDownloadTaskWait(long id) {
        int n = downloadCallbackList.beginBroadcast();
        for (int i = 0; i < n; i++) {
            IDownloadCallback broadcastItem = downloadCallbackList.getBroadcastItem(i);
            try {
                broadcastItem.onDownloadTaskWait(id);
            } catch (RemoteException e) {
                Logger.e(TAG, "send downloadTaskWait event for task#" + id + " failed.", e);
            }
        }
        downloadCallbackList.finishBroadcast();
    }

    @Override
    public void onDownloadCanceled(long id) {
        int n = downloadCallbackList.beginBroadcast();
        for (int i = 0; i < n; i++) {
            IDownloadCallback broadcastItem = downloadCallbackList.getBroadcastItem(i);
            try {
                broadcastItem.onDownloadCanceled(id);
            } catch (RemoteException e) {
                Logger.e(TAG, "send downloadCancel event for task#" + id + " failed.", e);
            }
        }
        downloadCallbackList.finishBroadcast();
    }

    @Override
    public void onDownloadFinished(DownloadInfo downloadInfo) {
        int n = downloadCallbackList.beginBroadcast();
        for (int i = 0; i < n; i++) {
            IDownloadCallback broadcastItem = downloadCallbackList.getBroadcastItem(i);
            try {
                broadcastItem.onUpdateInfo(downloadInfo);
            } catch (RemoteException e) {
                Logger.e(TAG, "send downloadFinished event for task#" + downloadInfo.getId() + " failed.", e);
            }
        }
        downloadCallbackList.finishBroadcast();
    }
}
