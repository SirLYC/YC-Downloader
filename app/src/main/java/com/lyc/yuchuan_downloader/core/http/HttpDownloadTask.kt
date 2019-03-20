package com.lyc.yuchuan_downloader.core.http

import androidx.annotation.WorkerThread
import com.lyc.yuchuan_downloader.core.DownloadTask
import com.lyc.yuchuan_downloader.utils.Network
import com.lyc.yuchuan_downloader.utils.WorkerPool
import okhttp3.Call
import okhttp3.Request
import java.io.File

/**
 * @author liuyuchuan
 * @date 2019/3/17
 * @email kevinliu.sir@qq.com
 */
class HttpDownloadTask(
    url: String,
    parentPath: String,
    downloadCallback: DownloadCallback,
    newCreate: Boolean,
    status: Int,
    recordId: Long?
) : DownloadTask(url, parentPath, downloadCallback, newCreate, status, recordId) {

    @Volatile
    private var call: Call? = null

    @WorkerThread
    override fun doDownload() {
        var st = state.get()

        if (st != PENDING && st != WAITING && st != PAUSED && st != ERROR) {
            return
        }

        if (call != null) {
            cancel()
        }

        // only one thread open download
        if (state.compareAndSet(st, DOWNLOADING)) {
            val recordData = record.value!!
            val file = File(recordData.path)
            if (file.exists()) {
                file.delete()
            }
            val bakFile = File("${recordData.path}.yuchuan")
            val req = Request.Builder().url(url).header("Range", "bytes=${bakFile.length()}-").build()

            call = Network.client.newCall(req).apply {
                val result = runCatching {
                    execute()
                }

                if (result.isSuccess) {
                    val resp = result.getOrNull()!!
                    val body = resp.body()

                    if (!resp.isSuccessful || body == null) {
                        cancel()
                        WorkerPool.main.execute {
                            downloadCallback.onError(this@HttpDownloadTask, "server error")
                        }
                        return
                    }

                    var contentLength = body.contentLength()
                    if (contentLength <= 0) {

                        contentLength = resp.header("Content-Range")?.let { contentRange ->
                            val index = contentRange.lastIndexOf('/')
                            if (index != -1) {
                                kotlin.runCatching {
                                    contentRange.substring(index).toLong()
                                }.getOrNull()
                            } else null
                        } ?: -1
                    } else {
                        contentLength += bakFile.length()
                    }
                    val readResult =
                        readToFile(body.byteStream(), bakFile, contentLength, bakFile.length())
                    if (readResult) {
                        bakFile.renameTo(file)
                        downloadCallback.onProgressUpdate(this@HttpDownloadTask)
                    } else {
                        st = state.get()
                        if (st == PAUSING && state.compareAndSet(st, PAUSED)) {
                            WorkerPool.main.execute {
                                downloadCallback.onProgressUpdate(this@HttpDownloadTask)
                            }
                        } else {
                            file.delete()
                            cancel()
                            if (state.compareAndSet(st, ERROR)) {
                                WorkerPool.main.execute {
                                    downloadCallback.onError(this@HttpDownloadTask, "download error")
                                }
                            }
                        }
                    }
                } else {
                    WorkerPool.main.execute {
                        downloadCallback.onError(this@HttpDownloadTask, "cannot connect to server")
                    }
                }
            }
        }
    }

    override fun cancel() {
        val curState = state.get()
        if (state.compareAndSet(curState, IDLE)) {
            call?.cancel()
            call = null
        }
    }
}
