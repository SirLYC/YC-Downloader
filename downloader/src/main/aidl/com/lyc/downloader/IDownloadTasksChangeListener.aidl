// IDownloadTasksChangeListener.aidl
package com.lyc.downloader;

import com.lyc.downloader.db.DownloadInfo;

interface IDownloadTasksChangeListener {

    void onNewDownloadTaskArrive(inout DownloadInfo downloadInfo);

    void onDownloadTaskRemove(long id);
}
