// IDownloadCallback.aidl
package com.lyc.downloader;

import com.lyc.downloader.db.DownloadInfo;

interface IDownloadCallback {

    void onDownloadPreparing(long id);

    void onDownloadProgressUpdate(long id, long total, long cur, double bps);

    void onDownloadUpdateInfo(in DownloadInfo downloadInfo);

    void onDownloadError(long id, int code, boolean fatal);

    void onDownloadStart(in DownloadInfo downloadInfo);

    void onDownloadPausing(long id);

    void onDownloadPaused(long id);

    void onDownloadWaiting(long id);

    void onDownloadCanceled(long id);

    void onDownloadFinished(in DownloadInfo downloadInfo);
}
