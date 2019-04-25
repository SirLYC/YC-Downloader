package com.lyc.downloader;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
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
import okhttp3.OkHttpClient.Builder;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
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
    private Set<WeakReference<RecoverListener>> userRecoverListeners = new HashSet<>();
    private int maxRunningTask = 4;
    private boolean recoverTasks;

    private DownloadManager(OkHttpClient client, Context appContext) {
        this.client = client;
        this.appContext = appContext;
        SQLiteDatabase db = new DevOpenHelper(appContext, DB_NAME).getWritableDatabase();
        daoSession = new DaoMaster(db).newSession();
        recoverDownloadTasks();
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
        HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
        httpLoggingInterceptor.setLevel(Level.HEADERS);
        OkHttpClient client = new Builder().addInterceptor(httpLoggingInterceptor).build();
        init(client, context);
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
                        DownloadTask downloadTask = new DownloadTask(downloadInfo, client, this);
                        taskTable.put(id, downloadTask);
                        infoTable.put(id, downloadInfo);
                        switch (downloadInfo.getDownloadItemState()) {
                            case PENDING:
                            case PREPARING:
                            case RUNNING:
                                waitingTasksId.offer(id);
                                downloadTask.toWait();
                                break;
                            case PAUSING:
                            case PAUSED:
                                pausingTasksId.add(id);
                                break;
                            case FINISH:
                                finishedTasksId.add(id);
                                break;
                            case WAITING:
                                waitingList.add(id);
                                break;
                            case CANCELED:
                                taskTable.remove(id);
                                infoTable.remove(id);
                                break;
                            case ERROR:
                                errorTasksId.add(id);
                                break;
                            case FATAL_ERROR:
                                errorTasksId.add(id);
                                break;
                        }
                    }
                    for (Long aLong : waitingList) {
                        DownloadTask downloadTask = taskTable.get(aLong);
                        if (downloadTask != null) {
                            waitingTasksId.offer(aLong);
                        }
                    }
                    schedule();
                    Log.d("DownloadManager", "recovered " + downloadInfoList.size() + " tasks!");
                    recoverTasks = true;
                    if (!downloadInfoList.isEmpty()) {
                        for (WeakReference<RecoverListener> userRecoverListener : userRecoverListeners) {
                            RecoverListener recoverListener = userRecoverListener.get();
                            if (recoverListener != null) {
                                recoverListener.recoverReady(queryAllDownloadInfo());
                            }
                            userRecoverListener.clear();
                        }
                    } else {
                        for (WeakReference<RecoverListener> userRecoverListener : userRecoverListeners) {
                            userRecoverListener.clear();
                        }
                    }
                    userRecoverListeners.clear();
                });
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
    public void onProgressUpdate(long id, long total, long cur) {
        DownloadExecutors.androidMain.execute(() -> {
            if (userDownloadListener != null) {
                DownloadListener downloadListener = userDownloadListener.get();
                if (downloadListener != null) {
                    downloadListener.onProgressUpdate(id, total, cur);
                }
            }
        });
    }

    @Override
    public void onSpeedChange(long id, double bps) {
        DownloadExecutors.androidMain.execute(() -> {
            if (userDownloadListener != null) {
                DownloadListener downloadListener = userDownloadListener.get();
                if (downloadListener != null) {
                    downloadListener.onSpeedChange(id, bps);
                }
            }
        });
    }

    @Override
    public void onDownloadError(long id, String reason, boolean fatal) {
        DownloadExecutors.androidMain.execute(() -> {
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            if (runningTasksId.remove(id) | pausingTasksId.remove(id) | waitingTasksId.remove(id)) {
                errorTasksId.add(id);
                if (userDownloadListener != null) {
                    DownloadListener downloadListener = userDownloadListener.get();
                    if (downloadListener != null) {
                        downloadListener.onDownloadError(id, reason, fatal);
                    }
                }
                schedule();
            }
        });
    }

    @Override
    public void onDownloadStart(long id) {
        DownloadExecutors.androidMain.execute(() -> {
            if (userDownloadListener != null) {
                DownloadListener downloadListener = userDownloadListener.get();
                if (downloadListener != null) {
                    downloadListener.onDownloadStart(id);
                }
            }
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
        DownloadExecutors.androidMain.execute(() -> {
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            if (runningTasksId.remove(id) || waitingTasksId.remove(id)) {
                pausingTasksId.add(id);
                if (userDownloadListener != null) {
                    DownloadListener downloadListener = userDownloadListener.get();
                    if (downloadListener != null) {
                        downloadListener.onDownloadPaused(id);
                    }
                }
                schedule();
            }
        });
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
            if (userDownloadListener != null) {
                DownloadListener downloadListener = userDownloadListener.get();
                if (downloadListener != null) {
                    downloadListener.onDownloadCanceled(id);
                }
            }
            schedule();
        });
    }

    @Override
    public void onDownloadTaskWait(long id) {
        DownloadExecutors.androidMain.execute(() -> {
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            if (!waitingTasksId.contains(id)) {
                pausingTasksId.remove(id);
                errorTasksId.remove(id);
                finishedTasksId.remove(id);
                waitingTasksId.add(id);
                if (userDownloadListener != null) {
                    DownloadListener downloadListener = userDownloadListener.get();
                    if (downloadListener != null) {
                        downloadListener.onDownloadTaskWait(id);
                    }
                }
                schedule();
            }
        });
    }

    @Override
    public void onDownloadFinished(long id) {
        DownloadExecutors.androidMain.execute(() -> {
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            if (runningTasksId.remove(id)) {
                finishedTasksId.add(id);
                if (userDownloadListener != null) {
                    DownloadListener downloadListener = userDownloadListener.get();
                    if (downloadListener != null) {
                        downloadListener.onDownloadFinished(id);
                    }
                }
                schedule();
            }
        });
    }

    @WorkerThread
    private void submitInner(String url, String path, List<CustomerHeader> customerHeaders, SubmitListener listener) {
        File file = new File(path);
        if (file.exists()) {
            DownloadExecutors.androidMain.execute(() -> listener.submitFail(new FileExitsException(file)));
            return;
        }
        DownloadInfo downloadInfo = new DownloadInfo(null, url, path, true, WAITING, 0, 0, new Date(), null, null);
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
            DownloadExecutors.androidMain.execute(() -> {
                if (insertId != null && insertId != -1) {
                    infoTable.put(insertId, downloadInfo);
                    DownloadTask downloadTask = new DownloadTask(downloadInfo, client, this);
                    taskTable.put(insertId, downloadTask);
                    waitingTasksId.add(insertId);
                    listener.submitSuccess(insertId);
                    schedule();
                } else {
                    listener.submitFail(new Exception("创建任务失败"));
                }
            });
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

        if (pausingTasksId.remove(id) | errorTasksId.remove(id) | finishedTasksId.remove(id)) {
            downloadTask.toWait();
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
    public void submit(String url, String path, Map<String, String> customerHeaders, SubmitListener listener) {
        List<CustomerHeader> headers = new ArrayList<>();
        if (customerHeaders != null) {
            for (String s : customerHeaders.keySet()) {
                headers.add(new CustomerHeader(null, 0, s, customerHeaders.get(s)));
            }
        }
        DownloadExecutors.io.execute(() -> submitInner(url, path, headers, listener));
    }

    @MainThread
    public int getMaxRunningTask() {
        return maxRunningTask;
    }

    @MainThread
    public void setMaxRunningTask(int maxRunningTask) {
        if (maxRunningTask <= 0) maxRunningTask = 1;
        this.maxRunningTask = maxRunningTask;
    }

    @MainThread
    public void setUserDownloadListener(DownloadListener downloadListener) {
        if (downloadListener == null) return;
        if (userDownloadListener != null) {
            userDownloadListener.clear();
        }
        userDownloadListener = new WeakReference<>(downloadListener);
    }

    @MainThread
    public void addUserRecoverListener(RecoverListener recoverListener) {
        if (recoverListener != null) {
            if (recoverTasks) {
                recoverListener.recoverReady(queryAllDownloadInfo());
            } else {
                userRecoverListeners.add(new WeakReference<>(recoverListener));
            }
        }
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
        for (Long aLong : finishedTasksId) {
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
