package com.lyc.yuchuan_downloader.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

/**
 * @author liuyuchuan
 * @date 2019/3/16
 * @email kevinliu.sir@qq.com
 */
@Dao
interface DownloadRecordDao {
    @Insert
    fun insertRecord(record: DownloadRecord): Long

    @Delete
    fun deleteRecord(record: DownloadRecord)

    @Query("delete from download where state = :state")
    fun deleteByStates(state: Int)

    @Query("select * from download where state = :state")
    fun queryByStates(state: Int): LiveData<List<DownloadRecord>>

    @Query("select * from download where state = :state")
    fun queryById(state: Long): LiveData<DownloadRecord>
}
