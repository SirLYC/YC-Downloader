package com.lyc.downloader;

import com.lyc.downloader.db.DownloadInfo;

/**
 * @author liuyuchuan
 * @date 2019-06-14
 * @email kevinliu.sir@qq.com
 */
public interface DownloadTasksChangeListener {

    /**
     * notified when a new download Task is added to
     * {@link DownloadManager}
     *
     * @param downloadInfo new-added info about download task
     */
    void onNewDownloadTaskArrive(DownloadInfo downloadInfo);

    /**
     * notified when a download task is deleted
     *
     * @param id the id of the download task
     */
    void onDownloadTaskRemove(long id);
}
