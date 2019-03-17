package com.lyc.yuchuan_downloader.core

import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import com.lyc.yuchuan_downloader.PROCESSING
import com.lyc.yuchuan_downloader.db.DownloadDB
import com.lyc.yuchuan_downloader.db.DownloadRecord
import com.lyc.yuchuan_downloader.utils.WorkerPool
import com.lyc.yuchuan_downloader.utils.toSpeed
import com.lyc.yuchuan_downloader.utils.toTime
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author liuyuchuan
 * @date 2019/3/16
 * @email kevinliu.sir@qq.com
 */
abstract class DownloadTask(
    val url: String,
    val parentPath: String,
    val downloadCallback: DownloadCallback,
    newCreate: Boolean = true,
    status: Int = IDLE,
    recordId: Long? = null
) {

    companion object {
        const val IDLE = 0
        const val PENDING = 2
        const val WAITING = 3
        const val DOWNLOADING = 4
        const val PAUSING = 5
        const val PAUSED = 6
        const val FINISHED = 7
        const val ERROR = 8
    }

    private var create = AtomicBoolean(!newCreate)
    protected var state = AtomicInteger(if (newCreate) IDLE else status)
    lateinit var record: LiveData<DownloadRecord>

    init {
        if (recordId != null) {
            record = DownloadDB.instance().downloadRecordDao().queryById(recordId)
        }
    }

    private fun parseFilename(): String {
        var name: String = url
        var index = url.lastIndexOf('?')
        if (index >= 0) {
            name = name.substring(0, index)
        }
        index = url.lastIndexOf('/')
        if (index >= 0) {
            name = name.substring(0, index)
        }
        return name
    }

    @MainThread
    fun createTask() {
        if (state.compareAndSet(IDLE, PENDING) && state.get() != FINISHED) {
            var fileName = parseFilename()
            // find a non-exit file name
            while (File(fileName).exists()) {
                val names = fileName.split('.').toMutableList()
                if (names.size == 1) {
                    fileName += "_new"
                } else {
                    names.add(names.size - 1, "_new")
                    fileName = names.joinToString(".")
                }
            }
            WorkerPool.diskIO.run {
                val downloadRecord = DownloadRecord(
                    0,
                    url,
                    fileName,
                    "$parentPath${File.pathSeparator}$fileName",
                    PROCESSING
                )
                val id = DownloadDB.instance().downloadRecordDao().insertRecord(
                    downloadRecord
                )

                WorkerPool.main.execute {
                    if (id > 0) {
                        downloadCallback.onTaskCreateFail(Exception("create task failed!"))
                    } else {
                        record = DownloadDB.instance().downloadRecordDao().queryById(id)
                        downloadCallback.onTaskCreated(this@DownloadTask)
                    }
                }
            }
        }
    }

    @WorkerThread
    abstract fun doDownload()

    abstract fun cancel()

    fun pause() = state.compareAndSet(DOWNLOADING, PAUSING)

    @WorkerThread
    protected fun readToFile(source: InputStream, bakFile: File, total: Long, alreadyWrite: Long) =
        runCatching {
            WorkerPool.main.execute {
                downloadCallback.onProgressUpdate(this@DownloadTask, bakFile.length(), total, "-", "-")
            }
            val sink = FileOutputStream(bakFile, true)
            var read = 1
            var write = alreadyWrite
            val bytes = ByteArray(1024)
            while (read > 0) {
                val st = state.get()
                if (st != DOWNLOADING) {
                    throw InterruptedException()
                }
                val startTime = System.nanoTime()
                read = source.read(bytes)
                if (read > 0) {
                    write += read
                    val speed = read.toFloat() / ((System.nanoTime() - startTime) / 10e9)
                    val left = if (total != -1L) {
                        ((total - write) / speed).toFloat().toTime()
                    } else {
                        "未知"
                    }
                    sink.write(bytes, 0, read)
                    WorkerPool.main.execute {
                        downloadCallback.onProgressUpdate(
                            this@DownloadTask,
                            write,
                            total,
                            speed.toFloat().toSpeed(),
                            left
                        )
                    }
                }
            }
        }.isSuccess


    interface DownloadCallback {
        fun onTaskCreateFail(t: Throwable)
        fun onTaskCreated(task: DownloadTask)
        fun onError(task: DownloadTask, reason: String)
        fun onProgressUpdate(
            task: DownloadTask,
            current: Long = 0,
            total: Long = 0,
            speed: String = "-",
            estimate: String = "-"
        )
    }
}
