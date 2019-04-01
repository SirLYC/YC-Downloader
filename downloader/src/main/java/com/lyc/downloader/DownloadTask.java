package com.lyc.downloader;

import androidx.annotation.IntDef;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author liuyuchuan
 * @date 2019/4/1
 * @email kevinliu.sir@qq.com
 */
public class DownloadTask {

    public static final int PENDING = 0;
    public static final int PREPARED = 1;
    public static final int CONNECTING = 2;
    public static final int RUNNING = 3;
    public static final int PAUSE = 4;
    public static final int FINISH = 5;
    public static final int WAITING = 6;
    protected final String url;
    protected final String path;
    protected final Map<String, String> customerHeaders;
    protected int state;
    private Request.Builder builder;
    private List<Request> requests;
    private Executor excutor;
    private File downloadFile;
    private int downloadThreadCount = 1;
    private String fileName;
    private OkHttpClient client;

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

    public int getState() {
        return state;
    }

    public boolean prepare() {
        excutor = Executors.newCachedThreadPool(r -> new Thread(r, "DownloadTaskThread: " + url));
        File targetFile = new File(path);
        if (targetFile.exists()) {
            return false;
        }
        builder = new Request.Builder();
        if (customerHeaders != null && !customerHeaders.isEmpty()) {
            Set<String> keys = customerHeaders.keySet();
            for (String s : keys) {
                String value = customerHeaders.get(s);
                if (value != null) {
                    builder.addHeader(s, value);
                }
            }
        }
        builder.url(url);
        return true;
    }

    private void preAllocation(File file, int length) {
        if (file == null) throw new NullPointerException("file not be null");
        if (file.exists() || length == -1) return;

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(length);
        } catch (FileNotFoundException e) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace();
            }
        }
    }

    public void start() {

    }

    public void pause() {

    }

    public void resume() {

    }

    public void cancel() {

    }

    public boolean resumable() {
        return true;
    }

    @IntDef({PENDING, PREPARED, CONNECTING, RUNNING, PAUSE, FINISH, WAITING})
    @Retention(RetentionPolicy.SOURCE)
    @interface DownloadState {
    }
}
