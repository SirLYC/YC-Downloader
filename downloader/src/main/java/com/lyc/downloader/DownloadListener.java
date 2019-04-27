package com.lyc.downloader;

import com.lyc.downloader.db.DownloadInfo;

/**
 * @author liuyuchuan
 * @date 2019/4/1
 * @email kevinliu.sir@qq.com
 */
public interface DownloadListener {
    void onPreparing(long id);

    // cur, total: bytes
    // bps: bytes/s
    void onProgressUpdate(long id, long total, long cur, double bps);

    void onUpdateInfo(DownloadInfo downloadInfo);

    void onDownloadError(long id, String reason, boolean fatal);

    void onDownloadStart(DownloadInfo downloadInfo);

    void onDownloadPausing(long id);

    void onDownloadPaused(long id);

    void onDownloadTaskWait(long id);

    void onDownloadCanceled(long id);

    void onDownloadFinished(DownloadInfo downloadInfo);
}
