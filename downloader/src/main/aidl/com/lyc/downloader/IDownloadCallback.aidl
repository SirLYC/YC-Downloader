// IDownloadCallback.aidl
package com.lyc.downloader;

import com.lyc.downloader.db.DownloadInfo;

interface IDownloadCallback {

    void onPreparing(long id);

    void onProgressUpdate(long id, long total, long cur, double bps);

    void onUpdateInfo(inout DownloadInfo downloadInfo);

    void onDownloadError(long id, String reason, boolean fatal);

    void onDownloadStart(inout DownloadInfo downloadInfo);

    void onDownloadPausing(long id);

    void onDownloadPaused(long id);

    void onDownloadTaskWait(long id);

    void onDownloadCanceled(long id);

    void onDownloadFinished(inout DownloadInfo downloadInfo);
}
