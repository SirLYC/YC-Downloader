// IDownloadService.aidl
package com.lyc.downloader;

import com.lyc.downloader.IDownloadCallback;
import com.lyc.downloader.ISubmitCallback;
import com.lyc.downloader.IDownloadTasksChangeCallback;
import com.lyc.downloader.db.DownloadInfo;

interface IDownloadService {

    void registerDownloadCallback(IDownloadCallback callback);

    void unregisterDownloadCallback();

    void registerDownloadTasksChangeCallback(IDownloadTasksChangeCallback callback);

    void unregisterDownloadTasksChangeCallback();

    void submit(String url, String path, String filename, ISubmitCallback callback);

    DownloadInfo queryDownloadInfo(long id);

    List<DownloadInfo> queryActiveDownloadInfoList();

    List<DownloadInfo> queryDeletedDownloadInfoList();

    List<DownloadInfo> queryFinishedDownloadInfoList();

    void startAll();

    void pauseAll();

    void startOrResume(long id, boolean restart);

    void pause(long id);

    void cancel(long id);

    void delete(long id, boolean deleteFile);

    void setMaxRunningTask(int count);

    int getMaxRunningTask();

    long getSpeedLimit();

    void setSpeedLimit(long speedLimit);

    boolean isAllowDownload();

    void setAllowDownload(boolean allowDownload);

    boolean isAvoidFrameDrop();

    void setAvoidFrameDrop(boolean avoidFrameDrop);

    // time unit: ms
    void setSendMessageIntervalNanos(long time);

    long getSendMessageIntervalNanos();
}
