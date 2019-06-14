package com.lyc.downloader;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    // avoid memory leak
    private WeakReference<DownloadListener> userDownloadListener;
    DownloadTasksChangeListener downloadTasksChangeListener;
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
                startOrResume(downloadInfo.getId(), false);
            }
        }
    }

    private void recoverDownloadTasks() {
        DownloadExecutors.io.execute(() -> {
            try {
                List<DownloadInfo> downloadInfoList = queryActiveDownloadInfoListInner();
                List<DownloadInfo> finishedDownloadInfoList = queryFinishedDownloadInfoList();
                // make sure to add last
                List<Long> waitingList = new ArrayList<>();
                for (DownloadInfo downloadInfo : downloadInfoList) {
                    long id = downloadInfo.getId();
                    DownloadTask downloadTask = new DownloadTask(downloadInfo, client, this);
                    taskTable.put(id, downloadTask);
                    infoTable.put(id, downloadInfo);
                    switch (downloadInfo.getDownloadItemState()) {
                        case PENDING:
                        case PREPARING:
                        case RUNNING:
                            downloadTask.toWait(false);
                            break;
                        case PAUSING:
                        case PAUSED:
                            pausingTasksId.add(id);
                            break;
                        case WAITING:
                            waitingList.add(id);
                            break;
                        case ERROR:
                        case FATAL_ERROR:
                            errorTasksId.add(id);
                            break;
                    }
                }
                for (Long aLong : waitingList) {
                    DownloadTask downloadTask = taskTable.get(aLong);
                    if (downloadTask != null) {
                        downloadTask.toWait(false);
                    }
                }
                daoSession.getDownloadInfoDao().saveInTx(downloadInfoList);
                for (DownloadInfo downloadInfo : finishedDownloadInfoList) {
                    taskTable.put(downloadInfo.getId(), new DownloadTask(downloadInfo, client, this));
                    infoTable.put(downloadInfo.getId(), downloadInfo);
                }
            } finally {
                recoverCountDownLatch.countDown();
            }
        });
    }

    private void schedule() {
        if (!DownloadExecutors.isMessageThread()) {
            throw new IllegalStateException("cannot schedule outside DownloadExecutors#messageThread");
        }


        while (runningTasksId.size() > maxRunningTask) {
            Long id = runningTasksId.pollFirst();
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) {
                taskTable.remove(id);
                infoTable.remove(id);
                continue;
            }
            downloadTask.toWait(false);
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
            }
        }
    }

    @Override
    public void onPreparing(long id) {
        DownloadExecutors.androidMain.execute(() -> {
            if (userDownloadListener != null) {
                DownloadListener downloadListener = userDownloadListener.get();
                if (downloadListener != null) {
                    downloadListener.onPreparing(id);
                }
            }
        });
    }

    @Override
    public void onProgressUpdate(long id, long total, long cur, double bps) {
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
                DownloadExecutors.androidMain.execute(() -> {
                    if (userDownloadListener != null) {
                        DownloadListener downloadListener = userDownloadListener.get();
                        if (downloadListener != null) {
                            downloadListener.onProgressUpdate(id, total, cur, bps);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onUpdateInfo(DownloadInfo downloadInfo) {
        DownloadExecutors.androidMain.execute(() -> {
            if (userDownloadListener != null) {
                DownloadListener downloadListener = userDownloadListener.get();
                if (downloadListener != null) {
                    downloadListener.onUpdateInfo(downloadInfo);
                }
            }
        });
    }

    @Override
    public void onDownloadError(long id, int code, boolean fatal) {
        DownloadExecutors.message.execute(() -> {
            lastSendMessageTime.remove(id);
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            if (runningTasksId.remove(id) | pausingTasksId.remove(id) | waitingTasksId.remove(id)) {
                errorTasksId.add(id);
                DownloadExecutors.androidMain.execute(() -> {
                    if (userDownloadListener != null) {
                        DownloadListener downloadListener = userDownloadListener.get();
                        if (downloadListener != null) {
                            downloadListener.onDownloadError(id, code, fatal);
                        }
                    }
                });
                schedule();
            }
        });
    }

    @Override
    public void onDownloadStart(DownloadInfo downloadInfo) {
        DownloadExecutors.message.execute(() -> {
            if (!runningTasksId.contains(downloadInfo.getId())) {
                runningTasksId.offer(downloadInfo.getId());
            }
            DownloadExecutors.androidMain.execute(() -> {
                if (userDownloadListener != null) {
                    DownloadListener downloadListener = userDownloadListener.get();
                    if (downloadListener != null) {
                        downloadListener.onDownloadStart(downloadInfo);
                    }
                }
            });
        });
    }

    @Override
    public void onDownloadPausing(long id) {
        DownloadExecutors.androidMain.execute(() -> {
            if (userDownloadListener != null) {
                DownloadListener downloadListener = userDownloadListener.get();
                if (downloadListener != null) {
                    downloadListener.onDownloadPausing(id);
                }
            }
        });
    }

    @Override
    public void onDownloadPaused(long id) {
        DownloadExecutors.message.execute(() -> {
            DownloadTask downloadTask = taskTable.get(id);
            lastSendMessageTime.remove(id);
            if (downloadTask == null) return;
            if (runningTasksId.remove(id) | waitingTasksId.remove(id)) {
                pausingTasksId.add(id);
                DownloadExecutors.androidMain.execute(() -> {
                    if (userDownloadListener != null) {
                        DownloadListener downloadListener = userDownloadListener.get();
                        if (downloadListener != null) {
                            downloadListener.onDownloadPaused(id);
                        }
                    }
                });
                schedule();
            }
        });
    }

    @Override
    public void onDownloadCanceled(long id) {
        DownloadExecutors.message.execute(() -> {
            lastSendMessageTime.remove(id);
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            if (runningTasksId.remove(id) | pausingTasksId.remove(id) |
                    errorTasksId.remove(id) | waitingTasksId.remove(id)) {
                taskTable.remove(id);
                Logger.d("DownloadManager", "remove task#" + id + " running tasks = " + runningTasksId.size());
                DownloadExecutors.androidMain.execute(() -> {
                    if (userDownloadListener != null) {
                        DownloadListener downloadListener = userDownloadListener.get();
                        if (downloadListener != null) {
                            downloadListener.onDownloadCanceled(id);
                        }
                    }
                });
                schedule();
            }
        });
    }

    @Override
    public void onDownloadTaskWait(long id) {
        DownloadExecutors.message.execute(() -> {
            lastSendMessageTime.remove(id);
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            if (!waitingTasksId.contains(id)) {
                pausingTasksId.remove(id);
                errorTasksId.remove(id);
                waitingTasksId.add(id);
                DownloadExecutors.androidMain.execute(() -> {
                    if (userDownloadListener != null) {
                        DownloadListener downloadListener = userDownloadListener.get();
                        if (downloadListener != null) {
                            downloadListener.onDownloadTaskWait(id);
                        }
                    }
                });
                schedule();
            }
        });
    }

    @Override
    public void onDownloadFinished(DownloadInfo downloadInfo) {
        DownloadExecutors.message.execute(() -> {
            long id = downloadInfo.getId();
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            if (runningTasksId.remove(id) | waitingTasksId.remove(id) | pausingTasksId.remove(id) | errorTasksId.remove(id)) {
                DownloadExecutors.androidMain.execute(() -> {
                    if (userDownloadListener != null) {
                        DownloadListener downloadListener = userDownloadListener.get();
                        if (downloadListener != null) {
                            downloadListener.onDownloadFinished(downloadInfo);
                        }
                    }
                });
                schedule();
            }
        });
    }

    @WorkerThread
    private void submitInner(String url, String path, String filename, SubmitListener listener) {
        DownloadInfo downloadInfo = new DownloadInfo(null, url, path, filename, true, WAITING,
                0, 0, null, new Date(), null, null);
        try {
            Long insertId = PersistUtil.persistDownloadInfo(daoSession, downloadInfo, null);
            DownloadExecutors.message.execute(() -> {
                if (insertId != null) {
                    infoTable.put(insertId, downloadInfo);
                    DownloadTask downloadTask = new DownloadTask(downloadInfo, client, this);
                    taskTable.put(insertId, downloadTask);
                    waitingTasksId.add(insertId);
                    DownloadExecutors.androidMain.execute(() -> listener.submitSuccess(downloadInfo));
                    // all DownloadTasksChangeListener runs on message thread
                    DownloadExecutors.message.execute(() -> {
                        if (downloadTasksChangeListener != null) {
                            downloadTasksChangeListener.onNewDownloadTaskArrive(downloadInfo);
                        }
                    });
                    schedule();
                } else {
                    DownloadExecutors.androidMain.execute(() -> listener.submitFail(new Exception("创建任务失败")));
                }
            });
        } catch (Exception e) {
            DownloadExecutors.androidMain.execute(() -> listener.submitFail(e));
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
                            DownloadTask newDownloadTask = new DownloadTask(downloadInfo, client, this);
                            taskTable.put(id, newDownloadTask);
                            infoTable.put(id, downloadInfo);
                            newDownloadTask.toWait(true);
                        });
                    }
                });
            } else if (downloadTask != null && ((pausingTasksId.remove(id) | errorTasksId.remove(id)) ||
                    downloadTask.getState() == FINISH)) {
                downloadTask.toWait(restart);
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
            if (downloadTasksChangeListener != null) {
                downloadTasksChangeListener.onDownloadTaskRemove(id);
            }
        });
    }


    /**
     * @param url      download url; must started with http/https
     * @param path     nonnull; parent directory of the file
     * @param filename self-defined filename; if null, it will be parsed by url or a pivot request by downloadManager
     * @param listener listener to inform submit success or fail
     */
    @Override
    public void submit(String url, String path, String filename, SubmitListener listener) {
        waitForRecovering();
        if (path == null) {
            throw new NullPointerException("path cannot be null");
        }
        DownloadExecutors.io.execute(() -> submitInner(url, path, filename, listener));
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
                if (downloadTasksChangeListener != null) {
                    downloadTasksChangeListener.onDownloadTaskRemove(id);
                }
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

    void setUserDownloadListener(DownloadListener downloadListener) {
        if (downloadListener == null) return;
        if (userDownloadListener != null) {
            userDownloadListener.clear();
        }
        userDownloadListener = new WeakReference<>(downloadListener);
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
}
