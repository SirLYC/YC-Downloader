package com.lyc.downloader;

/**
 * Created by Liu Yuchuan on 2019/5/18.
 */
// TODO: 2019-06-10 use IDownloadService?
public interface DownloadController {
    void startOrResume(long id, boolean restart);

    void pause(long id);

    void startAll();

    void pauseAll();

    void cancel(long id);

    void submit(String url, String path, String filename, SubmitListener listener);

    void delete(long id, boolean deleteFile);

    int getMaxRunningTask();

    void setMaxRunningTask(int count);

    boolean isAvoidFrameDrop();

    void setAvoidFrameDrop(boolean avoidFrameDrop);

    long getSendMessageIntervalNanos();

    // time unit: ms
    void setSendMessageIntervalNanos(long time);
}
