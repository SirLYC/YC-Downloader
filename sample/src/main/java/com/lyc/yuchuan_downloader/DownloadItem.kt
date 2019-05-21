package com.lyc.yuchuan_downloader

import java.util.*

/**
 * @author liuyuchuan
 * @date 2019-04-23
 * @email kevinliu.sir@qq.com
 */
data class DownloadItem(
    var id: Long = 0,
    var path: String? = null,
    var filename: String? = null,
    var url: String? = null,
    var bps: Double = 0.toDouble(),
    var totalSize: Long = 0,
    var downloadedSize: Long = 0,
    var createdTime: Date? = null,
    var finishedTime: Date? = null,
    var downloadState: Int = 0,
    var errorMessage: String? = null
) {
    override fun equals(other: Any?): Boolean {
        return this.id == (other as? DownloadItem)?.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
