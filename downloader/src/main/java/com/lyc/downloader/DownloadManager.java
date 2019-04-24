package com.lyc.downloader;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.MainThread;
import androidx.annotation.WorkerThread;
import androidx.collection.LongSparseArray;
import com.lyc.downloader.db.CustomerHeader;
import com.lyc.downloader.db.CustomerHeaderDao;
import com.lyc.downloader.db.DaoMaster;
import com.lyc.downloader.db.DaoMaster.DevOpenHelper;
import com.lyc.downloader.db.DaoSession;
import com.lyc.downloader.db.DownloadInfo;
import com.lyc.downloader.db.DownloadInfoDao;
import okhttp3.OkHttpClient;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.lyc.downloader.DownloadTask.*;

/**
 * @author liuyuchuan
 * @date 2019/4/1
 * @email kevinliu.sir@qq.com
 */
public class DownloadManager implements DownloadListener {

    static final String DB_NAME = "yuchuan_downloader_db";
    @SuppressLint("StaticFieldLeak")
    private volatile static DownloadManager instance;
    // for http
    private final OkHttpClient client;
    final DaoSession daoSession;
    private final Context appContext;
    private final LongSparseArray<DownloadTask> taskTable = new LongSparseArray<>();
    private final LongSparseArray<DownloadInfo> infoTable = new LongSparseArray<>();
    private final Deque<Long> runningTasksId = new ArrayDeque<>();
    private final Deque<Long> waitingTasksId = new ArrayDeque<>();
    private final Deque<Long> errorTasksId = new ArrayDeque<>();
    private final Deque<Long> pausingTasksId = new ArrayDeque<>();
    private final Deque<Long> finishedTasksId = new ArrayDeque<>();
    // avoid memory leak
    private WeakReference<DownloadListener> userDownloadListener;
    private WeakReference<RecoverListener> userRecoverListener;
    private int maxRunningTask = 4;

    public DownloadManager(OkHttpClient client, Context appContext) {
        this.client = client;
        this.appContext = appContext;
        SQLiteDatabase db = new DevOpenHelper(appContext, DB_NAME).getWritableDatabase();
        daoSession = new DaoMaster(db).newSession();
        recoverDownloadTasks();
        schedule();
    }

    public static void init(OkHttpClient client, Context context) {
        if (context == null) {
            throw new NullPointerException("Context cannot be null!");
        }

        if (client == null) {
            throw new NullPointerException("Http client cannot be null!");
        }

        if (instance == null) {
            synchronized (DownloadManager.class) {
                if (instance == null) {
                    instance = new DownloadManager(client, context);
                }
            }
        }
    }

    public static void init(Context context) {
        init(new OkHttpClient(), context);
    }


    public static DownloadManager instance() {
        if (instance == null) {
            throw new IllegalStateException("init download manager by DownloadManager.init(Context context) first!");
        }
        return instance;
    }

    private void recoverDownloadTasks() {
        DownloadExecutors.io.execute(() -> {
            DownloadInfoDao downloadInfoDao = daoSession.getDownloadInfoDao();
            List<DownloadInfo> downloadInfoList = downloadInfoDao.loadAll();
            if (!downloadInfoList.isEmpty()) {
                DownloadExecutors.androidMain.execute(() -> {
                    // make sure to add last
                    List<Long> waitingList = new ArrayList<>();
                    for (DownloadInfo downloadInfo : downloadInfoList) {
                        long id = downloadInfo.getId();
                        taskTable.put(id, new DownloadTask(downloadInfo, client, this));
                        infoTable.put(id, downloadInfo);
                        switch (downloadInfo.getDownloadItemState()) {
                            case PENDING:
                            case PREPARING:
                            case RUNNING:
                                downloadInfo.setDownloadItemState(PENDING);
                                waitingTasksId.add(id);
                                break;
                            case PAUSING:
                            case PAUSED:
                                downloadInfo.setDownloadItemState(PAUSED);
                                pausingTasksId.add(id);
                                break;
                            case FINISH:
                                downloadInfo.setDownloadItemState(FINISH);
                                finishedTasksId.add(id);
                                break;
                            case WAITING:
                                downloadInfo.setDownloadItemState(WAITING);
                                waitingList.add(id);
                                break;
                            case CANCELLING:
                            case CANCELED:
                                downloadInfo.setDownloadItemState(CANCELED);
                                taskTable.remove(id);
                                infoTable.remove(id);
                                break;
                            case ERROR:
                                downloadInfo.setDownloadItemState(ERROR);
                                errorTasksId.add(id);
                                break;
                            case FATAL_ERROR:
                                downloadInfo.setDownloadItemState(FATAL_ERROR);
                                errorTasksId.add(id);
                                break;
                        }
                    }
                    waitingTasksId.addAll(waitingList);
                    schedule();
                });
            }
            RecoverListener recoverListener;
            if (!downloadInfoList.isEmpty() && (recoverListener = userRecoverListener.get()) != null) {
                recoverListener.recoverReady(downloadInfoList);
            }
        });
    }

    // first come first service
    @MainThread
    private void schedule() {
        while (runningTasksId.size() < maxRunningTask && !waitingTasksId.isEmpty()) {
            Long id = waitingTasksId.pollFirst();
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) {
                taskTable.remove(id);
                continue;
            }
            downloadTask.start(false);
            runningTasksId.offer(id);
        }
    }

    @Override
    public void onPrepared(long id) {
        if (userDownloadListener != null) {
            DownloadListener downloadListener = userDownloadListener.get();
            if (downloadListener != null) {
                downloadListener.onPrepared(id);
            }
        }
    }

    @Override
    public void onProgressUpdate(long id, long total, long cur) {
        if (userDownloadListener != null) {
            DownloadListener downloadListener = userDownloadListener.get();
            if (downloadListener != null) {
                downloadListener.onProgressUpdate(id, total, cur);
            }
        }
    }

    @Override
    public void onSpeedChange(long id, double bps) {
        if (userDownloadListener != null) {
            DownloadListener downloadListener = userDownloadListener.get();
            if (downloadListener != null) {
                downloadListener.onSpeedChange(id, bps);
            }
        }
    }

    @Override
    public void onDownloadError(long id, String reason, boolean fatal) {
        DownloadExecutors.androidMain.execute(() -> {
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            if (runningTasksId.remove(id) | pausingTasksId.remove(id) | waitingTasksId.remove(id)) {
                errorTasksId.add(id);
            }
            schedule();
        });
        if (userDownloadListener != null) {
            DownloadListener downloadListener = userDownloadListener.get();
            if (downloadListener != null) {
                downloadListener.onPrepared(id);
            }
        }
    }

    @Override
    public void onDownloadStart(long id) {
        if (userDownloadListener != null) {
            DownloadListener downloadListener = userDownloadListener.get();
            if (downloadListener != null) {
                downloadListener.onDownloadStart(id);
            }
        }
    }

    @Override
    public void onDownloadPausing(long id) {
        onDownloadPaused(id);
        if (userDownloadListener != null) {
            DownloadListener downloadListener = userDownloadListener.get();
            if (downloadListener != null) {
                downloadListener.onDownloadPaused(id);
            }
        }
    }

    @Override
    public void onDownloadPaused(long id) {
        DownloadExecutors.androidMain.execute(() -> {
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            if (runningTasksId.remove(id)) {
                pausingTasksId.add(id);
            }
            schedule();
        });
        if (userDownloadListener != null) {
            DownloadListener downloadListener = userDownloadListener.get();
            if (downloadListener != null) {
                downloadListener.onDownloadPaused(id);
            }
        }
    }

    @Override
    public void onDownloadCancelling(long id) {
        onDownloadCanceled(id);
        if (userDownloadListener != null) {
            DownloadListener downloadListener = userDownloadListener.get();
            if (downloadListener != null) {
                downloadListener.onDownloadCancelling(id);
            }
        }
    }

    @Override
    public void onDownloadCanceled(long id) {
        DownloadExecutors.androidMain.execute(() -> {
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            if (runningTasksId.remove(id) | pausingTasksId.remove(id) |
                    errorTasksId.remove(id) | finishedTasksId.remove(id) |
                    waitingTasksId.remove(id)) {
                taskTable.remove(id);
            }
            schedule();
        });
        if (userDownloadListener != null) {
            DownloadListener downloadListener = userDownloadListener.get();
            if (downloadListener != null) {
                downloadListener.onDownloadCanceled(id);
            }
        }
    }

    @Override
    public void onDownloadFinished(long id) {
        DownloadExecutors.androidMain.execute(() -> {
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            if (runningTasksId.remove(id)) {
                finishedTasksId.add(id);
            }
            schedule();
        });
        if (userDownloadListener != null) {
            DownloadListener downloadListener = userDownloadListener.get();
            if (downloadListener != null) {
                downloadListener.onDownloadFinished(id);
            }
        }
    }

    @WorkerThread
    private void submitInner(String url, String path, List<CustomerHeader> customerHeaders, SubmitListener listener, DownloadListener downloadListener) {
        File file = new File(path);
        if (file.exists()) {
            listener.submitFail(new FileExitsException(file));
            return;
        }
        DownloadInfo downloadInfo = new DownloadInfo(null, url, path, PENDING);
        try {
            Long insertId = daoSession.callInTx(() -> {
                long id = daoSession.getDownloadInfoDao().insert(downloadInfo);
                if (id != -1) {
                    CustomerHeaderDao customerHeaderDao = daoSession.getCustomerHeaderDao();
                    for (CustomerHeader customerHeader : customerHeaders) {
                        customerHeader.setDownloadInfoId(id);
                        customerHeaderDao.insert(customerHeader);
                    }
                }
                return id;
            });
            if (insertId != null) {
                listener.submitSuccess(insertId);
                DownloadExecutors.androidMain.execute(() -> {
                    infoTable.put(insertId, downloadInfo);
                    taskTable.put(insertId, new DownloadTask(downloadInfo, client, downloadListener));
                    waitingTasksId.add(insertId);
                    schedule();
                });
            } else {
                listener.submitFail(new Exception("创建任务失败"));
            }
        } catch (Exception e) {
            listener.submitFail(e);
        }
    }

    /************************** api **************************/
    // include re-download
    @MainThread
    public void startOrResume(long id) {
        DownloadTask downloadTask = taskTable.get(id);
        if (downloadTask == null) {
            return;
        }
        if (waitingTasksId.contains(id)) {
            return;
        }

        if (pausingTasksId.remove(id) | errorTasksId.remove(id) | finishedTasksId.remove(id)) {
            waitingTasksId.add(id);
        }
    }

    @MainThread
    public void pause(long id) {
        DownloadTask downloadTask = taskTable.get(id);
        if (downloadTask == null) return;
        downloadTask.pause();
    }

    // also delete
    @MainThread
    public void cancel(long id) {
        DownloadTask downloadTask = taskTable.get(id);
        if (downloadTask == null) return;
        downloadTask.cancel();
    }

    @MainThread
    public void submit(String url, String path, Map<String, String> customerHeaders, SubmitListener listener, DownloadListener downloadListener) {
        List<CustomerHeader> headers = new ArrayList<>();
        for (String s : customerHeaders.keySet()) {
            headers.add(new CustomerHeader(null, 0, s, customerHeaders.get(s)));
        }
        DownloadExecutors.io.execute(() -> submitInner(url, path, headers, listener, downloadListener));
    }

    @MainThread
    public int getMaxRunningTask() {
        return maxRunningTask;
    }

    @MainThread
    public void setMaxRunningTask(int maxRunningTask) {
        if (maxRunningTask < 0) return;
        this.maxRunningTask = maxRunningTask;
    }

    @MainThread
    public void setUserDownloadListener(DownloadListener downloadListener) {
        if (userDownloadListener != null) {
            userDownloadListener.clear();
        }
        userDownloadListener = new WeakReference<>(downloadListener);
    }

    @MainThread
    public void setUserRecoverListener(RecoverListener recoverListener) {
        if (userRecoverListener != null) {
            userRecoverListener.clear();
        }
        userRecoverListener = new WeakReference<>(recoverListener);
    }

    public DownloadInfo queryDownloadInfo(long id) {
        return infoTable.get(id);
    }

    public List<DownloadInfo> queryAllDownloadInfo() {
        List<DownloadInfo> result = new ArrayList<>();
        Set<Long> addIds = new HashSet<>();
        for (Long aLong : runningTasksId) {
            if (addIds.add(aLong)) {
                result.add(infoTable.get(aLong));
            }
        }
        for (Long aLong : waitingTasksId) {
            if (addIds.add(aLong)) {
                result.add(infoTable.get(aLong));
            }
        }
        for (Long aLong : pausingTasksId) {
            if (addIds.add(aLong)) {
                result.add(infoTable.get(aLong));
            }
        }
        for (Long aLong : errorTasksId) {
            if (addIds.add(aLong)) {
                result.add(infoTable.get(aLong));
            }
        }
        return result;
    }

    public interface SubmitListener {
        void submitSuccess(long id);

        void submitFail(Exception e);
    }

    public interface RecoverListener {
        void recoverReady(List<DownloadInfo> recoveredTasks);
    }
}
