package com.lyc.yuchuan_downloader.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lyc.yuchuan_downloader.utils.WorkerPool

/**
 * @author liuyuchuan
 * @date 2019/3/16
 * @email kevinliu.sir@qq.com
 */
@Database(entities = [DownloadRecord::class], version = 1)
@TypeConverters(value = [DownloadRecordTypeConverter::class])
abstract class DownloadDB : RoomDatabase() {

    companion object {
        @Volatile
        private var instance: DownloadDB? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(DownloadDB::class.java) {
                    if (instance == null) {
                        instance =
                            Room.databaseBuilder(context.applicationContext, DownloadDB::class.java, "download.db")
                                .setQueryExecutor(WorkerPool.diskIO)
                                .build()
                    }
                }
            }
        }

        fun instance() = instance!!
    }

    abstract fun downloadRecordDao(): DownloadRecordDao
}
