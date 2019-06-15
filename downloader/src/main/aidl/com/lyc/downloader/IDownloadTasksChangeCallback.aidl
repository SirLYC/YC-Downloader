// IDownloadTasksChangeCallback.aidl
package com.lyc.downloader;

import com.lyc.downloader.db.DownloadInfo;

interface IDownloadTasksChangeCallback {

    void onNewDownloadTaskArrive(in DownloadInfo downloadInfo);

    void onDownloadTaskRemove(long id);
}
