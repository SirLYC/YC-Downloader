// IDownloadService.aidl
package com.lyc.downloader;

import com.lyc.downloader.IDownloadCallback;
import com.lyc.downloader.ISubmitCallback;
import com.lyc.downloader.db.DownloadInfo;

interface IDownloadService {

    void registerDownloadCallback(IDownloadCallback callback);

    void removeDownloadCallback(IDownloadCallback callback);

    void submit(String url, String path, String filename, in Map customerHeaders, ISubmitCallback callback);

    DownloadInfo queryDownloadInfo(long id);

    List<DownloadInfo> queryActiveDownloadInfoList();

    List<DownloadInfo> queryDeletedDownloadInfoList();

    List<DownloadInfo> queryFinishedDownloadInfoList();

    void startAll();

    void pauseAll();

    void startOrResume(long id);

    void pause(long id);

    void cancel(long id);
}
