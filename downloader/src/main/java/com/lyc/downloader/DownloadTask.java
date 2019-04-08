package com.lyc.downloader;

import android.util.Log;
import androidx.annotation.IntDef;
import androidx.annotation.WorkerThread;
import okhttp3.*;

import java.io.*;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
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
    public static final int CONNECTING = 2;
    public static final int RUNNING = 3;
    public static final int PAUSE = 4;
    public static final int FINISH = 5;
    public static final int WAITING = 6;
    public static final int CANCELED = 7;
    public static final int ERROR = 8;

    // int bytes
    private static final int MAX_BUFFER = 1 << 20;
    private static final int MIN_BUFFER = 4 * (2 << 10);

    private static final int MAX_DOWNLOAD_THREAD = 4;

    protected final String url;
    protected final String path;
    protected final Map<String, String> customerHeaders;
    private final AtomicInteger state = new AtomicInteger(PENDING);
    private List<Request> requests;
    private Request.Builder RequestBuilder;
    private File downloadFile;
    private int downloadThreadCount = 1;
    private String fileName;
    private OkHttpClient client;
    private Request pivotRequest;
    private DownloadBuffer downloadBuffer;
    private int bufferSize;
    private long[] startPos;
    private long[] nextPos;
    private long totalLen;
    // defined by manager
    private int retryCount = 1;
    private boolean downloadPosPresent = false;
    private boolean resumable = false;
    private ReentrantLock lock = new ReentrantLock();
    private Condition resume = lock.newCondition();
    private WriteToDiskRunnable writeToDiskRunnable;
    private DownloadRunnable[] downloadRunnables;


    protected DownloadTask(String url, String path, Map<String, String> customerHeaders, OkHttpClient client) {
        this.url = url;
        this.path = path;
        this.client = client;
        this.customerHeaders = customerHeaders;
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

    public boolean prepare() {
        File targetFile = new File(path);
        if (targetFile.exists()) {
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

    @WorkerThread
    public boolean execute() throws IOException {
        int s = state.get();
        boolean resuming = s == PAUSE;
        if (s != PREPARING) {
            state.compareAndSet(s, PREPARING);
            if (!prepare())
                throw new IllegalStateException("prepare failed!");

            if (!resuming) {
                initDownloadInfo();
            }
        }

        s = state.get();
        if (s == RUNNING) {
            throw new IllegalStateException("current state is running");
        }
        if (!state.compareAndSet(s, RUNNING)) {
            throw new IllegalStateException("current state(try to connect) : " + state.get());
        }

        for (DownloadRunnable downloadRunnable : downloadRunnables) {
            DownloadExecutors.io.execute(downloadRunnable);
        }

        writeToDiskRunnable.run();

        return state.get() == FINISH;
    }

    private void initDownloadInfo() throws IOException {
        // fetch last download info if possible
        Response response = client.newCall(pivotRequest).execute();
        resumable = response.isSuccessful();
        if (resumable) {
            ResponseBody body = response.body();
            if (body == null) {
                throw new NullPointerException("nothing available for download");
            }
            // multi thread with resuming
            totalLen = body.contentLength();
            long bufferSize = totalLen / 100L;
            if (bufferSize < MIN_BUFFER) {
                bufferSize = MIN_BUFFER;
            } else if (bufferSize > MAX_BUFFER) {
                bufferSize = MAX_BUFFER;
            }
            this.bufferSize = (int) bufferSize;

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
            startPos = new long[downloadThreadCount];
            nextPos = new long[downloadThreadCount];
            if (totalLen == -1) {
                // cannot resume
                // downloadTreadCount == 1
                startPos[0] = nextPos[0] = 0L;
            } else {
                long downloadLen = totalLen / downloadThreadCount;
                for (int i = 0; i < downloadThreadCount; i++) {
                    startPos[i] = nextPos[i] = i * downloadLen;
                }
            }
        }

        downloadBuffer = new DownloadBuffer(downloadThreadCount, bufferSize);
        writeToDiskRunnable = new WriteToDiskRunnable();
        downloadRunnables = new DownloadRunnable[downloadThreadCount];
        for (int i = 0; i < downloadRunnables.length; i++) {
            long contentLen;
            if (i + 1 < downloadRunnables.length) {
                contentLen = startPos[i + 1] - startPos[i];
            } else {
                contentLen = totalLen == -1 ? -1 : totalLen - startPos[i];
            }
            downloadRunnables[i] = new DownloadRunnable(startPos[i], contentLen);
        }
    }

    public void pause() {
        if (downloadRunnables != null) {

        }
    }

    public void resume() {

    }

    public void cancel() {
        int s = state.get();
        if (s != CANCELED) {
            state.compareAndSet(s, CANCELED);
        }
    }

    private void reportError(String info) {
        int s = state.get();
        if (s != ERROR && state.compareAndSet(s, ERROR)) {
            // TODO: 2019/4/7 dispatch error information here
            if (BuildConfig.DEBUG) {
                Log.e(Constants.DEBUG_TAG, info);
            }
        }
    }

    private boolean preAllocation() {
        if (downloadFile == null) throw new NullPointerException("file not be null");
        if (downloadFile.exists() || totalLen == -1) return true;

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

    @IntDef({PENDING, PREPARING, CONNECTING, RUNNING, PAUSE, FINISH, WAITING, CANCELED, ERROR})
    @Retention(RetentionPolicy.SOURCE)
    @interface DownloadState {
    }

    class DownloadRunnable implements Runnable {
        private final long startPos;
        private final long contentLen;
        private long currentPos;
        private Call call;
        private int retryCount = 2;

        DownloadRunnable(long startPos, long contentLen) {
            this.startPos = startPos;
            this.contentLen = contentLen;
            currentPos = startPos;
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
                request = RequestBuilder.addHeader("Range", "bytes=" + startPos + "-" + (startPos + contentLen - 1)).build();
            } else {
                request = RequestBuilder.addHeader("Range", "bytes=" + startPos + "-").build();
            }

            boolean success = false;
            ResponseBody body = null;
            do {
                int s = state.get();
                if (s != CONNECTING) {
                    return;
                }
                try {
                    call = client.newCall(request);
                    Response response = call.execute();
                    if (!response.isSuccessful() || ((body = response.body()) == null) || body.contentLength() != contentLen)
                        continue;
                    retryCount = 0;
                    success = true;
                } catch (IOException e) {
                    if (BuildConfig.DEBUG) {
                        e.printStackTrace();
                    }
                    s = state.get();
                    if (s != CONNECTING) {
                        return;
                    }
                }
            } while (retryCount-- > 0);

            if (!success) {
                reportError(Constants.ERROR_CONNECT_FAILL);
                return;
            }


            InputStream is = Objects.requireNonNull(body).byteStream();

            Segment segment;
            for (; ; ) {
                int s = state.get();
                if (s == PAUSE || s == CANCELED || s == ERROR) {
                    return;
                }
                try {
                    segment = downloadBuffer.availableWriteSegment();
                } catch (InterruptedException e) {
                    if (BuildConfig.DEBUG) {
                        e.printStackTrace();
                    }
                    continue;
                }
                try {
                    segment.readSize = is.read(segment.buffer);
                    segment.startPos = currentPos;
                    downloadBuffer.enqueueReadSegment(segment);
                } catch (IOException e) {
                    reportError(Constants.ERROR_DOWNLOAD_FAIL);
                    if (BuildConfig.DEBUG) {
                        e.printStackTrace();
                    }
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
                    Segment segment = null;
                    int s = state.get();

                    if (s == PAUSE) {
                        if (resumable) {
                            try {
                                resume.await();
                            } catch (InterruptedException e) {
                                continue;
                            }
                        } else {
                            state.compareAndSet(s, CANCELED);
                            return;
                        }
                    } else if (s == CANCELED || s == ERROR) {
                        return;
                    } else if (s != RUNNING) {
                        if (BuildConfig.DEBUG) {
                            reportError("未知错误");
                            return;
                        }
                    }

                    try {
                        segment = downloadBuffer.availableReadSegment();
                    } catch (InterruptedException e) {
                        if (state.get() == PAUSE) {
                            continue;
                        }
                    }

                    if (segment == null) {
                        reportError(Constants.ERROR_DOWNLOAD_FAIL);
                        continue;
                    }

                    if (segment.bufferSize <= 0) {
                        finishCount++;
                    } else {
                        raf.seek(segment.startPos);
                        raf.write(segment.buffer, 0, segment.readSize);
                    }
                    // TODO: 2019/4/7 update database here
                    downloadBuffer.enqueueWriteSegment(segment);
                }
            } catch (FileNotFoundException e) {
                reportError(Constants.ERROR_WRITE_FILE);
            } catch (IOException e) {
                reportError(Constants.ERROR_WRITE_FILE);
            }

            if (state.get() == RUNNING && !downloadFile.renameTo(new File(path))) {
                if (BuildConfig.DEBUG) {
                    Log.e(Constants.DEBUG_TAG, "rename failed");
                }
            }
        }
    }
}
