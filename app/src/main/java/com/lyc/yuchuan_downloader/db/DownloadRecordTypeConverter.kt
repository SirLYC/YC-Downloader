package com.lyc.yuchuan_downloader.db

import androidx.room.TypeConverter
import java.util.*

/**
 * @author liuyuchuan
 * @date 2019/3/16
 * @email kevinliu.sir@qq.com
 */
class DownloadRecordTypeConverter {
    @TypeConverter
    fun longToCalendar(time: Long): Calendar? {
        if (time < 0) {
            return null
        }

        return Calendar.getInstance().apply {
            timeInMillis = time
        }
    }

    @TypeConverter
    fun calendarToLong(calendar: Calendar?) = calendar?.timeInMillis ?: -1
}
