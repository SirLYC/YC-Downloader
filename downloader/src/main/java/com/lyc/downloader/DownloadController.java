package com.lyc.downloader;

/**
 * Created by Liu Yuchuan on 2019/5/18.
 */
public interface DownloadController {
    void startOrResume(long id, boolean restart);

    void pause(long id);

    void startAll();

    void pauseAll();

    void cancel(long id);

    void submit(String url, String path, String filename, ISubmitCallback callback);

    void delete(long id, boolean deleteFile);

    int getMaxRunningTask();

    void setMaxRunningTask(int count);

    long getSpeedLimit();

    void setSpeedLimit(long speedLimit);

    boolean isAvoidFrameDrop();

    void setAvoidFrameDrop(boolean avoidFrameDrop);

    long getSendMessageIntervalNanos();

    // time unit: ms
    void setSendMessageIntervalNanos(long time);
}
