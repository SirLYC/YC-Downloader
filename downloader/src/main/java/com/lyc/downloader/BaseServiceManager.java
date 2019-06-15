package com.lyc.downloader;

import android.app.ActivityManager;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder.DeathRecipient;
import android.os.Looper;
import android.os.RemoteException;
import com.lyc.downloader.db.DownloadInfo;
import com.lyc.downloader.utils.Logger;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by Liu Yuchuan on 2019/5/19.
 */
public abstract class BaseServiceManager implements DownloadController, DownloadInfoProvider {
    private static final int MAX_SUPPORT_TASK_COUNT = Runtime.getRuntime().availableProcessors() * 4;
    private static final long WAITING_TIME = TimeUnit.SECONDS.toNanos(6);
    final Context appContext;
    private final DownloadListenerDispatcher downloadListenerDispatcher = new DownloadListenerDispatcher();
    private final Set<DownloadTasksChangeListener> downloadTasksChangeListeners = new LinkedHashSet<>();
    IDownloadService downloadService;
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    ServiceConnection downloadServiceConnection;
    private final IDownloadTasksChangeCallback downloadTasksChangeCallback = new IDownloadTasksChangeCallback.Stub() {
        @Override
        public void onNewDownloadTaskArrive(DownloadInfo downloadInfo) {
            if (!downloadTasksChangeListeners.isEmpty()) {
                DownloadExecutors.androidMain.execute(() -> {
                    for (DownloadTasksChangeListener downloadTasksChangeListener : downloadTasksChangeListeners) {
                        downloadTasksChangeListener.onNewDownloadTaskArrive(downloadInfo);
                    }
                });
            }
        }

        @Override
        public void onDownloadTaskRemove(long id) {
            if (!downloadTasksChangeListeners.isEmpty()) {
                DownloadExecutors.androidMain.execute(() -> {
                    for (DownloadTasksChangeListener downloadTasksChangeListener : downloadTasksChangeListeners) {
                        downloadTasksChangeListener.onDownloadTaskRemove(id);
                    }
                });
            }
        }
    };
    private int tryToConnectCount = 3;
    DeathRecipient deathRecipient = new DeathRecipient() {
        @Override
        public void binderDied() {
            Logger.e("BaseServiceManager", "Binder died...try to restart");
            if (downloadService != null) {
                downloadService.asBinder().unlinkToDeath(deathRecipient, 0);
            }
            downloadService = null;
            if (tryToConnectCount-- > 0) {
                connectToService();
            }
        }
    };

    BaseServiceManager(Context appContext) {
        DownloadExecutors.init();
        this.appContext = appContext.getApplicationContext();
        initServiceConnection();
        connectToService();
    }

    static String getProcessName(Context applicationContext) {
        int pid = android.os.Process.myPid();
        ActivityManager activityService = (ActivityManager) applicationContext
                .getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo runningAppProcess : activityService.getRunningAppProcesses()) {
            if (runningAppProcess.pid == pid) {
                return runningAppProcess.processName;
            }
        }
        throw new IllegalStateException("cannot find process of application!");
    }

    abstract void connectToService();

    abstract void initServiceConnection();

    private void waitingForConnection() {
        long start;
        long waited = 0;
        while (countDownLatch.getCount() > 0 && waited < WAITING_TIME) {
            start = System.nanoTime();
            try {
                countDownLatch.await(WAITING_TIME, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                waited += System.nanoTime() - start;
            }
        }
        if (countDownLatch.getCount() > 0) {
            throw new IllegalStateException("Waiting for service connection for too long! Is there is dead lock?");
        }
    }

    @Override
    public void startOrResume(long id, boolean restart) {
        DownloadExecutors.command.execute(() -> {
            try {
                waitingForConnection();
                downloadService.startOrResume(id, restart);
            } catch (RemoteException e) {
                Logger.e("DownloadController", "cannot startOrResume", e);
            }
        });
    }

    @Override
    public void pause(long id) {
        DownloadExecutors.command.execute(() -> {
            try {
                waitingForConnection();
                downloadService.pause(id);
            } catch (RemoteException e) {
                Logger.e("DownloadController", "cannot startOrResume", e);
            }
        });
    }

    @Override
    public void startAll() {
        DownloadExecutors.command.execute(() -> {
            try {
                waitingForConnection();
                downloadService.startAll();
            } catch (RemoteException e) {
                Logger.e("DownloadController", "cannot startAll", e);
            }
        });
    }

    @Override
    public void pauseAll() {
        DownloadExecutors.command.execute(() -> {
            try {
                waitingForConnection();
                downloadService.pauseAll();
            } catch (RemoteException e) {
                Logger.e("DownloadController", "cannot pauseAll", e);
            }

        });
    }

    @Override
    public void delete(long id, boolean deleteFile) {
        DownloadExecutors.command.execute(() -> {
            waitingForConnection();
            try {
                downloadService.delete(id, deleteFile);
            } catch (RemoteException e) {
                Logger.e("DownloadController", "cannot delete", e);
            }
        });
    }

    @Override
    public void cancel(long id) {
        DownloadExecutors.command.execute(() -> {
            waitingForConnection();
            try {
                downloadService.cancel(id);
            } catch (RemoteException e) {
                Logger.e("DownloadController", "cannot cancel", e);
            }
        });
    }

    @Override
    public void submit(String url, String path, String filename, ISubmitCallback callback) {
        DownloadExecutors.command.execute(() -> {
            waitingForConnection();
            try {
                downloadService.submit(url, path, filename, callback);
            } catch (RemoteException e) {
                try {
                    callback.submitFail(e.getMessage());
                } catch (RemoteException e1) {
                    Logger.e("BaseServiceManager", "submitFail", e1);
                }
            }
        });
    }

    int getMaxSupportRunningTask() {
        return MAX_SUPPORT_TASK_COUNT;
    }

    @Override
    public int getMaxRunningTask() {
        if (downloadService == null) {
            return 0;
        }
        try {
            return downloadService.getMaxRunningTask();
        } catch (RemoteException e) {
            Logger.e(getClass().getSimpleName(), "cannot get maxRunning task", e);
        }

        return 0;
    }

    @Override
    public void setMaxRunningTask(int count) {
        if (count < 0 || count > MAX_SUPPORT_TASK_COUNT) return;
        DownloadExecutors.command.execute(() -> {
            waitingForConnection();
            try {
                downloadService.setMaxRunningTask(count);
            } catch (RemoteException e) {
                Logger.e(getClass().getSimpleName(), "cannot set max task count", e);
            }
        });
    }

    @Override
    public boolean isAvoidFrameDrop() {
        if (downloadService == null) {
            return false;
        }
        try {
            return downloadService.isAvoidFrameDrop();
        } catch (RemoteException e) {
            Logger.e(getClass().getSimpleName(), "cannot get avoid frame drop", e);
        }

        return false;
    }

    @Override
    public void setAvoidFrameDrop(boolean avoidFrameDrop) {
        DownloadExecutors.command.execute(() -> {
            waitingForConnection();
            try {
                downloadService.setAvoidFrameDrop(avoidFrameDrop);
            } catch (RemoteException e) {
                Logger.e(getClass().getSimpleName(), "cannot set avoid frame drop", e);
            }
        });
    }

    @Override
    public long getSendMessageIntervalNanos() {
        if (downloadService == null) {
            return 0;
        }
        try {
            return downloadService.getSendMessageIntervalNanos();
        } catch (RemoteException e) {
            Logger.e(getClass().getSimpleName(), "cannot get send message interval nanos", e);
        }

        return 0;
    }

    @Override
    public void setSendMessageIntervalNanos(long time) {
        DownloadExecutors.command.execute(() -> {
            waitingForConnection();
            try {
                downloadService.setSendMessageIntervalNanos(time);
            } catch (RemoteException e) {
                Logger.e(getClass().getSimpleName(), "cannot set send message interval nanos", e);
            }
        });
    }

    @Override
    public DownloadInfo queryDownloadInfo(long id) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            throw new IllegalThreadStateException("this method cannot call in main thread");
        }
        waitingForConnection();
        try {
            return downloadService.queryDownloadInfo(id);
        } catch (RemoteException e) {
            Logger.e(getClass().getSimpleName(), "cannot queryDownloadInfo", e);
        }
        return null;
    }

    @Override
    public List<DownloadInfo> queryActiveDownloadInfoList() {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            throw new IllegalThreadStateException("this method cannot call in main thread");
        }
        waitingForConnection();
        try {
            return downloadService.queryActiveDownloadInfoList();
        } catch (RemoteException e) {
            Logger.e(getClass().getSimpleName(), "cannot queryActiveDownloadInfoList", e);
        }
        return null;
    }

    @Override
    public List<DownloadInfo> queryDeletedDownloadInfoList() {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            throw new IllegalThreadStateException("this method cannot call in main thread");
        }
        waitingForConnection();
        try {
            return downloadService.queryDeletedDownloadInfoList();
        } catch (RemoteException e) {
            Logger.e(getClass().getSimpleName(), "cannot queryDeletedDownloadInfoList", e);
        }

        return null;
    }

    @Override
    public List<DownloadInfo> queryFinishedDownloadInfoList() {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            throw new IllegalThreadStateException("this method cannot call in main thread");
        }
        waitingForConnection();
        try {
            return downloadService.queryFinishedDownloadInfoList();
        } catch (RemoteException e) {
            Logger.e(getClass().getSimpleName(), "cannot queryFinishedDownloadInfoList", e);
        }

        return null;
    }

    void registerDownloadListener(Long id, DownloadListener downloadListener) {
        if (downloadListener != null) {
            DownloadExecutors.command.execute(() -> {
                waitingForConnection();
                downloadListenerDispatcher.registerDownloadListener(id, downloadListener);
            });
        }
    }

    void registerDownloadListener(Set<Long> ids, DownloadListener downloadListener) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        if (downloadListener != null) {
            DownloadExecutors.command.execute(() -> {
                waitingForConnection();
                downloadListenerDispatcher.registerDownloadListener(ids, downloadListener);
            });
        }
    }

    void unregisterDownloadListener(Long id, DownloadListener downloadListener) {
        if (downloadListener != null) {
            DownloadExecutors.command.execute(() -> {
                waitingForConnection();
                downloadListenerDispatcher.unregisterDownloadListener(id, downloadListener);
            });
        }
    }

    void unregisterDownloadListener(Set<Long> ids, DownloadListener downloadListener) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        if (downloadListener != null) {
            DownloadExecutors.command.execute(() -> {
                waitingForConnection();
                downloadListenerDispatcher.unregisterDownloadListener(ids, downloadListener);
            });
        }
    }

    void unregisterDownloadListener(DownloadListener downloadListener) {
        if (downloadListener != null) {
            DownloadExecutors.command.execute(() -> {
                waitingForConnection();
                downloadListenerDispatcher.unregisterDownloadListener(downloadListener);
            });
        }
    }

    void registerDownloadTasksChangeListener(DownloadTasksChangeListener downloadTasksChangeListener) {
        downloadTasksChangeListeners.add(downloadTasksChangeListener);
    }

    void removeDownloadTasksChangeListener(DownloadTasksChangeListener downloadTasksChangeListener) {
        downloadTasksChangeListeners.remove(downloadTasksChangeListener);
    }

    void postOnConnection(Runnable runnable) {
        if (runnable == null) return;
        if (countDownLatch.getCount() == 0) {
            if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
                runnable.run();
            } else {
                DownloadExecutors.androidMain.execute(runnable);
            }
        } else {
            DownloadExecutors.command.execute(() -> {
                waitingForConnection();
                DownloadExecutors.androidMain.execute(runnable);
            });
        }
    }

    void registerLocalListeners() {
        try {
            downloadService.registerDownloadCallback(downloadListenerDispatcher);
        } catch (RemoteException e) {
            Logger.e("BaseServiceManager", "registerDownloadCallback", e);
        }

        try {
            downloadService.registerDownloadTasksChangeCallback(downloadTasksChangeCallback);
        } catch (RemoteException e) {
            Logger.e("BaseServiceManager", "registerDownloadTasksChangeListener", e);
        }
    }

    abstract boolean isInServerProcess();
}
