package com.lyc.downloader;

import com.lyc.downloader.db.DownloadInfo;

import java.util.List;

/**
 * Created by Liu Yuchuan on 2019/5/19.
 */
public interface DownloadInfoProvider {
    DownloadInfo queryDownloadInfo(long id);

    List<DownloadInfo> queryActiveDownloadInfoList();

    List<DownloadInfo> queryDeletedDownloadInfoList();

    List<DownloadInfo> queryFinishedDownloadInfoList();
}
