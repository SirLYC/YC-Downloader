package com.lyc.downloader;

import com.lyc.downloader.db.DownloadInfo;

/**
 * @author liuyuchuan
 * @date 2019/4/1
 * @email kevinliu.sir@qq.com
 */
public interface DownloadListener {
    /**
     * Called when the task begins to connect to the server.
     *
     * @param id task id
     */
    void onDownloadConnecting(long id);

    /**
     * This callback is controlled by {@link DownloadManager#avoidFrameDrop}
     * and {@link DownloadManager#sendMessageIntervalNanos}
     *
     * @param id    task id
     * @param total total size. May be -1.
     * @param cur   current download size in bytes
     * @param bps   download speed, in bytes/second
     */
    void onDownloadProgressUpdate(long id, long total, long cur, double bps);

    /**
     * Called when downloadInfo is changed.
     * Always called due to filename updated (fetch from server or change to avoid repeating in filesystem).
     *
     * @param downloadInfo new DownloadInfo.
     */
    void onDownloadUpdateInfo(DownloadInfo downloadInfo);

    /**
     * @param id    task id
     * @param code  {@link DownloadError}
     * @param fatal if true, start command will re-download
     */
    void onDownloadError(long id, int code, boolean fatal);

    /**
     * Called after {@link #onDownloadConnecting(long)}.
     * Connecting success and start to download to disk.
     */
    void onDownloadStart(DownloadInfo downloadInfo);

    /**
     * Called when the task is pausing.
     * After this state, {@link #onDownloadPaused(long)} will be called.
     * And you should not start the task when it's state is {@link DownloadTask#STOPPING}.
     */
    void onDownloadStopping(long id);

    /**
     * @see #onDownloadStopping(long)
     */
    void onDownloadPaused(long id);

    /**
     * Called when the task is in the waiting queue.
     * If any running task changes it's state, the manager will schedule
     * to pick the downloadTask that was added to queue first to start.
     */
    void onDownloadTaskWait(long id);

    /**
     * After this callback is called, the download task is still in database.
     * If you want delete it in database, use {@link DownloadController#delete(long, boolean)}.
     */
    void onDownloadCanceled(long id);

    /**
     * Called when download is finished.
     */
    void onDownloadFinished(DownloadInfo downloadInfo);
}
