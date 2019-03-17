package com.lyc.yuchuan_downloader.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

/**
 * @author liuyuchuan
 * @date 2019/3/16
 * @email kevinliu.sir@qq.com
 */
@Entity(tableName = "download")
data class DownloadRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val url: String,
    val name: String,
    val path: String,
    val state: Int,
    val createTime: Calendar = Calendar.getInstance(),
    val finishedTime: Calendar? = null
)
