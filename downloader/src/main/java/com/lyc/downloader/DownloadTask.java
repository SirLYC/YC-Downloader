package com.lyc.downloader;

import android.util.Log;
import androidx.annotation.IntDef;
import androidx.annotation.WorkerThread;
import com.lyc.downloader.db.CustomerHeader;
import com.lyc.downloader.db.CustomerHeaderDao;
import com.lyc.downloader.db.DaoSession;
import com.lyc.downloader.db.DownloadInfo;
import com.lyc.downloader.db.DownloadInfoDao;
import com.lyc.downloader.db.DownloadItemState;
import com.lyc.downloader.db.DownloadThreadInfo;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author liuyuchuan
 * @date 2019/4/1
 * @email kevinliu.sir@qq.com
 */
// TODO 2019/4/10 @liuyuchuan: 处理服务器文件发生改变的情况
public class DownloadTask {

    public static final int PENDING = 0;
    public static final int PREPARING = 1;
    public static final int RUNNING = 2;
    public static final int PAUSING = 3;
    public static final int PAUSED = 4;
    public static final int FINISH = 5;
    public static final int WAITING = 6;
    public static final int CANCELLING = 7;
    public static final int CANCELED = 8;
    public static final int ERROR = 9;
    // this state should not be resumed
    public static final int FATAL_ERROR = 10;

    // in bytes
    private static final int MAX_BUFFER = 1 << 16;
    private static final int MIN_BUFFER = 4 * (2 << 10);

    private static final int MAX_DOWNLOAD_THREAD = 4;

    public final String url;
    public final String path;
    private final Map<String, String> customerHeaders;
    private final AtomicInteger state = new AtomicInteger(PENDING);
    private final DownloadError downloadError = DownloadError.instance();
    private Request.Builder RequestBuilder;
    private File downloadFile;
    private int downloadThreadCount = 1;
    private String fileName;
    private OkHttpClient client;
    private Request pivotRequest;
    private DownloadBuffer downloadBuffer;
    private long totalLen;
    private boolean resumable = false;
    private WriteToDiskRunnable writeToDiskRunnable;
    private DownloadRunnable[] downloadRunnables;
    private final DownloadInfo downloadInfo;
    private final DownloadListener downloadListener;
    private DownloadThreadInfo[] downloadThreadInfos;
    private Call pivotCall;
    private long bufferTimeout = 1;
    private AtomicLong downloadSize = new AtomicLong(0);

    DownloadTask(String url, String path, Map<String, String> customerHeaders, OkHttpClient client, DownloadListener downloadListener) {
        this.url = url;
        this.path = path;
        this.client = client;
        this.customerHeaders = customerHeaders;
        this.downloadListener = downloadListener;
        this.downloadInfo = new DownloadInfo(null, url, path, DownloadItemState.DOWNLOADING);
        setup();
    }

    /**
     * only used by {@link DownloadManager} to recover from database when start
     */
    @WorkerThread
    DownloadTask(DownloadInfo downloadInfo, OkHttpClient client, DownloadListener downloadListener) {
        this.downloadInfo = downloadInfo;
        this.downloadListener = downloadListener;
        this.url = downloadInfo.getUrl();
        this.path = downloadInfo.getPath();
        this.client = client;
        Map<String, String> headers = new HashMap<>();
        for (CustomerHeader customerHeader : downloadInfo.getCustomerHeaders()) {
            headers.put(customerHeader.getKey(), customerHeader.getValue());
        }
        this.customerHeaders = headers;
        setup();
    }

    private void setup() {
        downloadFile = new File(path + Constants.TMP_FILE_SUFFIX);
        int index = path.lastIndexOf('/');
        if (index == -1) {
            throw new IllegalArgumentException("path should be absolute path");
        }

        fileName = path.substring(index + 1);
        if (fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("filename should not be empty");
        }
        downloadFile = new File(path + Constants.TMP_FILE_SUFFIX);
    }

    public @DownloadState
    int getState() {
        return state.get();
    }

    private boolean prepare() {
        File targetFile = new File(path);
        if (targetFile.exists()) {
            reportError(DownloadError.ERROR_FILE_EXITS);
            return false;
        }

        File dir = targetFile.getParentFile();
        if (dir == null || (!dir.exists() && !dir.mkdirs())) {
            reportError(DownloadError.ERROR_CREATE_DIR);
            return false;
        }

        if (downloadInfo.getId() == null) {
            if (!persistDownloadInfo()) {
                reportError(DownloadError.ERROR_CREATE_TASK);
                return false;
            }
        }

        RequestBuilder = new Request.Builder();
        if (customerHeaders != null && !customerHeaders.isEmpty()) {
            Set<String> keys = customerHeaders.keySet();
            for (String s : keys) {
                String value = customerHeaders.get(s);
                if (value != null) {
                    RequestBuilder.addHeader(s, value);
                }
            }
        }
        RequestBuilder.url(url);
        // returns 206 if it supports resume or 200 not support
        pivotRequest = new Request.Builder()
                .url(url)
                .addHeader("If-Range", "bytes=0-")
                .build();
        totalLen = -1;
        return true;
    }

    private boolean persistDownloadInfo() {
        DaoSession daoSession = DownloadManager.instance().daoSession;
        try {
            daoSession.runInTx(() -> {
                DownloadInfoDao downloadInfoDao = daoSession.getDownloadInfoDao();
                CustomerHeaderDao customerHeaderDao = daoSession.getCustomerHeaderDao();
                long id = downloadInfoDao.insert(downloadInfo);
                if (id != -1) {
                    for (String key : customerHeaders.keySet()) {
                        String value = customerHeaders.get(key);
                        if (value != null) {
                            customerHeaderDao.insert(new CustomerHeader(null, id, key, value));
                        }
                    }
                }
            });
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace();
            }
            return false;
        }
        Long id = downloadInfo.getId();

        return id != null && id != -1;
    }

    // resume or start
    public boolean start(boolean restart) {
        int s = state.get();
        if (restart) {
            if (s != RUNNING && s != PAUSING && s != CANCELLING && state.compareAndSet(s, PENDING)) {
                DownloadExecutors.io.execute(this::execute);
                return true;
            }
        } else {
            if (s != RUNNING && s != PAUSING && s != CANCELLING) {
                DownloadExecutors.io.execute(this::execute);
                return true;
            }
        }

        return false;
    }

    @WorkerThread
    void execute() {
        if (BuildConfig.DEBUG) {
            Log.d(Constants.DEBUG_TAG, "execute: " + url);
        }
        int s = state.get();
        // 如果是从暂停或者错误中恢复，不需要再重试
        boolean resuming = (s == PAUSED || s == ERROR) && resumable;
        if (!resuming && s != PREPARING) {
            state.compareAndSet(s, PREPARING);
            if (!prepare())
                return;

            if (!initDownloadInfo()) {
                return;
            }
        }

        downloadListener.onPrepared();
        s = state.get();
        if (s == RUNNING) {
            throw new IllegalStateException("current state is running");
        }
        if (!state.compareAndSet(s, RUNNING)) {
            throw new IllegalStateException("current state(try to connect) : " + state.get());
        }

        downloadBuffer.lastUpdateSpeedTime = System.nanoTime();

        for (DownloadRunnable downloadRunnable : downloadRunnables) {
            DownloadExecutors.io.execute(downloadRunnable);
        }

        // reuse this thread
        writeToDiskRunnable.run();
    }

    private boolean initDownloadInfo() {
        // try to recover from last download
        long totalLen = -1;
        int bufferSize = MAX_BUFFER;
        try {
            List<DownloadThreadInfo> downloadThreadInfoList = downloadInfo.getDownloadThreadInfos();
            if (!downloadThreadInfoList.isEmpty()) {
                downloadThreadInfos = new DownloadThreadInfo[downloadThreadInfoList.size()];
                downloadThreadInfoList.toArray(downloadThreadInfos);
                Arrays.sort(downloadThreadInfos);
                long tmp = 0;
                for (DownloadThreadInfo downloadThreadInfo : downloadThreadInfos) {
                    if (downloadThreadInfo.getTotalSize() != -1) {
                        tmp += downloadThreadInfo.getTotalSize();
                        downloadSize.addAndGet(downloadThreadInfo.getDownloadedSize());
                    } else {
                        downloadSize.set(0);
                        tmp = -1;
                        if (downloadThreadInfos.length != 1) {
                            DownloadManager.instance().daoSession.getDownloadThreadInfoDao().deleteInTx(downloadThreadInfos);
                            downloadThreadInfos = null;
                            downloadInfo.resetDownloadThreadInfos();
                        }
                        break;
                    }
                }

                if (downloadThreadInfos != null) {
                    totalLen = tmp;
                    bufferSize = chooseBufferSize(totalLen);
                    downloadThreadCount = downloadThreadInfos.length;
                }
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace();
            }
            if (downloadThreadInfos != null) {
                DownloadManager.instance().daoSession.getDownloadThreadInfoDao().deleteInTx(downloadThreadInfos);
                downloadThreadInfos = null;
                downloadInfo.resetDownloadThreadInfos();
            }
            downloadSize.set(0);
        }

        if (downloadThreadInfos == null) {
            // fetch last download info if possible
            Response response;
            try {
                pivotCall = client.newCall(pivotRequest);
                response = pivotCall.execute();
            } catch (IOException e) {
                if (BuildConfig.DEBUG) {
                    e.printStackTrace();
                }
                if (pivotCall != null) {
                    pivotCall.cancel();
                    pivotCall = null;
                }
                reportError(DownloadError.ERROR_NETWORK);
                return false;
            }

            pivotCall = null;
            resumable = response.code() == 206;
            if (resumable) {
                ResponseBody body = response.body();
                if (body == null) {
                    throw new NullPointerException("nothing available for download");
                }
                // multi thread with resuming
                totalLen = body.contentLength();
                if (totalLen == -1) {
                    String contentRange = response.header("Content-Rang");
                    if (contentRange != null) {
                        int index = contentRange.lastIndexOf('/');
                        if (index != -1) {
                            String len = contentRange.substring(index);
                            try {
                                totalLen = Long.parseLong(len);
                            } catch (NumberFormatException e) {
                                totalLen = -1;
                            }
                        }
                    }
                }

                bufferSize = chooseBufferSize(totalLen);

                // 选择合适的线程数，每个线程能多做事
                // 如果每个线程做的事太少了，减少线程数
                int threadCnt;
                for (threadCnt = MAX_DOWNLOAD_THREAD; threadCnt >= 1; threadCnt--) {
                    int readCnt = 100 / threadCnt;
                    if (readCnt * bufferSize < totalLen) {
                        break;
                    }
                }
                downloadThreadCount = threadCnt;
            } else if (response.isSuccessful()) {
                // single thread without resuming
                downloadThreadCount = 1;
            } else {
                reportError(DownloadError.ERROR_NETWORK);
                return false;
            }

            downloadSize.set(0);
            downloadThreadInfos = new DownloadThreadInfo[downloadThreadCount];
            if (totalLen == -1) {
                // cannot resume
                // downloadTreadCount == 1
                downloadThreadInfos[0] = new DownloadThreadInfo(
                        null, 0, 0,
                        0, -1, downloadInfo.getId()
                );
            } else {
                long downloadLen = totalLen / downloadThreadCount;
                long lenSum = 0, last;
                for (int i = 0; i < downloadThreadCount; i++) {
                    last = lenSum;
                    lenSum += Math.min(totalLen, lenSum + downloadLen);
                    downloadThreadInfos[i] = new DownloadThreadInfo(
                            null, i, i * downloadLen,
                            0, lenSum - last, downloadInfo.getId()
                    );
                }
            }
        }

        this.totalLen = totalLen;
        downloadBuffer = new DownloadBuffer(downloadThreadCount, bufferSize, downloadListener, 1000000000L);
        writeToDiskRunnable = new WriteToDiskRunnable();
        downloadRunnables = new DownloadRunnable[downloadThreadCount];
        for (int i = 0; i < downloadThreadInfos.length; i++) {
            downloadRunnables[i] = new DownloadRunnable(downloadThreadInfos[i].getStartPosition(),
                    downloadThreadInfos[i].getTotalSize(),
                    downloadThreadInfos[i].getTid());
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

    public void pause() {
        if (state.compareAndSet(RUNNING, PAUSING) || state.compareAndSet(PREPARING, PAUSING)) {
            if (downloadRunnables != null) {
                for (DownloadRunnable downloadRunnable : downloadRunnables) {
                    downloadRunnable.cancelRequest();
                }
            }
            downloadListener.onDownloadPausing();
        }
    }

    public void cancel() {
        int s = state.get();
        if (s == PAUSED && state.compareAndSet(PAUSED, CANCELED)) {
            downloadListener.onDownloadCanceled();
        }
        if (s != CANCELLING && s != PREPARING && s != PAUSING && s != FINISH && s != CANCELED) {
            state.compareAndSet(s, CANCELLING);
            if (downloadRunnables != null) {
                for (DownloadRunnable downloadRunnable : downloadRunnables) {
                    downloadRunnable.cancelRequest();
                }
            }
            downloadListener.onDownloadCancelling();
        }
    }

    private void reportError(int code) {
        int s = state.get();
        if (s == ERROR) {
            return;
        }

        if (s == FATAL_ERROR) {
            DownloadManager.instance().daoSession
                    .getDownloadThreadInfoDao().deleteInTx(downloadInfo.getDownloadThreadInfos());
            downloadInfo.getDownloadThreadInfos().clear();
            downloadThreadInfos = null;
            return;
        }

        boolean fatal = downloadError.isFatal(code);
        if (!fatal && state.compareAndSet(s, ERROR)) {
            downloadListener.onDownloadError(downloadError.translate(code), false);
        } else if (fatal && state.compareAndSet(s, FATAL_ERROR)) {
            downloadListener.onDownloadError(downloadError.translate(code), true);
        }
    }

    private boolean preAllocation() {
        if (downloadFile == null) {
            reportError(DownloadError.ERROR_WRITE_FILE);
            return false;
        }
        if (downloadFile.exists() || totalLen == -1) {
            return true;
        }

        try (RandomAccessFile raf = new RandomAccessFile(downloadFile, "rw")) {
            raf.setLength(totalLen);
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

        return true;
    }

    public long downloadedSize() {
        return downloadSize.get();
    }

    @IntDef({PENDING, PREPARING, RUNNING, PAUSING, PAUSED, FINISH, WAITING, CANCELLING, CANCELED, ERROR})
    @Retention(RetentionPolicy.SOURCE)
    @interface DownloadState {
    }

    class DownloadRunnable implements Runnable {
        private final long startPos;
        private final long contentLen;
        private long currentPos;
        private Call call;
        private final int retryCount = 2;
        /**
         * @see DownloadThreadInfo#tid
         */
        private final int id;

        DownloadRunnable(long startPos, long contentLen, int id) {
            this.startPos = startPos;
            this.contentLen = contentLen;
            currentPos = startPos;
            this.id = id;
        }

        private void cancelRequest() {
            if (call != null) {
                call.cancel();
            }
        }

        @Override
        public void run() {
            InputStream is = null;

            if (currentPos < startPos + contentLen) {
                Request request;
                if (contentLen > 0 && resumable) {
                    request = RequestBuilder.addHeader("Range", "bytes=" + currentPos + "-" + (startPos + contentLen - 1)).build();
                } else if (resumable) {
                    request = RequestBuilder.addHeader("Range", "bytes=" + currentPos + "-").build();
                } else {
                    currentPos = 0;
                    request = RequestBuilder.build();
                }

                boolean success = false;
                int retryCount = this.retryCount;
                ResponseBody body = null;
                do {
                    int s = state.get();
                    if (s != RUNNING) {
                        return;
                    }
                    try {
                        call = client.newCall(request);
                        Response response = call.execute();
                        if (!response.isSuccessful() || ((body = response.body()) == null))
                            continue;
                        retryCount = 0;
                        success = true;
                    } catch (IOException e) {
                        if (BuildConfig.DEBUG) {
                            e.printStackTrace();
                        }
                        s = state.get();
                        if (s != RUNNING) {
                            return;
                        }
                    }
                } while (retryCount-- > 0);

                if (!success) {
                    if (BuildConfig.DEBUG) {
                        Log.d(Constants.DEBUG_TAG, "connect fail " + "id : " + id + " retry count " + retryCount);
                    }
                    reportError(DownloadError.ERROR_NETWORK);
                    return;
                }


                is = Objects.requireNonNull(body).byteStream();
            }

            int retryCount = this.retryCount;
            Segment segment;
            for (; ; ) {
                int s = state.get();
                if (s != RUNNING) {
                    return;
                }
                try {
                    segment = downloadBuffer.availableWriteSegment(bufferTimeout);
                } catch (InterruptedException e) {
                    if (BuildConfig.DEBUG) {
                        e.printStackTrace();
                    }
                    continue;
                }

                // timeout
                if (segment == null) {
                    continue;
                }

                try {
                    if (is != null) {
                        segment.readSize = is.read(segment.buffer);
                    } else {
                        // finished
                        segment.readSize = -1;
                    }
                    segment.startPos = currentPos;
                    segment.tid = this.id;
                    if (segment.readSize > 0) {
                        currentPos += segment.readSize;
                    }
                    downloadBuffer.enqueueReadSegment(segment);
                } catch (IOException e) {
                    s = state.get();
                    if (s != PAUSING && s != PAUSED && s != CANCELED && s != CANCELLING && retryCount-- <= 0) {
                        reportError(DownloadError.ERROR_DOWNLOAD_FAIL);
                        if (BuildConfig.DEBUG) {
                            e.printStackTrace();
                        }
                    }
                    downloadBuffer.enqueueWriteSegment(segment);
                    continue;
                }

                if (segment.readSize <= 0) {
                    break;
                }
            }
        }
    }

    class WriteToDiskRunnable implements Runnable {
        private final Set<Integer> finishThreads = new HashSet<>();

        @Override
        public void run() {
            downloadListener.onProgressUpdate(downloadSize.get(), totalLen);
            if (!preAllocation()) {
                if (BuildConfig.DEBUG) {
                    Log.e(Constants.DEBUG_TAG, "preAllocation failed");
                }
                reportError(DownloadError.ERROR_SPACE_FULL);
                return;
            }

            try (RandomAccessFile raf = new RandomAccessFile(downloadFile, "rw")) {
                while (finishThreads.size() < downloadThreadCount) {
                    Segment segment;
                    int s = state.get();

                    if (s == PAUSING) {
                        if (state.compareAndSet(PAUSING, PAUSED)) {
                            downloadListener.onDownloadPaused();
                        }
                        return;
                    } else if (s == CANCELLING) {
                        if (state.compareAndSet(CANCELLING, CANCELED)) {
                            downloadListener.onDownloadCanceled();
                        }
                        return;
                    } else if (s >= ERROR) {
                        return;
                    } else if (s != RUNNING) {
                        if (BuildConfig.DEBUG) {
                            reportError(DownloadError.ERROR_DOWNLOAD_FAIL);
                            return;
                        }
                    }

                    try {
                        segment = downloadBuffer.availableReadSegment(bufferTimeout);
                    } catch (InterruptedException e) {
                        continue;
                    }

                    // timeout
                    if (segment == null) {
                        continue;
                    }

                    int tid = -1;
                    long downloaded = -1;
                    if (segment.readSize <= 0) {
                        finishThreads.add(segment.tid);
                    } else {
                        try {
                            raf.seek(segment.startPos);
                            raf.write(segment.buffer, 0, segment.readSize);
                            tid = segment.tid;
                            downloaded = segment.readSize;
                            downloadSize.addAndGet(segment.readSize);
                            downloadListener.onProgressUpdate(downloadSize.get(), totalLen);
                        } catch (IOException e) {
                            reportError(DownloadError.ERROR_WRITE_FILE);
                            if (BuildConfig.DEBUG) {
                                e.printStackTrace();
                            }
                            downloadBuffer.enqueueWriteSegment(segment);
                            continue;
                        }
                    }
                    // TODO: 2019/4/7 update database here
                    downloadBuffer.enqueueWriteSegment(segment);
                    if (tid != -1 && downloaded != -1) {
                        DownloadThreadInfo downloadThreadInfo = downloadThreadInfos[tid];
                        long newSize = downloadThreadInfo.getDownloadedSize() + downloaded;
                        downloadThreadInfo.setDownloadedSize(newSize);
                    }
                }
            } catch (FileNotFoundException e) {
                reportError(DownloadError.ERROR_WRITE_FILE);
                if (BuildConfig.DEBUG) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                reportError(DownloadError.ERROR_WRITE_FILE);
                if (BuildConfig.DEBUG) {
                    e.printStackTrace();
                }
            }

            int s = state.get();
            if (s == RUNNING && downloadFile.renameTo(new File(path))) {
                if (state.compareAndSet(s, FINISH)) {
                    downloadListener.onDownloadFinished();
                }
            } else {
                reportError(DownloadError.ERROR_DOWNLOAD_FAIL);
            }
        }
    }
}
