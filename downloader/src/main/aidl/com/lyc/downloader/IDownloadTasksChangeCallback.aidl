// IDownloadTasksChangeCallback.aidl
package com.lyc.downloader;

import com.lyc.downloader.db.DownloadInfo;

interface IDownloadTasksChangeCallback {

    void onNewDownloadTaskArrive(inout DownloadInfo downloadInfo);

    void onDownloadTaskRemove(long id);
}
