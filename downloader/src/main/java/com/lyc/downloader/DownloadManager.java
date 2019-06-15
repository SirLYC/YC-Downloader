package com.lyc.downloader;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.RemoteException;
import androidx.annotation.WorkerThread;
import androidx.collection.LongSparseArray;
import com.lyc.downloader.db.DaoMaster;
import com.lyc.downloader.db.DaoMaster.DevOpenHelper;
import com.lyc.downloader.db.DaoSession;
import com.lyc.downloader.db.DownloadInfo;
import com.lyc.downloader.db.DownloadInfoDao;
import com.lyc.downloader.utils.Logger;
import com.lyc.downloader.utils.UniqueDequeue;
import okhttp3.OkHttpClient;
import okhttp3.OkHttpClient.Builder;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.lyc.downloader.DownloadTask.*;

/**
 * @author liuyuchuan
 * @date 2019/4/1
 * @email kevinliu.sir@qq.com
 */
class DownloadManager implements DownloadListener, DownloadController, DownloadInfoProvider {

    static final String DB_NAME = "yuchuan_downloader_db";
    @SuppressLint("StaticFieldLeak")
    private volatile static DownloadManager instance;
    private static final String TAG = "DownloadManager";
    // for http
    private final OkHttpClient client;
    final DaoSession daoSession;
    private final LongSparseArray<DownloadTask> taskTable = new LongSparseArray<>();
    private final LongSparseArray<DownloadInfo> infoTable = new LongSparseArray<>();
    private final LongSparseArray<Long> lastSendMessageTime = new LongSparseArray<>();
    private final Deque<Long> runningTasksId = new UniqueDequeue<>();
    private final Deque<Long> waitingTasksId = new UniqueDequeue<>();
    private final Deque<Long> errorTasksId = new UniqueDequeue<>();
    private final Deque<Long> pausingTasksId = new UniqueDequeue<>();
    private final Lock downloadCallbackSetLock = new ReentrantLock();
    private final Lock downloadTasksChangeCallbackSetLock = new ReentrantLock();
    private IDownloadCallback downloadCallback;
    private IDownloadTasksChangeCallback downloadTasksChangeCallback;
    private int maxRunningTask = 4;
    private volatile boolean avoidFrameDrop = true;

    private CountDownLatch recoverCountDownLatch = new CountDownLatch(1);
    // ns
    private volatile long sendMessageIntervalNanos = TimeUnit.MILLISECONDS.toNanos(333);

    private DownloadManager(OkHttpClient client, Context appContext) {
        this.client = client;
        SQLiteDatabase db = new DevOpenHelper(appContext, DB_NAME).getWritableDatabase();
        daoSession = new DaoMaster(db).newSession();
        recoverDownloadTasks();
    }


    static void init(Context context) {
        if (instance == null) {
            synchronized (DownloadManager.class) {
                if (instance == null) {
                    if (context == null) {
                        throw new NullPointerException("Context cannot be null!");
                    }
                    HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
                    httpLoggingInterceptor.setLevel(Level.HEADERS);
                    OkHttpClient client = new Builder().addInterceptor(httpLoggingInterceptor).build();
                    instance = new DownloadManager(client, context);
                }
            }
        }
    }

    static DownloadManager instance() {
        if (instance == null) {
            throw new IllegalStateException("init download manager by DownloadManager.init(Context context) first!");
        }
        return instance;
    }

    private void pauseAllInner() {
        for (Long aLong : waitingTasksId) {
            DownloadTask downloadTask = taskTable.get(aLong);
            if (downloadTask != null) {
                downloadTask.pause();
            }
        }

        for (Long aLong : runningTasksId) {
            DownloadTask downloadTask = taskTable.get(aLong);
            if (downloadTask != null) {
                downloadTask.pause();
            }
        }
    }

    private void startAllInner() {
        int size = taskTable.size();
        List<DownloadInfo> infoList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            DownloadInfo downloadInfo = infoTable.valueAt(i);
            if (downloadInfo != null) {
                infoList.add(downloadInfo);
            }
        }
        Collections.sort(infoList, (o1, o2) ->
                o2.getCreatedTime().compareTo(o1.getCreatedTime()));
        for (DownloadInfo downloadInfo : infoList) {
            DownloadTask downloadTask = taskTable.get(downloadInfo.getId());
            if (downloadTask != null) {
                enqueueTask(downloadInfo.getId(), false, false);
            }
        }
        schedule();
    }

    private void recoverDownloadTasks() {
        DownloadExecutors.io.execute(() -> {
            try {
                List<DownloadInfo> downloadInfoList = queryActiveDownloadInfoListInner();
                List<DownloadInfo> finishedDownloadInfoList = queryFinishedDownloadInfoList();
                Collections.reverse(finishedDownloadInfoList);
                // make sure to add last
                for (DownloadInfo downloadInfo : downloadInfoList) {
                    long id = downloadInfo.getId();
                    DownloadTask downloadTask = new DownloadTask(downloadInfo, client);
                    taskTable.put(id, downloadTask);
                    infoTable.put(id, downloadInfo);
                    switch (downloadInfo.getDownloadItemState()) {
                        case PENDING:
                        case CONNECTING:
                        case RUNNING:
                        case WAITING:
                            enqueueTask(id, false, false);
                            break;
                        case PAUSING:
                        case PAUSED:
                            pausingTasksId.add(id);
                            break;
                        case ERROR:
                        case FATAL_ERROR:
                            errorTasksId.add(id);
                            break;
                    }
                    downloadInfo.setDownloadItemState(downloadTask.getState());
                }
                daoSession.getDownloadInfoDao().saveInTx(downloadInfoList);
                for (DownloadInfo downloadInfo : finishedDownloadInfoList) {
                    taskTable.put(downloadInfo.getId(), new DownloadTask(downloadInfo, client));
                    infoTable.put(downloadInfo.getId(), downloadInfo);
                }
            } finally {
                schedule();
                recoverCountDownLatch.countDown();
            }
        });
    }

    private void schedule() {

        while (runningTasksId.size() > maxRunningTask) {
            Long id = runningTasksId.pollFirst();
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) {
                taskTable.remove(id);
                infoTable.remove(id);
                continue;
            }
            enqueueTask(id, false, false);
        }

        while (runningTasksId.size() < maxRunningTask && !waitingTasksId.isEmpty()) {
            Long id = waitingTasksId.pollFirst();
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) {
                taskTable.remove(id);
                infoTable.remove(id);
                continue;
            }
            if (downloadTask.start()) {
                runningTasksId.offer(id);
            } else if (downloadTask.getState() == WAITING) {
                enqueueTask(id, false, false);
            }
        }
    }

    @Override
    public void onDownloadConnecting(long id) {
        IDownloadCallback downloadCallback = this.downloadCallback;
        if (downloadCallback != null) {
            try {
                downloadCallback.onDownloadPreparing(id);
            } catch (RemoteException e) {
                Logger.e(TAG, "onDownloadConnecting", e);
            }
        }
    }

    @Override
    public void onDownloadProgressUpdate(long id, long total, long cur, double bps) {
        IDownloadCallback downloadCallback = this.downloadCallback;
        DownloadExecutors.message.execute(() -> {
            boolean shouldSend = false;
            long currentTime = System.nanoTime();
            if (avoidFrameDrop) {
                Long lastTime = lastSendMessageTime.get(id);
                if (lastTime == null || lastTime + sendMessageIntervalNanos <= currentTime) {
                    shouldSend = true;
                }
            } else {
                shouldSend = true;
            }

            if (shouldSend) {
                lastSendMessageTime.put(id, currentTime);
                if (downloadCallback != null) {
                    try {
                        downloadCallback.onDownloadProgressUpdate(id, total, cur, bps);
                    } catch (RemoteException e) {
                        Logger.e(TAG, "onDownloadProgressUpdate", e);
                    }
                }
            }
        });
    }

    @Override
    public void onDownloadUpdateInfo(DownloadInfo downloadInfo) {
        IDownloadCallback downloadCallback = this.downloadCallback;
        if (downloadCallback != null) {
            try {
                downloadCallback.onDownloadUpdateInfo(downloadInfo);
            } catch (RemoteException e) {
                Logger.e(TAG, "onDownloadUpdateInfo", e);
            }
        }
    }

    @Override
    public void onDownloadError(long id, int code, boolean fatal) {
        IDownloadCallback downloadCallback = this.downloadCallback;
        DownloadExecutors.message.execute(() -> {
            lastSendMessageTime.remove(id);
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            if (runningTasksId.remove(id) | pausingTasksId.remove(id) | waitingTasksId.remove(id)) {
                errorTasksId.add(id);
                if (downloadCallback != null) {
                    try {
                        downloadCallback.onDownloadError(id, code, fatal);
                    } catch (RemoteException e) {
                        Logger.e(TAG, "onDownloadError", e);
                    }
                }
                schedule();
            }
        });
    }

    @Override
    public void onDownloadStart(DownloadInfo downloadInfo) {
        IDownloadCallback downloadCallback = this.downloadCallback;
        DownloadExecutors.message.execute(() -> {
            if (!runningTasksId.contains(downloadInfo.getId())) {
                runningTasksId.offer(downloadInfo.getId());
            }
            if (downloadCallback != null) {
                try {
                    downloadCallback.onDownloadStart(downloadInfo);
                } catch (RemoteException e) {
                    Logger.e(TAG, "onDownloadStart", e);
                }
            }
        });
    }

    @Override
    public void onDownloadPausing(long id) {
        IDownloadCallback downloadCallback = this.downloadCallback;
        if (downloadCallback != null) {
            try {
                downloadCallback.onDownloadPausing(id);
            } catch (RemoteException e) {
                Logger.e(TAG, "onDownloadPausing", e);
            }
        }
    }

    @Override
    public void onDownloadPaused(long id) {
        IDownloadCallback downloadCallback = this.downloadCallback;
        DownloadExecutors.message.execute(() -> {
            DownloadTask downloadTask = taskTable.get(id);
            lastSendMessageTime.remove(id);
            if (downloadTask == null) return;
            if (runningTasksId.remove(id) | waitingTasksId.remove(id)) {
                pausingTasksId.add(id);
                if (downloadCallback != null) {
                    try {
                        downloadCallback.onDownloadPaused(id);
                    } catch (RemoteException e) {
                        Logger.e(TAG, "onDownloadPaused", e);
                    }
                }
                schedule();
            }
        });

    }

    @Override
    public void onDownloadCanceled(long id) {
        IDownloadCallback downloadCallback = this.downloadCallback;
        DownloadExecutors.message.execute(() -> {
            lastSendMessageTime.remove(id);
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            if (runningTasksId.remove(id) | pausingTasksId.remove(id) |
                    errorTasksId.remove(id) | waitingTasksId.remove(id)) {
                taskTable.remove(id);
                Logger.d("DownloadManager", "remove task#" + id + " running tasks = " + runningTasksId.size());
                if (downloadCallback != null) {
                    try {
                        downloadCallback.onDownloadCanceled(id);
                    } catch (RemoteException e) {
                        Logger.e(TAG, "onDownloadCanceled", e);
                    }
                }
                schedule();
            }
        });
    }

    @Override
    public void onDownloadTaskWait(long id) {
        IDownloadCallback downloadCallback = this.downloadCallback;
        try {
            if (downloadCallback != null) {
                downloadCallback.onDownloadWaiting(id);
            }
        } catch (RemoteException e) {
            Logger.e(TAG, "onDownloadWaiting", e);
        }
    }

    @Override
    public void onDownloadFinished(DownloadInfo downloadInfo) {
        IDownloadCallback downloadCallback = this.downloadCallback;
        DownloadExecutors.message.execute(() -> {
            long id = downloadInfo.getId();
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            if (runningTasksId.remove(id) | waitingTasksId.remove(id) | pausingTasksId.remove(id) | errorTasksId.remove(id)) {
                try {
                    if (downloadCallback != null) {
                        downloadCallback.onDownloadFinished(downloadInfo);
                    }
                } catch (RemoteException e) {
                    Logger.e(TAG, "onDownloadFinished", e);
                }
                schedule();
            }
        });
    }

    @WorkerThread
    private void submitInner(String url, String path, String filename, ISubmitCallback listener) {
        DownloadInfo downloadInfo = new DownloadInfo(null, url, path,
                filename, true, WAITING,
                0, 0, null, new Date(), null, null);
        try {
            Long insertId = PersistUtil.persistDownloadInfo(daoSession, downloadInfo, null);
            DownloadExecutors.message.execute(() -> {
                if (insertId != null) {
                    infoTable.put(insertId, downloadInfo);
                    DownloadTask downloadTask = new DownloadTask(downloadInfo, client);
                    taskTable.put(insertId, downloadTask);
                    waitingTasksId.add(insertId);
                    try {
                        listener.submitSuccess(downloadInfo);
                    } catch (RemoteException e) {
                        Logger.e(TAG, "submitSuccess", e);
                    }
                    IDownloadTasksChangeCallback downloadTasksChangeCallback = this.downloadTasksChangeCallback;
                    if (downloadTasksChangeCallback != null) {
                        try {
                            downloadTasksChangeCallback.onNewDownloadTaskArrive(downloadInfo);
                        } catch (RemoteException e) {
                            Logger.e(TAG, "onNewDownloadTaskArrive", e);
                        }
                    }
                    schedule();
                } else {
                    try {
                        listener.submitFail("创建任务失败");
                    } catch (RemoteException e) {
                        Logger.e(TAG, "submitFail", e);
                    }
                }
            });
        } catch (Exception e) {
            try {
                listener.submitFail(e.getLocalizedMessage());
            } catch (RemoteException e1) {
                Logger.e(TAG, "submitFail", e1);
            }
        }
    }

    private void enqueueTask(long id, boolean restart, boolean scheduleAfterEnqueue) {
        DownloadTask downloadTask = taskTable.get(id);
        if (downloadTask == null) {
            return;
        }

        downloadTask.toWait(restart);
        if (!waitingTasksId.contains(id)) {
            waitingTasksId.offer(id);
            if (scheduleAfterEnqueue) {
                schedule();
            }
            IDownloadCallback downloadCallback = this.downloadCallback;
            if (downloadCallback != null) {
                try {
                    downloadCallback.onDownloadWaiting(id);
                } catch (RemoteException e) {
                    Logger.e(TAG, "onDownloadWaiting", e);
                }
            }
        }
    }

    /************************** api **************************/

    @Override
    public void pauseAll() {
        DownloadExecutors.message.execute(this::pauseAllInner);
    }

    @Override
    public void startAll() {
        DownloadExecutors.message.execute(this::startAllInner);
    }

    // include re-download
    @Override
    public void startOrResume(long id, boolean restart) {
        DownloadExecutors.message.execute(() -> {
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null && restart) {
                DownloadExecutors.io.execute(() -> {
                    DownloadInfo downloadInfo = daoSession.getDownloadInfoDao().load(id);
                    if (downloadInfo != null) {
                        DownloadExecutors.message.execute(() -> {
                            DownloadTask newDownloadTask = new DownloadTask(downloadInfo, client);
                            taskTable.put(id, newDownloadTask);
                            infoTable.put(id, downloadInfo);
                            enqueueTask(id, true, true);
                        });
                    }
                });
            } else if (downloadTask != null && ((pausingTasksId.remove(id) | errorTasksId.remove(id)) ||
                    downloadTask.getState() == FINISH)) {
                enqueueTask(id, restart, true);
            }
        });
    }

    @Override
    public void pause(long id) {
        DownloadExecutors.message.execute(() -> {
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            downloadTask.pause();
        });
    }

    // also delete
    @Override
    public void cancel(long id) {
        DownloadExecutors.message.execute(() -> {
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            downloadTask.cancel();
            IDownloadTasksChangeCallback downloadTasksChangeCallback = this.downloadTasksChangeCallback;
            if (downloadTasksChangeCallback != null) {
                try {
                    downloadTasksChangeCallback.onDownloadTaskRemove(id);
                } catch (RemoteException e) {
                    Logger.e(TAG, "onDownloadTaskRemove", e);
                }
            }
        });
    }


    /**
     * @param url      download url; must started with http/https
     * @param path     nonnull; parent directory of the file
     * @param filename self-defined filename; if null, it will be parsed by url or a pivot request by downloadManager
     * @param callback listener to inform submit success or fail
     */
    @Override
    public void submit(String url, String path, String filename, ISubmitCallback callback) {
        if (path == null) {
            throw new NullPointerException("path cannot be null");
        }
        DownloadExecutors.io.execute(() -> {
            waitForRecovering();
            submitInner(url, path, filename, callback);
        });
    }

    @Override
    public void delete(long id, boolean deleteFile) {
        DownloadExecutors.message.execute(() -> {
            DownloadTask downloadTask = taskTable.get(id);
            DownloadInfo info = infoTable.get(id);
            if (downloadTask != null && info != null) {
                taskTable.remove(id);
                infoTable.remove(id);
                runningTasksId.remove(id);
                waitingTasksId.remove(id);
                errorTasksId.remove(id);
                pausingTasksId.remove(id);
                downloadTask.delete(deleteFile);
                IDownloadTasksChangeCallback downloadTasksChangeCallback = this.downloadTasksChangeCallback;
                if (downloadTasksChangeCallback != null) {
                    try {
                        downloadTasksChangeCallback.onDownloadTaskRemove(id);
                    } catch (RemoteException e) {
                        Logger.e(TAG, "onDownloadTaskRemove", e);
                    }
                }
                schedule();
            } else {
                Logger.w("DownloadManager", "delete a task that is not present in DownloadManager! find in db. id = " + id);
                DownloadExecutors.io.execute(() -> {
                    DownloadInfoDao downloadInfoDao = daoSession.getDownloadInfoDao();
                    DownloadInfo downloadInfo = downloadInfoDao.load(id);
                    if (downloadInfo != null) {
                        PersistUtil.deleteDownloadInfo(daoSession, downloadInfo);
                        PersistUtil.deleteFile(downloadInfo, deleteFile);
                    }
                });
            }
        });
    }

    @Override
    public int getMaxRunningTask() {
        return maxRunningTask;
    }

    @Override
    public void setMaxRunningTask(int count) {
        if (maxRunningTask != count) {
            DownloadExecutors.message.execute(() -> {
                maxRunningTask = count;
                schedule();
            });
        }
    }

    public boolean isAvoidFrameDrop() {
        return avoidFrameDrop;
    }

    public void setAvoidFrameDrop(boolean avoidFrameDrop) {
        if (this.avoidFrameDrop != avoidFrameDrop) {
            DownloadExecutors.io.execute(() -> {
                this.avoidFrameDrop = avoidFrameDrop;
                if (!this.avoidFrameDrop) {
                    lastSendMessageTime.clear();
                }
            });
        }
    }

    public long getSendMessageIntervalNanos() {
        return sendMessageIntervalNanos;
    }

    public void setSendMessageIntervalNanos(long time) {
        this.sendMessageIntervalNanos = time;
    }

    @Override
    public DownloadInfo queryDownloadInfo(long id) {
        return infoTable.get(id);
    }

    @Override
    public List<DownloadInfo> queryFinishedDownloadInfoList() {
        DownloadInfoDao downloadInfoDao = daoSession.getDownloadInfoDao();
        return downloadInfoDao.queryBuilder()
                .where(DownloadInfoDao.Properties.DownloadItemState.eq(FINISH))
                .orderDesc(DownloadInfoDao.Properties.FinishedTime).build().list();
    }

    @Override
    public List<DownloadInfo> queryActiveDownloadInfoList() {
        waitForRecovering();
        return queryActiveDownloadInfoListInner();
    }

    private void waitForRecovering() {
        while (recoverCountDownLatch.getCount() > 0) {
            try {
                recoverCountDownLatch.await();
            } catch (InterruptedException e) {
                // do nothing
            }
        }
    }

    private List<DownloadInfo> queryActiveDownloadInfoListInner() {
        DownloadInfoDao downloadInfoDao = daoSession.getDownloadInfoDao();
        return downloadInfoDao.queryBuilder()
                .where(DownloadInfoDao.Properties.DownloadItemState.notEq(FINISH))
                .where(DownloadInfoDao.Properties.DownloadItemState.notEq(CANCELED))
                .orderDesc(DownloadInfoDao.Properties.CreatedTime)
                .build()
                .list();
    }

    @Override
    public List<DownloadInfo> queryDeletedDownloadInfoList() {
        DownloadInfoDao downloadInfoDao = daoSession.getDownloadInfoDao();
        return downloadInfoDao.queryBuilder()
                .where(DownloadInfoDao.Properties.DownloadItemState.eq(FINISH))
                .orderDesc(DownloadInfoDao.Properties.CreatedTime)
                .build()
                .list();
    }

    void setDownloadCallback(IDownloadCallback callback) {
        try {
            downloadCallbackSetLock.lock();
            this.downloadCallback = callback;
        } finally {
            downloadCallbackSetLock.unlock();
        }
    }

    void setDownloadTasksChangeCallback(IDownloadTasksChangeCallback callback) {
        try {
            downloadTasksChangeCallbackSetLock.lock();
            downloadTasksChangeCallback = callback;
        } finally {
            downloadTasksChangeCallbackSetLock.unlock();
        }
    }
}
