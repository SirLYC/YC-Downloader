package com.lyc.downloader;

import android.util.Log;
import androidx.annotation.IntDef;
import androidx.annotation.WorkerThread;
import okhttp3.*;

import java.io.*;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final int MAX_BUFFER = 1 << 20;
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
    private long[] startPos;
    private long totalLen;
    private boolean downloadPosPresent = false;
    private boolean resumable = false;
    private WriteToDiskRunnable writeToDiskRunnable;
    private DownloadRunnable[] downloadRunnables;
    private final DownloadListener downloadListener;
    private volatile long downloadSize = 0;
    private Call pivotCall;
    private long bufferTimeout = 1;

    protected DownloadTask(String url, String path, Map<String, String> customerHeaders, OkHttpClient client, DownloadListener downloadListener) {
        this.url = url;
        this.path = path;
        this.client = client;
        this.customerHeaders = customerHeaders;
        downloadFile = new File(path + Constants.TMP_FILE_SUFFIX);
        this.downloadListener = downloadListener;
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
        // returns 206 if it supports resume
        pivotRequest = new Request.Builder()
                .url(url)
                .addHeader("Range", "bytes=0-")
                .build();
        downloadPosPresent = false;
        totalLen = -1;
        return true;
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
    public void execute() {
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

        if (resuming) {
            downloadListener.onProgressUpdate(writeToDiskRunnable.downloadedSize, totalLen);
        } else {
            downloadListener.onDownloadStart();
        }
        writeToDiskRunnable.run();
    }

    private boolean initDownloadInfo() {
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
        long bufferSize;
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

            // 选择bufferSize
            // 尽可能让进度条能多动，但下载缓冲区不至于太小或太大
            bufferSize = totalLen / 100L;
            if (bufferSize < MIN_BUFFER) {
                bufferSize = MIN_BUFFER;
            } else if (bufferSize > MAX_BUFFER) {
                bufferSize = MAX_BUFFER;
            }

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
            // single thread with out resuming
            downloadThreadCount = 1;
            bufferSize = MAX_BUFFER;
        } else {
            reportError(DownloadError.ERROR_NETWORK);
            return false;
        }

        if (!downloadPosPresent) {
            downloadSize = 0;
            startPos = new long[downloadThreadCount];
            if (totalLen == -1) {
                // cannot resume
                // downloadTreadCount == 1
                startPos[0] = 0L;
            } else {
                long downloadLen = totalLen / downloadThreadCount;
                for (int i = 0; i < downloadThreadCount; i++) {
                    startPos[i] = i * downloadLen;
                }
            }
        }

        downloadBuffer = new DownloadBuffer(downloadThreadCount, (int) bufferSize, downloadListener, 1000000000L);
        writeToDiskRunnable = new WriteToDiskRunnable(downloadSize);
        downloadRunnables = new DownloadRunnable[downloadThreadCount];
        for (int i = 0; i < downloadRunnables.length; i++) {
            long contentLen;
            if (i + 1 < downloadRunnables.length) {
                contentLen = startPos[i + 1] - startPos[i];
            } else {
                contentLen = totalLen == -1 ? -1 : totalLen - startPos[i];
            }
            downloadRunnables[i] = new DownloadRunnable(startPos[i], contentLen, i);
        }

        return true;
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
        if (s == ERROR || s == FATAL_ERROR) {
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
        if (writeToDiskRunnable != null) {
            return writeToDiskRunnable.downloadedSize;
        }

        return 0;
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
            Request request;
            if (contentLen > 0) {
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


            InputStream is = Objects.requireNonNull(body).byteStream();

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
                    segment.readSize = is.read(segment.buffer);
                    segment.startPos = currentPos;
                    if (segment.readSize > 0) {
                        currentPos += segment.readSize;
                    }
                    downloadBuffer.enqueueReadSegment(segment);
                } catch (IOException e) {
                    s = state.get();
                    if (s != PAUSING && s != PAUSED && s != CANCELED && s != CANCELLING) {
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
        private int finishCount;
        private long downloadedSize;

        WriteToDiskRunnable(long downloadedSize) {
            this.downloadedSize = downloadedSize;
        }

        @Override
        public void run() {
            if (!preAllocation()) {
                if (BuildConfig.DEBUG) {
                    Log.e(Constants.DEBUG_TAG, "preAllocation failed");
                }
                reportError(DownloadError.ERROR_SPACE_FULL);
                return;
            }

            try (RandomAccessFile raf = new RandomAccessFile(downloadFile, "rw")) {
                while (finishCount < downloadThreadCount) {
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

                    if (segment.readSize <= 0) {
                        finishCount++;
                    } else {
                        try {
                            raf.seek(segment.startPos);
                            raf.write(segment.buffer, 0, segment.readSize);
                            downloadedSize += segment.readSize;
                            downloadListener.onProgressUpdate(downloadedSize, totalLen);
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
