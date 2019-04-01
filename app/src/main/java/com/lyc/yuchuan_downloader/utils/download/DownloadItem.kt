package com.lyc.yuchuan_downloader.utils.download

import com.lyc.yuchuan_downloader.core.DownloadTask

/**
 * @author liuyuchuan
 * @date 2019/3/19
 * @email kevinliu.sir@qq.com
 */
class DownloadItem : DownloadTask.DownloadCallback {

    override fun onTaskCreateFail(t: Throwable) {

    }

    override fun onTaskCreated(task: DownloadTask) {

    }

    override fun onError(task: DownloadTask, reason: String) {

    }

    override fun onProgressUpdate(task: DownloadTask, current: Long, total: Long, speed: String, estimate: String) {

    }
}
