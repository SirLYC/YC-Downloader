package com.lyc.downloader;

import android.util.SparseArray;
import androidx.annotation.IntDef;
import androidx.annotation.WorkerThread;
import com.lyc.downloader.db.DownloadInfo;
import com.lyc.downloader.db.DownloadThreadInfo;
import com.lyc.downloader.utils.DownloadStringUtil;
import com.lyc.downloader.utils.Logger;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author liuyuchuan
 * @date 2019/4/1
 * @email kevinliu.sir@qq.com
 */
public class DownloadTask {

    public static final int PENDING = 0;
    public static final int CONNECTING = 1;
    public static final int RUNNING = 2;
    public static final int PAUSING = 3;
    public static final int PAUSED = 4;
    public static final int FINISH = 5;
    public static final int WAITING = 6;
    public static final int CANCELED = 7;
    public static final int ERROR = 8;
    // this state should not be resumed
    public static final int FATAL_ERROR = 9;

    // in bytes
    private static final int MAX_BUFFER = 1 << 16;
    private static final int MIN_BUFFER = 4 * (2 << 10);

    // TODO: 2019/4/26 design a suitable thread count choose algorithm...
    private static final int MAX_DOWNLOAD_THREAD = 4;

    private static Lock fileLock = new ReentrantLock();
    // ATTENTION: SYNC OPERATE
    private volatile int state;
    private final Lock stateLock = new ReentrantLock();
    private final Lock runLock = new ReentrantLock();
    private CountDownLatch startDownloadLatch;
    private final DownloadError downloadError = DownloadError.instance();
    private File downloadFile;
    private int downloadThreadCount = 4;
    private final OkHttpClient client;
    private Call pivotCall;
    private DownloadRunnable[] downloadRunnables;
    private WriteToDiskRunnable[] writeToDiskRunnables;
    private Set<Thread> threads = new HashSet<>();
    final DownloadInfo downloadInfo;
    private final SparseArray<DownloadThreadInfo> downloadThreadInfos = new SparseArray<>();
    private long bufferTimeout = 1;
    private AtomicLong downloadSize = new AtomicLong(0);
    private Request baseRequest;
    private boolean resuming;
    private AtomicInteger leftActiveThreadCount = new AtomicInteger();
    private Semaphore semaphore;
    private boolean restart = false;
    static Pattern reduplicatedFilenamePattern = Pattern.compile("^(.*)\\(([1-9][0-9]*)\\)$");
    private volatile AtomicBoolean deleted = new AtomicBoolean(false);
    private static final String TAG = "DownloadTask";
    /**
     * also {@link DownloadListener}
     */
    private DownloadManager downloadManager = DownloadManager.instance();

    /**
     * only used by {@link DownloadManager}
     */
    DownloadTask(DownloadInfo downloadInfo, OkHttpClient client) {
        this.downloadInfo = downloadInfo;
        this.client = client;
        // init by download manager
        // in a single thread
        switch (downloadInfo.getDownloadItemState()) {
            case PENDING:
            case CONNECTING:
            case RUNNING:
            case WAITING:
                state = WAITING;
                break;
            case PAUSING:
            case PAUSED:
                state = PAUSED;
                break;
            case FINISH:
                state = FINISH;
                break;
            case CANCELED:
                state = CANCELED;
                break;
            case ERROR:
                state = ERROR;
                break;
            case FATAL_ERROR:
                state = FATAL_ERROR;
                break;
        }
        downloadSize.set(downloadInfo.getDownloadedSize());
        downloadInfo.setDownloadItemState(state);
    }

    @DownloadState
    int getState() {
        return state;
    }

    private boolean buildBaseRequest() {
        Builder builder = new Builder();
        try {
            builder.url(downloadInfo.getUrl());
        } catch (Exception e) {
            reportError(DownloadError.ERROR_ILLEGAL_URL);
            return false;
        }
        baseRequest = builder.build();
        return true;
    }

    private boolean doPivotCall() {
        File parent = new File(downloadInfo.getPath());
        if ((!parent.exists() && !parent.mkdirs()) || !parent.isDirectory()) {
            Logger.e(TAG, "cannot create directory: " + parent.getAbsolutePath());
            reportError(DownloadError.ERROR_CREATE_DIR);
            return false;
        }


        if (state != CONNECTING) {
            return false;
        }

        Request pivotRequest = baseRequest.newBuilder().header("Range", "bytes=0-").build();
        String lastModified;
        String filename;
        long totalSize;
        boolean resumable;

        try {
            pivotCall = client.newCall(pivotRequest);
            Response response = pivotCall.execute();
            ResponseBody body = response.body();
            if (body == null) {
                reportError(DownloadError.ERROR_EMPTY_RESPONSE);
                return false;
            }

            totalSize = body.contentLength();
            if (totalSize == -1) {
                String contentRange = response.header("Content-Rang");
                if (contentRange != null) {
                    int index = contentRange.lastIndexOf('/');
                    if (index != -1) {
                        String len = contentRange.substring(index);
                        try {
                            totalSize = Long.parseLong(len);
                        } catch (NumberFormatException e) {
                            totalSize = -1;
                        }
                    }
                }
            }

            resumable = response.code() == 206 || "bytes".equalsIgnoreCase(response.header("Accept-Ranges"));
            lastModified = response.header("Last-Modified");

            filename = downloadInfo.getFilename();

            if (filename == null) {
                filename = DownloadStringUtil.parseFilenameFromContentDisposition(response.header("Content-Disposition"));
                if (filename == null) filename = DownloadStringUtil.parseFilenameFromUrl(downloadInfo.getUrl());
            }


            File file = new File(parent, filename);
            downloadFile = new File(parent, filename + Constants.TMP_FILE_SUFFIX);
            int maxLength = 127 - Constants.TMP_FILE_SUFFIX.length();

            try {
                fileLock.lock();
                if (file.exists() || downloadFile.exists()) {
                    int index = filename.lastIndexOf(".");
                    String name;
                    String extendName;
                    if (index != -1) {
                        name = filename.substring(0, index);
                        extendName = filename.substring(index);
                    } else {
                        name = filename;
                        extendName = "";
                    }

                    int cnt = 1;
                    Matcher matcher = reduplicatedFilenamePattern.matcher(filename);
                    if (matcher.find() && matcher.groupCount() == 2) {
                        name = matcher.group(1);
                        cnt = Integer.parseInt(matcher.group(2));
                    }

                    StringBuilder sb = new StringBuilder();
                    while (file.exists() || downloadFile.exists()) {
                        sb.delete(0, sb.length());
                        sb.append(name).append('(').append(cnt++).append(')').append(extendName);
                        if (sb.length() > maxLength) {
                            sb.delete(0, sb.length() - maxLength);
                        }
                        filename = sb.toString();
                        Logger.w(TAG, "Task#" + downloadInfo.getId() + ": " + "file " + file.getName() +
                                " exists, " + " try " + filename);
                        file = new File(parent, filename);
                        downloadFile = new File(parent, sb.append(Constants.TMP_FILE_SUFFIX).toString());
                    }
                }
                if (!preAllocation()) {
                    reportError(DownloadError.ERROR_SPACE_FULL);
                    return false;
                }
            } finally {
                fileLock.unlock();
            }
        } catch (IOException e) {
            try {
                stateLock.lock();
                if (state != PAUSING && state != CANCELED) {
                    reportError(DownloadError.ERROR_CONNECT_FATAL);
                }
            } finally {
                stateLock.unlock();
            }
            return false;
        }

        if (!filename.equals(downloadInfo.getFilename())) {
            downloadInfo.setFilename(filename);
            downloadManager.onDownloadUpdateInfo(downloadInfo);
        }
        downloadInfo.setTotalSize(totalSize);
        downloadInfo.setLastModified(lastModified);
        PersistUtil.persistDownloadInfoQuietly(
                downloadManager.daoSession,
                downloadInfo,
                null
        );
        downloadManager.onDownloadUpdateInfo(downloadInfo);
        if (totalSize <= 0 || !resumable) {
            downloadThreadCount = 1;
        }
        return true;
    }

    // resume or start
    boolean start() {
        try {
            stateLock.lock();
            if (runLock.tryLock()) {
                try {
                    if (restart) {
                        if (state != RUNNING && state != PAUSING && state != CANCELED) {
                            this.state = PENDING;
                            stateChange();
                            DownloadExecutors.io.execute(() -> {
                                try {
                                    runLock.lock();
                                    this.execute();
                                } finally {
                                    runLock.unlock();
                                }
                            });
                            return true;
                        }
                    } else if (state != RUNNING && state != PAUSING && state != CANCELED && state != FINISH) {
                        state = PENDING;
                        stateChange();
                        DownloadExecutors.io.execute(() -> {
                            try {
                                runLock.lock();
                                this.execute();
                            } finally {
                                runLock.unlock();
                            }
                        });
                        return true;
                    }

                } finally {
                    runLock.unlock();
                }
            }
            return false;
        } finally {
            stateLock.unlock();
        }
    }

    @WorkerThread
    private void execute() {
        Logger.d(TAG, "execute: " + downloadInfo.getUrl());

        try {
            stateLock.lock();
            if (state == PENDING) {
                state = CONNECTING;
                stateChange();
                downloadManager.onDownloadConnecting(downloadInfo.getId());
            } else if (state == WAITING || handlePauseOrCancel()) {
                return;
            } else {
                Logger.e(TAG, "try to preparing but get a wrong state, " +
                        "state = " + state + "; task is:\n" + downloadInfo);
            }
        } finally {
            stateLock.unlock();
        }

        // 如果是从暂停或者错误中恢复，不需要再重试
        if ((!resuming || writeToDiskRunnables == null || downloadRunnables == null) && !initDownloadInfo()) {
            handlePauseOrCancel();
            return;
        }
        resuming = false;

        semaphore = new Semaphore(-1);
        ThreadGroup threadGroup = new ThreadGroup("task#" + downloadInfo.getId());

        startDownloadLatch = new CountDownLatch(1);
        int id = 0;
        for (WriteToDiskRunnable writeToDiskRunnable : writeToDiskRunnables) {
            Thread t = new Thread(writeToDiskRunnable, "Task#" + downloadInfo.getId() + "-Write" + id++);
            threads.add(t);
            t.start();
        }

        id = 0;
        for (DownloadRunnable downloadRunnable : downloadRunnables) {
            Thread t = new Thread(downloadRunnable, "Task#" + downloadInfo.getId() + "-Download" + id++);
            threads.add(t);
            t.start();
        }

        leftActiveThreadCount.set(downloadThreadCount * 2);
        try {
            startDownloadLatch.await();
        } catch (InterruptedException e) {
            // do nothing
        }
        startDownloadLatch = null;

        new ProgressWatcher().run();

        try {
            stateLock.lock();
            if (!deleted.get() && state == RUNNING || downloadSize.get() == downloadInfo.getTotalSize()) {
                try {
                    fileLock.lock();
                    File targetFile = new File(downloadInfo.getPath(), downloadInfo.getFilename());
                    // pause will not work now
                    if (!downloadFile.renameTo(targetFile) && !(targetFile.delete() && downloadFile.renameTo(targetFile))) {
                        reportError(DownloadError.ERROR_WRITE_FILE);
                        Logger.e(TAG, "Task#" + downloadInfo.getId() + " cannot rename file "
                                + downloadFile.getAbsolutePath() + " to " + targetFile.getAbsolutePath());
                        return;
                    }
                } finally {
                    fileLock.unlock();
                }

                if (downloadInfo.getTotalSize() <= 0) {
                    downloadInfo.setTotalSize(downloadSize.get());
                }
                state = FINISH;
                stateChange();
                downloadManager.onDownloadFinished(downloadInfo);
            } else if (state != ERROR && !handlePauseOrCancel() && !deleted.get()) {
                Logger.e(TAG, "Task#" + downloadInfo.getId() + " has a wrong state: " + state +
                        "; task is\n" + downloadInfo);
            }
        } finally {
            stateLock.unlock();
        }
    }

    private boolean handlePauseOrCancel() {
        try {
            stateLock.lock();
            if (state == PAUSING) {
                state = PAUSED;
                stateChange();
                downloadManager.onDownloadPaused(downloadInfo.getId());
                preparedForResuming();
                return true;
            } else if (state == CANCELED) {
                try {
                    fileLock.lock();
                    if (downloadFile != null && downloadFile.exists() && !downloadFile.delete()) {
                        Logger.e(TAG, "Task#" + downloadInfo.getId() + " cannot deleted download tmp file "
                                + downloadFile.getAbsolutePath() + " when canceled.");
                    }
                } finally {
                    fileLock.unlock();
                }
                return true;
            }
            return state == PAUSED || state == WAITING;
        } finally {
            stateLock.unlock();
        }
    }

    private void preparedForResuming() {
        resuming = (state == PAUSED || state == ERROR) && downloadInfo.getResumable()
                && downloadThreadInfos.size() > 0
                && writeToDiskRunnables != null && downloadRunnables != null;
    }

    @WorkerThread
    private void stateChange() {
        if (downloadInfo != null && downloadInfo.getDownloadItemState() != state) {
            downloadInfo.setDownloadItemState(state);
            if (!deleted.get()) {
                PersistUtil.persistDownloadInfoQuietly(
                        downloadManager.daoSession,
                        downloadInfo,
                        null
                );
            }
        }
    }

    void toWait(boolean restart) {
        try {
            stateLock.lock();
            if (state == WAITING) {
                return;
            }
            if (runLock.tryLock()) {
                try {
                    if (state == PENDING || state == PAUSED || state == ERROR
                            || state == FATAL_ERROR || (state == FINISH && restart)) {
                        state = WAITING;
                        this.restart = restart;
                        if (restart) {
                            resuming = false;
                            // restart time
                            downloadInfo.getCreatedTime().setTime(System.currentTimeMillis());
                            downloadInfo.setDownloadedSize(0);
                            DownloadExecutors.io.execute(() -> {
                                PersistUtil.persistDownloadInfoQuietly(downloadManager.daoSession, downloadInfo, new SparseArray<>(0));
                                PersistUtil.deleteFile(downloadInfo, true);
                                stateChange();
                            });
                        } else {
                            stateChange();
                        }
                    }
                } finally {
                    runLock.unlock();
                }
            } else if (state == RUNNING || state == CONNECTING) {
                this.restart = false;
                state = WAITING;
                stateChange();
                if (pivotCall != null) {
                    pivotCall.cancel();
                }
                if (downloadRunnables != null) {
                    for (DownloadRunnable downloadRunnable : downloadRunnables) {
                        downloadRunnable.cancelRequest();
                    }

                    Iterator<Thread> iterator = threads.iterator();
                    while (iterator.hasNext()) {
                        Thread t = iterator.next();
                        if (!t.isInterrupted()) {
                            t.interrupt();
                        }
                        iterator.remove();
                    }
                }
            }
        } finally {
            stateLock.unlock();
        }
    }

    private boolean initDownloadInfo() {
        if (!buildBaseRequest()) {
            return false;
        }
        int bufferSize = MAX_BUFFER;
        // try to recover from last download
        try {
            if (!restart) {
                List<DownloadThreadInfo> downloadThreadInfoList = downloadInfo.getDownloadThreadInfos();
                if (!downloadThreadInfoList.isEmpty()) {
                    for (DownloadThreadInfo downloadThreadInfo : downloadThreadInfoList) {
                        downloadThreadInfos.put(downloadThreadInfo.getTid(), downloadThreadInfo);
                    }
                    long tmp = 0;
                    for (int i = 0, s = downloadThreadInfos.size(); i < s; i++) {
                        DownloadThreadInfo downloadThreadInfo = downloadThreadInfos.valueAt(i);
                        if (downloadThreadInfo.getTotalSize() != -1) {
                            tmp += downloadThreadInfo.getDownloadedSize();
                        } else {
                            downloadSize.set(0);
                            tmp = -1;
                            if (downloadThreadInfos.size() != 1) {
                                // will be caught...
                                throw new IllegalStateException("need reset downloadThreadList");
                            }
                            break;
                        }
                    }
                    downloadSize.set(tmp);
                    if (downloadInfo.getDownloadedSize() != tmp) {
                        downloadInfo.setDownloadedSize(tmp);
                    }

                    if (downloadThreadInfos.size() > 0) {
                        bufferSize = chooseBufferSize(downloadInfo.getTotalSize());
                        downloadFile = new File(downloadInfo.getPath(), downloadInfo.getFilename() + Constants.TMP_FILE_SUFFIX);
                        downloadThreadCount = downloadThreadInfos.size();
                    }
                }
            } else {
                downloadThreadInfos.clear();
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace();
            }
            if (downloadThreadInfos.size() > 0) {
                downloadThreadInfos.clear();
                downloadInfo.resetDownloadThreadInfos();
            }
        }

        if (downloadThreadInfos.size() == 0 || downloadInfo.getFilename() == null) {
            downloadSize.set(0);
            // fetch last download info if possible
            if (!doPivotCall()) {
                return false;
            }

            long totalSize = downloadInfo.getTotalSize();
            if (totalSize == -1 || !downloadInfo.getResumable()) {
                // cannot resume
                // downloadTreadCount == 1
                downloadThreadInfos.put(0, new DownloadThreadInfo(
                        null, 0, 0,
                        0, -1, downloadInfo.getId()
                ));
            } else {
                long downloadLen = totalSize / downloadThreadCount;
                long lenSum = 0, last;
                for (int i = 0; i < downloadThreadCount; i++) {
                    last = lenSum;
                    lenSum = Math.min(totalSize, lenSum + downloadLen);
                    downloadThreadInfos.put(i, new DownloadThreadInfo(
                            null, i, i * downloadLen,
                            0, lenSum - last, downloadInfo.getId()));
                }
            }
        }

        writeToDiskRunnables = new WriteToDiskRunnable[downloadThreadCount];
        downloadRunnables = new DownloadRunnable[downloadThreadCount];
        for (int i = 0, s = downloadThreadInfos.size(); i < s; i++) {
            DownloadBuffer downloadBuffer = new DownloadBuffer(bufferSize);
            DownloadThreadInfo downloadThreadInfo = downloadThreadInfos.get(i);
            downloadRunnables[i] = new DownloadRunnable(
                    downloadThreadInfo.getStartPosition(),
                    downloadThreadInfo.getDownloadedSize(),
                    downloadThreadInfo.getTotalSize(),
                    downloadBuffer, downloadThreadInfos.get(i).getTid()
            );
            writeToDiskRunnables[i] = new WriteToDiskRunnable(
                    downloadThreadInfo.getStartPosition(),
                    downloadThreadInfo.getTotalSize(),
                    downloadThreadInfo.getTid(),
                    downloadBuffer);
        }
        if (!deleted.get()) {
            PersistUtil.persistDownloadInfoQuietly(downloadManager.daoSession, downloadInfo, downloadThreadInfos);
        }
        return true;
    }

    private int chooseBufferSize(long totalLen) {
        if (totalLen == -1) {
            return MAX_BUFFER;
        }
        // 选择bufferSize
        // 尽可能让进度条能多动，但下载缓冲区不至于太小或太大
        long bufferSize = totalLen / 100L;
        if (bufferSize < MIN_BUFFER) bufferSize = MIN_BUFFER;
        else bufferSize = MAX_BUFFER;

        return (int) bufferSize;
    }

    void pause() {
        try {
            stateLock.lock();
            if (runLock.tryLock()) {
                try {
                    if (state == PAUSED || state == CANCELED) {
                        return;
                    }
                    state = PAUSED;
                    stateChange();
                    downloadManager.onDownloadPaused(downloadInfo.getId());
                } finally {
                    runLock.unlock();
                }
            } else {
                if (state != RUNNING && state != CONNECTING) {
                    return;
                }
                state = PAUSING;
                stateChange();
                if (pivotCall != null) {
                    pivotCall.cancel();
                }
                if (downloadRunnables != null) {
                    for (DownloadRunnable downloadRunnable : downloadRunnables) {
                        downloadRunnable.cancelRequest();
                    }

                    Iterator<Thread> iterator = threads.iterator();
                    while (iterator.hasNext()) {
                        Thread t = iterator.next();
                        if (!t.isInterrupted()) {
                            t.interrupt();
                        }
                        iterator.remove();
                    }
                }
                downloadManager.onDownloadPausing(downloadInfo.getId());
            }
        } finally {
            stateLock.unlock();
        }
    }

    void cancel() {
        try {
            stateLock.lock();
            if (state != CANCELED) {
                state = CANCELED;
                if (pivotCall != null) {
                    pivotCall.cancel();
                }
                if (downloadRunnables != null) {
                    for (DownloadRunnable downloadRunnable : downloadRunnables) {
                        downloadRunnable.cancelRequest();
                    }
                }
                Iterator<Thread> iterator = threads.iterator();
                while (iterator.hasNext()) {
                    Thread t = iterator.next();
                    if (!t.isInterrupted()) {
                        t.interrupt();
                    }
                    iterator.remove();
                }
                stateChange();
                downloadManager.onDownloadCanceled(downloadInfo.getId());
            }
        } finally {
            stateLock.unlock();
        }
    }

    // this download task will be removed
    void delete(boolean deleteFile) {
        if (!deleted.compareAndSet(false, true)) {
            return;
        }
        try {
            if (pivotCall != null) {
                pivotCall.cancel();
            }
            if (downloadRunnables != null) {
                for (DownloadRunnable downloadRunnable : downloadRunnables) {
                    downloadRunnable.cancelRequest();
                }
            }
            Iterator<Thread> iterator = threads.iterator();
            while (iterator.hasNext()) {
                Thread t = iterator.next();
                if (!t.isInterrupted()) {
                    t.interrupt();
                }
                iterator.remove();
            }
        } catch (Exception e) {
            Logger.e("DownloadTask", "error when stop task#" + downloadInfo.getId(), e);
        } finally {
            PersistUtil.deleteFile(downloadInfo, deleteFile);
            PersistUtil.deleteDownloadInfo(downloadManager.daoSession, downloadInfo);
        }
    }

    private void reportError(int code) {
        if (deleted.get()) {
            return;
        }
        try {
            stateLock.lock();
            if (state == ERROR || state == FATAL_ERROR || (state != CONNECTING && state != RUNNING)) {
                return;
            }

            boolean fatal = downloadError.isFatal(code);
            Logger.e(TAG, "error: " + downloadError.translate(code) + "; isFatal: " + fatal +
                    "; task is\n" + downloadInfo);
            downloadInfo.setErrorCode(code);
            if (fatal) {
                state = FATAL_ERROR;
                if (downloadFile != null && downloadFile.exists() && !downloadFile.delete()) {
                    Logger.e(TAG, "cannot deleted " + downloadFile.getAbsolutePath() + " when fatal error");
                }
                downloadManager.daoSession
                        .getDownloadThreadInfoDao().deleteInTx(downloadInfo.getDownloadThreadInfos());
                downloadInfo.getDownloadThreadInfos().clear();
                stateChange();
                downloadManager.onDownloadError(downloadInfo.getId(), code, true);
            } else {
                state = ERROR;
                preparedForResuming();
                stateChange();
                downloadManager.onDownloadError(downloadInfo.getId(), code, false);
            }
        } finally {
            stateLock.unlock();
        }
    }

    private boolean preAllocation() {
        String filename = downloadInfo.getFilename();
        long totalSize = downloadInfo.getTotalSize();
        if (downloadFile == null) {
            downloadFile = new File(downloadInfo.getPath(), filename + Constants.TMP_FILE_SUFFIX);
        }

        try {
            if (downloadFile.exists() || (totalSize == -1 && downloadFile.createNewFile())) {
                return true;
            }
        } catch (IOException e) {
            return false;
        }

        if (!deleted.get()) {
            try (RandomAccessFile raf = new RandomAccessFile(downloadFile, "rw")) {
                if (!deleted.get()) {
                    raf.setLength(totalSize);
                }
            } catch (FileNotFoundException e) {
                if (BuildConfig.DEBUG) {
                    e.printStackTrace();
                }
                return false;
            } catch (IOException e) {
                if (BuildConfig.DEBUG) {
                    e.printStackTrace();
                }
                return false;
            }
        }

        return !deleted.get();
    }

    @IntDef({PENDING, CONNECTING, RUNNING, PAUSING, PAUSED, FINISH, WAITING, CANCELED, ERROR, FATAL_ERROR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DownloadState {
    }

    private boolean checkEnd() {
        try {
            stateLock.lock();
            boolean result = leftActiveThreadCount.get() <= 0 || state != RUNNING;
            if (result && !deleted.get()) {
                PersistUtil.persistDownloadInfoQuietly(
                        downloadManager.daoSession,
                        downloadInfo,
                        downloadThreadInfos
                );
            }
            return result;
        } finally {
            stateLock.unlock();
        }
    }

    private class DownloadRunnable implements Runnable {
        private long startPos;
        private long contentLen;
        private long threadDownloadedSize;
        private Call call;
        private final int retryCount = 2;
        private final DownloadBuffer downloadBuffer;
        /**
         * @see DownloadThreadInfo#getTid()
         */
        private final int id;

        DownloadRunnable(long startPos, long threadDownloadedSize, long contentLen, DownloadBuffer downloadBuffer, int id) {
            this.startPos = startPos;
            this.threadDownloadedSize = threadDownloadedSize;
            this.contentLen = contentLen;
            this.downloadBuffer = downloadBuffer;
            this.id = id;
        }

        private void cancelRequest() {
            if (call != null) {
                call.cancel();
            }
        }

        @Override
        public void run() {
            try {
                innerRun();
            } finally {
                leftActiveThreadCount.decrementAndGet();
            }
        }

        private void innerRun() {
            InputStream is = null;
            long currentPos = startPos + threadDownloadedSize;

            try {
                if (currentPos < startPos + contentLen || contentLen == -1) {
                    Request request;
                    boolean requestPartCheck = false;
                    boolean resumable = downloadInfo.getResumable();
                    if (contentLen > 0 && resumable) {
                        Builder builder = baseRequest.newBuilder()
                                .addHeader("Range", "bytes=" + currentPos + "-" + (startPos + contentLen - 1));
                        if (downloadInfo.getLastModified() != null) {
                            builder.addHeader("If-Range", downloadInfo.getLastModified()).build();
                            requestPartCheck = true;
                        }
                        request = builder.build();
                    } else if (resumable) {
                        Builder builder = baseRequest.newBuilder()
                                .addHeader("Range", "bytes=" + currentPos + "-");
                        if (downloadInfo.getLastModified() != null) {
                            builder.addHeader("If-Range", downloadInfo.getLastModified()).build();
                            requestPartCheck = true;
                        }
                        request = builder.build();
                    } else {
                        currentPos = 0;
                        downloadSize.set(0);
                        request = baseRequest;
                    }

                    boolean success = false;
                    int retryCount = this.retryCount;
                    ResponseBody body = null;
                    do {
                        if ((state != RUNNING && state != CONNECTING) || deleted.get()) {
                            return;
                        }
                        try {
                            call = client.newCall(request);
                            Response response = call.execute();
                            // TODO: 2019/4/26 auto retry if expired
                            if (requestPartCheck && response.code() == 200) {
                                reportError(DownloadError.ERROR_CONTENT_EXPIRED);
                                return;
                            }

                            if (!response.isSuccessful() || ((body = response.body()) == null))
                                continue;
                            retryCount = 0;
                            success = true;
                        } catch (IOException e) {
                            try {
                                stateLock.lock();
                                if (state == RUNNING) {
                                    Logger.e(TAG, "when connect @Task#" + downloadInfo.getId() + ", Thread#" + id, e);
                                }
                            } finally {
                                stateLock.unlock();
                            }
                        }
                    } while (retryCount-- > 0 && !deleted.get());

                    if (!success) {
                        reportError(DownloadError.ERROR_NETWORK);
                        return;
                    }

                    is = Objects.requireNonNull(body).byteStream();

                    if (state == CONNECTING) {
                        try {
                            stateLock.lock();
                            if (state == CONNECTING) {
                                state = RUNNING;
                                stateChange();
                                downloadManager.onDownloadStart(downloadInfo);
                            } else if (state != RUNNING && handlePauseOrCancel()) {
                                Logger.e(TAG, "State CONNECTING or RUNNING expected but got a wrong state, " +
                                        "state = " + state + "; task is:\n" + downloadInfo);
                            }
                        } finally {
                            stateLock.unlock();
                        }
                    }
                }
            } finally {
                if (startDownloadLatch != null) {
                    startDownloadLatch.countDown();
                }
            }

            int retryCount = this.retryCount;
            Segment segment;
            while (state == RUNNING && !deleted.get()) {
                try {
                    segment = downloadBuffer.availableWriteSegment(bufferTimeout);
                } catch (InterruptedException e) {
                    continue;
                }

                // timeout
                if (segment == null) {
                    continue;
                }

                //-----------------------segment must return to buffer!--------------------------//

                int readSize = -1;

                try {
                    if (is != null && !deleted.get()) {
                        readSize = is.read(segment.buffer);
                    }
                    segment.startPos = currentPos;
                    segment.tid = this.id;
                    segment.readSize = readSize;
                    if (readSize > 0) {
                        threadDownloadedSize += segment.readSize;
                    }
                    downloadBuffer.enqueueReadSegment(segment);
                } catch (IOException e) {
                    try {
                        stateLock.lock();
                        if (state != PAUSING && state != PAUSED && state != CANCELED && state != WAITING && retryCount-- <= 0) {
                            reportError(DownloadError.ERROR_NETWORK);
                            if (BuildConfig.DEBUG) {
                                e.printStackTrace();
                            }
                        }
                        downloadBuffer.enqueueWriteSegment(segment);
                    } finally {
                        stateLock.unlock();
                    }
                    continue;
                }

                if (readSize <= 0) {
                    break;
                }
            }
        }
    }

    private class WriteToDiskRunnable implements Runnable {
        private final int id;
        private long startPos;
        private long contentLen;
        private final int retryCount = 2;
        private final DownloadBuffer downloadBuffer;

        WriteToDiskRunnable(long startPos, long contentLen, int id, DownloadBuffer downloadBuffer) {
            this.startPos = startPos;
            this.contentLen = contentLen;
            this.id = id;
            this.downloadBuffer = downloadBuffer;
        }

        @Override
        public void run() {
            try {
                innerRun();
            } finally {
                leftActiveThreadCount.decrementAndGet();
            }
        }

        private void innerRun() {
            DownloadThreadInfo downloadThreadInfo = downloadThreadInfos.get(id);
            if (contentLen != -1 && downloadThreadInfo.getDownloadedSize() >= contentLen) {
                return;
            }
            int retryCount = this.retryCount;
            RandomAccessFile raf = null;

            while (!deleted.get()) {
                try {
                    raf = new RandomAccessFile(downloadFile, "rw");
                    raf.seek(startPos + downloadThreadInfo.getDownloadedSize());
                    break;
                } catch (IOException e) {
                    try {
                        stateLock.lock();
                        if (retryCount > 0 && (state == RUNNING || state == CONNECTING)) {
                            retryCount--;
                            continue;
                        }
                        if (BuildConfig.DEBUG) {
                            e.printStackTrace();
                        }
                        reportError(DownloadError.ERROR_WRITE_FILE);
                        return;
                    } finally {
                        stateLock.unlock();
                    }
                }
            }

            Segment segment;
            while ((state == RUNNING || state == CONNECTING) && !deleted.get() && raf != null) {
                try {
                    segment = downloadBuffer.availableReadSegment(bufferTimeout);
                } catch (InterruptedException e) {
                    continue;
                }

                // timeout
                if (segment == null) {
                    continue;
                }

                //-----------------------segment must return to buffer!--------------------------//

                int writeSize = segment.readSize;
                if (writeSize > 0 && !deleted.get()) {
                    try {
                        raf.write(segment.buffer, 0, writeSize);
                        downloadBuffer.enqueueWriteSegment(segment);
                        long downloadedSize = downloadThreadInfo.getDownloadedSize();
                        downloadThreadInfo.setDownloadedSize(downloadedSize + writeSize);
                        downloadSize.addAndGet(writeSize);
                        // inform watcher to update progress
                        semaphore.release();
                    } catch (IOException e) {
                        try {
                            stateLock.lock();
                            if (state != PAUSING && state != PAUSED && state != CANCELED && state != WAITING && retryCount-- <= 0) {
                                reportError(DownloadError.ERROR_DOWNLOAD_FAIL);
                                if (BuildConfig.DEBUG) {
                                    e.printStackTrace();
                                }
                                return;
                            }
                            downloadBuffer.enqueueReadSegment(segment);
                        } finally {
                            stateLock.unlock();
                        }
                    }
                } else {
                    break;
                }
            }
        }
    }

    private class ProgressWatcher implements Runnable {
        // ms
        private long watchInterval = 16;
        // 32ms min
        private final long minWatchInterval = 50;
        // 1s max
        private final long maxWatchInterval = 1000;
        private long lastDownloadSize;
        private long lastTimeNano;
        private double lastBps;
        private int skipTime = 0;
        private final int maxSkipTime = 3;
        private long lastDeltaSize;
        private long lastDeltaTime;

        @Override
        public void run() {
            lastTimeNano = System.nanoTime();
            lastBps = 0;
            lastDownloadSize = downloadSize.get();
            boolean tryAcquire = true;
            while (true) {
                if (checkEnd()) {
                    return;
                }

                boolean acquire = false;

                try {
                    if (tryAcquire) {
                        acquire = semaphore.tryAcquire(downloadThreadCount, watchInterval, TimeUnit.MILLISECONDS);
                    } else {
                        Thread.sleep(watchInterval);
                    }
                } catch (InterruptedException e) {
                    if (checkEnd()) {
                        return;
                    }
                    continue;
                }

                long time = System.nanoTime();
                long deltaTime = time - lastTimeNano;
                if (deltaTime < watchInterval * 1000000) {
                    watchInterval = Math.min(watchInterval - minWatchInterval, minWatchInterval);
                }

                if (checkEnd()) {
                    return;
                }

                long current = downloadSize.get();
                long downloaded = current - lastDownloadSize;
                // try to avoid 0B/s...
                // git it more time to do it
                if (downloaded < 1 && watchInterval < maxWatchInterval) {
                    if (!acquire) {
                        watchInterval = Math.min(watchInterval * 2, maxWatchInterval);
                    }
                    tryAcquire = true;
                    continue;
                }

                double bps = downloaded / (deltaTime / 1e9);
                if (bps > lastBps * 1.05) {
                    if (skipTime < maxSkipTime) {
                        watchInterval = Math.max(minWatchInterval, watchInterval - minWatchInterval);
                        skipTime++;
                        // next time sleep
                        // no need to wait
                        if (acquire) {
                            tryAcquire = false;
                        }
                        continue;
                    } else {
                        downloaded += lastDeltaSize;
                        deltaTime += lastDeltaTime;
                        bps = downloaded / (deltaTime / 1e9);
                    }
                } else if (lastBps > bps * 1.05) {
                    if (skipTime < maxSkipTime) {
                        watchInterval = Math.min(maxWatchInterval, watchInterval + minWatchInterval);
                        skipTime++;
                        tryAcquire = true;
                        continue;
                    } else {
                        downloaded += lastDeltaSize;
                        deltaTime += lastDeltaTime;
                        bps = downloaded / (deltaTime / 1e9);
                    }
                }

                if (!deleted.get()) {
                    PersistUtil.persistDownloadInfoQuietly(
                            downloadManager.daoSession,
                            downloadInfo,
                            downloadThreadInfos
                    );
                }
                downloadManager.onDownloadProgressUpdate(downloadInfo.getId(), downloadInfo.getTotalSize(), current, bps);
                System.out.println("update! " + current + "/" + downloadInfo.getTotalSize());
                lastDownloadSize = current;
                lastTimeNano = time;
                lastBps = bps;
                skipTime = 0;
                lastDeltaSize = downloaded;
                lastDeltaTime = deltaTime;
                tryAcquire = true;
            }
        }
    }
}
