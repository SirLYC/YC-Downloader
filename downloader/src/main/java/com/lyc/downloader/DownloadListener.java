package com.lyc.downloader;

/**
 * @author liuyuchuan
 * @date 2019/4/1
 * @email kevinliu.sir@qq.com
 */
public interface DownloadListener {
    void onPreparing(long id);

    // cur, total: byte
    void onProgressUpdate(long id, long total, long cur, double bps);

    void onDownloadError(long id, String reason, boolean fatal);

    void onDownloadStart(long id);

    void onDownloadPausing(long id);

    void onDownloadPaused(long id);

    void onDownloadTaskWait(long id);

    void onDownloadCanceled(long id);

    void onDownloadFinished(long id);
}
