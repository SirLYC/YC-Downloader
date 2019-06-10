package com.lyc.downloader;

import android.app.ActivityManager;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import com.lyc.downloader.ISubmitCallback.Stub;
import com.lyc.downloader.db.DownloadInfo;
import com.lyc.downloader.utils.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Liu Yuchuan on 2019/5/19.
 */
public abstract class BaseServiceManager implements DownloadController, DownloadInfoProvider {
    private static final int MAX_SUPPORT_TASK_COUNT = Runtime.getRuntime().availableProcessors() * 2 + 1;
    private static final long WAITING_TIME = TimeUnit.SECONDS.toNanos(6);
    final Context appContext;
    final Lock connectLock = new ReentrantLock();
    final DownloadListenerDispatcher downloadListenerDispatcher = new DownloadListenerDispatcher();
    IDownloadService downloadService;
    CountDownLatch countDownLatch;
    ServiceConnection downloadServiceConnection;
    DeathRecipient deathRecipient = new DeathRecipient() {
        @Override
        public void binderDied() {
            try {
                connectLock.lock();
                if (downloadService != null) {
                    downloadService.asBinder().unlinkToDeath(deathRecipient, 0);
                }
                downloadService = null;
            } finally {
                connectLock.unlock();
            }
            connectToService();
        }
    };

    BaseServiceManager(Context appContext) {
        this.appContext = appContext.getApplicationContext();
        initServiceConnection();
        DownloadExecutors.io.execute(this::connectToService);
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
        CountDownLatch countDownLatch = this.countDownLatch;
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
        DownloadExecutors.io.execute(() -> {
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
        DownloadExecutors.io.execute(() -> {
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
        DownloadExecutors.io.execute(() -> {
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
        DownloadExecutors.io.execute(() -> {
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
        DownloadExecutors.io.execute(() -> {
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
        DownloadExecutors.io.execute(() -> {
            waitingForConnection();
            try {
                downloadService.cancel(id);
            } catch (RemoteException e) {
                Logger.e("DownloadController", "cannot cancel", e);
            }
        });
    }

    @Override
    public void submit(String url, String path, String filename, Map<String, String> customerHeaders, SubmitListener listener) {
        DownloadExecutors.io.execute(() -> {
            waitingForConnection();
            try {
                downloadService.submit(url, path, filename, customerHeaders, new Stub() {
                    @Override
                    public void submitSuccess(DownloadInfo downloadInfo) {
                        DownloadExecutors.androidMain.execute(() -> listener.submitSuccess(downloadInfo));
                    }

                    @Override
                    public void submitFail(String reason) {
                        DownloadExecutors.androidMain.execute(() -> listener.submitFail(new Exception(reason)));
                    }
                });
            } catch (RemoteException e) {
                DownloadExecutors.androidMain.execute(() -> listener.submitFail(e));
            }
        });
    }

    @Override
    public int getMaxSupportTaskCount() {
        return MAX_SUPPORT_TASK_COUNT;
    }

    @Override
    public int getMaxRunningTask() {
        try {
            return downloadService.getMaxRunningTask();
        } catch (RemoteException e) {
            Logger.e(getClass().getSimpleName(), "cannot get maxRunning task", e);
        }

        return 0;
    }

    @Override
    public void setMaxRunningTask(int count) {
        if (count <= 0) return;
        DownloadExecutors.io.execute(() -> {
            waitingForConnection();
            try {
                if (count > MAX_SUPPORT_TASK_COUNT) {
                    downloadService.setMaxRunningTask(MAX_SUPPORT_TASK_COUNT);
                } else {
                    downloadService.setMaxRunningTask(count);
                }
            } catch (RemoteException e) {
                Logger.e(getClass().getSimpleName(), "cannot set max task count", e);
            }
        });
    }

    @Override
    public boolean isAvoidFrameDrop() {
        try {
            return downloadService.isAvoidFrameDrop();
        } catch (RemoteException e) {
            Logger.e(getClass().getSimpleName(), "cannot get avoid frame drop", e);
        }

        return false;
    }

    @Override
    public void setAvoidFrameDrop(boolean avoidFrameDrop) {
        try {
            downloadService.setAvoidFrameDrop(avoidFrameDrop);
        } catch (RemoteException e) {
            Logger.e(getClass().getSimpleName(), "cannot set avoid frame drop", e);
        }
    }

    @Override
    public long getSendMessageIntervalNanos() {
        try {
            return downloadService.getSendMessageIntervalNanos();
        } catch (RemoteException e) {
            Logger.e(getClass().getSimpleName(), "cannot get send message interval nanos", e);
        }

        return 0;
    }

    @Override
    public void setSendMessageIntervalNanos(long time) {
        try {
            downloadService.setSendMessageIntervalNanos(time);
        } catch (RemoteException e) {
            Logger.e(getClass().getSimpleName(), "cannot set send message interval nanos", e);
        }
    }

    @Override
    public DownloadInfo queryDownloadInfo(long id) {
        waitingForConnection();
        try {
            return downloadService.queryDownloadInfo(id);
        } catch (RemoteException e) {
            throw new IllegalStateException("should not happen");
        }
    }

    @Override
    public List<DownloadInfo> queryActiveDownloadInfoList() {
        waitingForConnection();
        try {
            return downloadService.queryActiveDownloadInfoList();
        } catch (RemoteException e) {
            throw new IllegalStateException("should not happen");
        }
    }

    @Override
    public List<DownloadInfo> queryDeletedDownloadInfoList() {
        waitingForConnection();
        try {
            return downloadService.queryDeletedDownloadInfoList();
        } catch (RemoteException e) {
            throw new IllegalStateException("should not happen");
        }
    }

    @Override
    public List<DownloadInfo> queryFinishedDownloadInfoList() {
        waitingForConnection();
        try {
            return downloadService.queryFinishedDownloadInfoList();
        } catch (RemoteException e) {
            throw new IllegalStateException("should not happen");
        }
    }

    void registerDownloadListener(Long id, DownloadListener downloadListener) {
        downloadListenerDispatcher.registerDownloadListener(id, downloadListener);
    }

    void registerDownloadListener(Set<Long> ids, DownloadListener downloadListener) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        downloadListenerDispatcher.registerDownloadListener(ids, downloadListener);
    }

    void unregisterDownloadListener(Long id, DownloadListener downloadListener) {
        downloadListenerDispatcher.unregisterDownloadListener(id, downloadListener);
    }

    void unregisterDownloadListener(Set<Long> ids, DownloadListener downloadListener) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        downloadListenerDispatcher.unregisterDownloadListener(ids, downloadListener);
    }

    void unregisterDownloadListener(DownloadListener downloadListener) {
        downloadListenerDispatcher.unregisterDownloadListener(downloadListener);
    }
}
