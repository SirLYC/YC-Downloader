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
import com.lyc.downloader.utils.UniqueDequeue;
import okhttp3.OkHttpClient;
import okhttp3.OkHttpClient.Builder;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
    // TODO: 2019/4/26 register network change BroadReceiver
    private final Context appContext;
    private final LongSparseArray<DownloadTask> taskTable = new LongSparseArray<>();
    private final LongSparseArray<DownloadInfo> infoTable = new LongSparseArray<>();
    private final LongSparseArray<Long> lastSendMessageTime = new LongSparseArray<>();
    private final Deque<Long> runningTasksId = new UniqueDequeue<>();
    private final Deque<Long> waitingTasksId = new UniqueDequeue<>();
    private final Deque<Long> errorTasksId = new UniqueDequeue<>();
    private final Deque<Long> pausingTasksId = new UniqueDequeue<>();
    private final Deque<Long> finishedTasksId = new UniqueDequeue<>();
    // avoid memory leak
    private WeakReference<DownloadListener> userDownloadListener;
    private Set<WeakReference<RecoverListener>> userRecoverListeners = new HashSet<>();
    private int maxRunningTask = 4;
    private boolean recoverTasks;
    private volatile boolean avoidFrameDrop = true;
    // ns
    private volatile long sendMessageInterval = TimeUnit.MILLISECONDS.toNanos(500);

    private DownloadManager(OkHttpClient client, Context appContext) {
        this.client = client;
        this.appContext = appContext;
        DownloadExecutors.init();
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
                DownloadExecutors.message.execute(() -> {
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
                            case FATAL_ERROR:
                                errorTasksId.add(id);
                                break;
                        }
                    }
                    for (Long aLong : waitingList) {
                        DownloadTask downloadTask = taskTable.get(aLong);
                        if (downloadTask != null) {
                            downloadTask.toWait();
                        }
                    }
                    Log.d("DownloadManager", "recovered " + downloadInfoList.size() + " tasks!");
                    recoverTasks = true;
                    List<DownloadInfo> list = queryAllDownloadInfo();
                    if (!downloadInfoList.isEmpty()) {
                        for (WeakReference<RecoverListener> userRecoverListener : userRecoverListeners) {
                            RecoverListener recoverListener = userRecoverListener.get();
                            if (recoverListener != null) {
                                DownloadExecutors.androidMain.execute(() -> recoverListener.recoverReady(list));
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
    private void schedule() {
        if (!DownloadExecutors.isMessageThread()) {
            throw new IllegalStateException("cannot schedule outside DownloadExecutors#messageThread");
        }
        while (runningTasksId.size() < maxRunningTask && !waitingTasksId.isEmpty()) {
            Long id = waitingTasksId.pollFirst();
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) {
                taskTable.remove(id);
                infoTable.remove(id);
                continue;
            }
            if (downloadTask.start(false)) {
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
                if (lastTime == null || lastTime + sendMessageInterval <= currentTime) {
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
    public void onDownloadError(long id, String reason, boolean fatal) {
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
                            downloadListener.onDownloadError(id, reason, fatal);
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
                    errorTasksId.remove(id) | finishedTasksId.remove(id) |
                    waitingTasksId.remove(id)) {
                taskTable.remove(id);
                Log.d("DownloadManager", "remove task#" + id + " running tasks = " + runningTasksId.size());
                for (Long aLong : runningTasksId) {
                    DownloadInfo downloadInfo = infoTable.get(aLong);
                    System.out.println(downloadInfo == null ? null : downloadInfo.getFilename());
                }
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
                finishedTasksId.remove(id);
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
                finishedTasksId.add(id);
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
    private void submitInner(String url, String path, String filename, List<CustomerHeader> customerHeaders, SubmitListener listener) {
        DownloadInfo downloadInfo = new DownloadInfo(null, url, path, filename, true, WAITING,
                0, 0, null, new Date(), null, null);
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
            DownloadExecutors.message.execute(() -> {
                if (insertId != null && insertId != -1) {
                    infoTable.put(insertId, downloadInfo);
                    DownloadTask downloadTask = new DownloadTask(downloadInfo, client, this);
                    taskTable.put(insertId, downloadTask);
                    waitingTasksId.add(insertId);
                    DownloadExecutors.androidMain.execute(() -> listener.submitSuccess(downloadInfo));
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
    // include re-download
    @MainThread
    public void startOrResume(long id) {
        DownloadExecutors.message.execute(() -> {
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) {
                return;
            }

            if (pausingTasksId.remove(id) | errorTasksId.remove(id) | finishedTasksId.remove(id)) {
                downloadTask.toWait();
            }
        });
    }

    @MainThread
    public void pause(long id) {
        DownloadExecutors.message.execute(() -> {
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            downloadTask.pause();
        });
    }

    // also delete
    @MainThread
    public void cancel(long id) {
        DownloadExecutors.message.execute(() -> {
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            downloadTask.cancel();
        });
    }


    /**
     * @param url             download url; must started with http/https
     * @param path            nonnull; parent directory of the file
     * @param filename        self-defined filename; if null, it will be parsed by url or a pivot request by downloadManager
     * @param customerHeaders customer header (`Range` will be removed)
     * @param listener        listener to inform submit success or fail
     */
    @MainThread
    public void submit(String url, String path, String filename, Map<String, String> customerHeaders, SubmitListener listener) {
        List<CustomerHeader> headers = new ArrayList<>();
        if (customerHeaders != null) {
            for (String s : customerHeaders.keySet()) {
                if (s != null && !s.equalsIgnoreCase("range")) {
                    headers.add(new CustomerHeader(null, 0, s, customerHeaders.get(s)));
                }
            }
        }
        if (path == null) {
            throw new NullPointerException("path cannot be null");
        }
        DownloadExecutors.io.execute(() -> submitInner(url, path, filename, headers, listener));
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

    public boolean isAvoidFrameDrop() {
        return avoidFrameDrop;
    }

    public void setAvoidFrameDrop(boolean avoidFrameDrop) {
        this.avoidFrameDrop = avoidFrameDrop;
    }

    public void setSendMessageInterval(long time, TimeUnit timeUnit) {
        this.sendMessageInterval = timeUnit.toNanos(time);
    }

    public long getSendMessageInterval() {
        return sendMessageInterval;
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

    private List<DownloadInfo> queryAllDownloadInfo() {
        LongSparseArray<DownloadInfo> infoTable;

        if (DownloadExecutors.isMessageThread()) {
            infoTable = this.infoTable;
        } else {
            infoTable = this.infoTable.clone();
        }

        int size = infoTable.size();
        PriorityQueue<DownloadInfo> queue = new PriorityQueue<>();
        for (int i = 0; i < size; i++) {
            DownloadInfo downloadInfo = infoTable.valueAt(i);
            if (downloadInfo.getDownloadItemState() != CANCELED) {
                queue.offer(downloadInfo);
            }
        }
        return new ArrayList<>(queue);
    }

    public interface SubmitListener {
        void submitSuccess(DownloadInfo downloadInfo);

        void submitFail(Exception e);
    }

    public interface RecoverListener {
        void recoverReady(List<DownloadInfo> recoveredTasks);
    }
}
