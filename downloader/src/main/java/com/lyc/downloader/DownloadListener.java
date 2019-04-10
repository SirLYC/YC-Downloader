package com.lyc.downloader;

/**
 * @author liuyuchuan
 * @date 2019/4/1
 * @email kevinliu.sir@qq.com
 */
public interface DownloadListener {
    void onPrepared();

    // cur, total: byte
    void onProgressUpdate(long cur, long total);

    void onSpeedChange(double bps);

    void onDownloadError(String reason, boolean fatal);

    void onDownloadStart();

    void onDownloadPausing();

    void onDownloadPaused();

    void onDownloadCancelling();

    void onDownloadCanceled();

    void onDownloadFinished();
}
