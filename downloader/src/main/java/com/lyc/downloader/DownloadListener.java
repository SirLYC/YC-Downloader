package com.lyc.downloader;

/**
 * @author liuyuchuan
 * @date 2019/4/1
 * @email kevinliu.sir@qq.com
 */
public interface DownloadListener {
    void onPrepared(long id);

    // cur, total: byte
    void onProgressUpdate(long id, long total, long cur);

    void onSpeedChange(long id, double bps);

    void onDownloadError(long id, String reason, boolean fatal);

    void onDownloadStart(long id);

    void onDownloadPausing(long id);

    void onDownloadPaused(long id);

    void onDownloadCancelling(long id);

    void onDownloadCanceled(long id);

    void onDownloadFinished(long id);
}
