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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author liuyuchuan
 * @date 2019/4/1
 * @email kevinliu.sir@qq.com
 */
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

    // int bytes
    private static final int MAX_BUFFER = 1 << 20;
    private static final int MIN_BUFFER = 4 * (2 << 10);

    private static final int MAX_DOWNLOAD_THREAD = 4;

    protected final String url;
    protected final String path;
    protected final Map<String, String> customerHeaders;
    private final AtomicInteger state = new AtomicInteger(PENDING);
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
    private ReentrantLock lock = new ReentrantLock();
    private Condition resume = lock.newCondition();
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
            reportError(Constants.ERROR_EXIST);
            return false;
        }


        File dir = targetFile.getParentFile();
        if (dir == null || (!dir.exists() && !dir.mkdirs())) {
            reportError(Constants.ERROR_CREATED_DIR);
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
    private void execute() {
        if (BuildConfig.DEBUG) {
            Log.d(Constants.DEBUG_TAG, "execute: " + url);
        }
        int s = state.get();
        boolean resuming = (s == PAUSED || s == ERROR) && resumable;
        if (!resuming && s != PREPARING) {
            state.compareAndSet(s, PREPARING);
            if (!prepare())
                return;

            if (!initDownloadInfo()) {
                reportError(Constants.ERROR_DOWNLOAD_FAIL);
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

        downloadListener.onDownloadStart();
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
            return false;
        }

        pivotCall = null;
        resumable = response.isSuccessful();
        long bufferSize;
        if (resumable) {
            ResponseBody body = response.body();
            if (body == null) {
                throw new NullPointerException("nothing available for download");
            }
            // multi thread with resuming
            totalLen = body.contentLength();
            bufferSize = totalLen / 100L;
            if (bufferSize < MIN_BUFFER) {
                bufferSize = MIN_BUFFER;
            } else if (bufferSize > MAX_BUFFER) {
                bufferSize = MAX_BUFFER;
            }

            int threadCnt;
            for (threadCnt = MAX_DOWNLOAD_THREAD; threadCnt >= 1; threadCnt--) {
                int readCnt = 100 / threadCnt;
                if (readCnt * bufferSize < totalLen) {
                    break;
                }
            }
            downloadThreadCount = threadCnt;
        } else {
            // single thread with out resuming
            downloadThreadCount = 1;
            bufferSize = MAX_BUFFER;
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

    private void reportError(String info) {
        int s = state.get();
        if (s != ERROR && state.compareAndSet(s, ERROR)) {
            // TODO: 2019/4/7 dispatch error information here
            if (BuildConfig.DEBUG) {
                Log.e(Constants.DEBUG_TAG, info);
            }
            downloadListener.onDownloadError(info);
        }
    }

    private boolean preAllocation() {
        if (downloadFile == null) {
            reportError(Constants.ERROR_WRITE_FILE);
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
                reportError(Constants.ERROR_CONNECT);
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
                        reportError(Constants.ERROR_DOWNLOAD_FAIL);
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
                reportError(Constants.ERROR_SPACE_FULL);
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
                    } else if (s == ERROR) {
                        return;
                    } else if (s != RUNNING) {
                        if (BuildConfig.DEBUG) {
                            reportError("未知错误");
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
                            reportError(Constants.ERROR_WRITE_FILE);
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
                reportError(Constants.ERROR_WRITE_FILE);
                if (BuildConfig.DEBUG) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                reportError(Constants.ERROR_WRITE_FILE);
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
                reportError(Constants.ERROR_DOWNLOAD_FAIL);
            }
        }
    }
}
