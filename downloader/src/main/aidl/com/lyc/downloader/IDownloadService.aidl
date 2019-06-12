// IDownloadService.aidl
package com.lyc.downloader;

import com.lyc.downloader.IDownloadCallback;
import com.lyc.downloader.ISubmitCallback;
import com.lyc.downloader.db.DownloadInfo;

interface IDownloadService {

    void registerDownloadCallback(IDownloadCallback callback);

    void removeDownloadCallback(IDownloadCallback callback);

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

    void setAvoidFrameDrop(boolean avoidFrameDrop);

    boolean isAvoidFrameDrop();

    // time unit: ms
    void setSendMessageIntervalNanos(long time);

    long getSendMessageIntervalNanos();
}
