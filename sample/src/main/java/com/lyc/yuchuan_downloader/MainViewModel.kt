package com.lyc.yuchuan_downloader

import android.util.Log
import androidx.collection.LongSparseArray
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.ListUpdateCallback
import com.lyc.downloader.DownloadListener
import com.lyc.downloader.DownloadTask.*
import com.lyc.downloader.DownloadTasksChangeListener
import com.lyc.downloader.SubmitListener
import com.lyc.downloader.YCDownloader
import com.lyc.downloader.db.DownloadInfo
import com.lyc.downloader.utils.DownloadStringUtil
import com.lyc.yuchuan_downloader.util.ObservableList
import java.util.*

/**
 * @author liuyuchuan
 * @date 2019-04-23
 * @email kevinliu.sir@qq.com
 */
class MainViewModel : ViewModel(), SubmitListener, DownloadListener, DownloadTasksChangeListener {
    override fun onNewDownloadTaskArrive(downloadInfo: DownloadInfo) {
        Log.d("MainViewModel", "new download test arrive: $downloadInfo")
        if (idToItem[downloadInfo.id] == null) {
            when (downloadInfo.downloadItemState) {
                FINISH -> {
                    downloadInfoToItem(downloadInfo).let {
                        idToItem.put(downloadInfo.id, it)
                        finishedItemList.add(0, it)
                    }
                }

                CANCELED -> {
                    // do nothing
                }

                else -> {
                    downloadInfoToItem(downloadInfo).let { newAddItem ->
                        idToItem.put(downloadInfo.id, newAddItem)
                        var index = downloadItemList.indexOfFirst { item ->
                            item.createdTime <= newAddItem.createdTime
                        }

                        if (index < 0) {
                            index = 0
                        }
                        downloadItemList.add(index, newAddItem)
                    }
                }
            }
        }
    }

    override fun onDownloadTaskRemove(id: Long) {
        Log.d("MainViewModel", "download task removed: ${idToItem[id]}")
    }

    internal val itemList = ObservableList(ArrayList<Any>())
    private val downloadItemList = ObservableList(ArrayList<DownloadItem>())
    private val finishedItemList = ObservableList(ArrayList<DownloadItem>())
    internal val failLiveData = MutableLiveData<String>()
    private val idToItem = LongSparseArray<DownloadItem>()
    var path: String? = null
        private set
    private val downloading = "正在下载"
    private val finished = "已完成"
    private var setup = false

    private val downloadListCallback = object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            var offset = 0
            val list: MutableList<Any> = ArrayList(downloadItemList.subList(position, position + count))
            if (count == downloadItemList.size) {
                list.add(0, downloading)
            } else {
                offset = 1
            }
            itemList.addAll(position + offset, list)
        }

        override fun onRemoved(position: Int, count: Int) {
            var offset = 0
            if (downloadItemList.isEmpty() && !itemList.isEmpty() && itemList[0] === downloading) {
                itemList.removeAt(0)
            } else {
                offset = 1
            }
            val p = position + offset
            var i = 0
            while (i < count) {
                itemList.removeAt(p)
                i++
            }
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            itemList.enable = false
            itemList.enable = false
            itemList[fromPosition + 1] = downloadItemList[toPosition]
            itemList[toPosition + 1] = downloadItemList[fromPosition]
            itemList.enable = true
            itemList.onMoved(fromPosition + 1, toPosition + 1)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            var i = 0
            while (i < count) {
                itemList[position + i + 1] = downloadItemList[position + i]
                i++
            }
        }
    }


    private val finishedListCallback = object : ListUpdateCallback {
        private val startOffset: Int
            get() = if (downloadItemList.isEmpty()) {
                0
            } else downloadItemList.size + 1

        override fun onInserted(position: Int, count: Int) {
            var offset = 0
            val list: MutableList<Any> = ArrayList(finishedItemList.subList(position, position + count))
            if (count == finishedItemList.size) {
                list.add(0, finished)
            } else {
                offset = 1
            }
            itemList.addAll(position + startOffset + offset, list)
        }

        override fun onRemoved(position: Int, count: Int) {
            var offset = startOffset
            if (finishedItemList.isEmpty() && !itemList.isEmpty() && itemList[offset] === finished) {
                itemList.removeAt(offset)
            } else {
                offset++
            }
            val p = position + offset
            var i = 0
            while (i < count) {
                itemList.removeAt(p)
                i++
            }
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            itemList.enable = false
            itemList.enable = false
            itemList[fromPosition + 1 + startOffset] = downloadItemList[toPosition]
            itemList[toPosition + 1 + startOffset] = downloadItemList[fromPosition]
            itemList.enable = true
            itemList.onMoved(fromPosition + 1 + startOffset, toPosition + 1 + startOffset)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            var i = 0
            val offset = startOffset + 1
            while (i < count) {
                itemList[position + i + offset] = finishedItemList[position + i]
                i++
            }
        }
    }

    fun setup(path: String) {
        if (setup) {
            return
        }
        setup = true
        this.path = path
        downloadItemList.addCallback(downloadListCallback)
        finishedItemList.addCallback(finishedListCallback)
        YCDownloader.registerDownloadListener(this)
        YCDownloader.registerDownloadTasksChangeListener(this)
        Async.cache.execute {
            val downloadInfoList = YCDownloader.queryActiveDownloadInfoList()
            val finishedList = YCDownloader.queryFinishedDownloadInfoList()
            Async.main.execute {
                for (downloadInfo in downloadInfoList) {
                    val item = downloadInfoToItem(downloadInfo)
                    idToItem.put(downloadInfo.id!!, item)
                    downloadItemList.add(item)
                }

                for (downloadInfo in finishedList) {
                    val item = downloadInfoToItem(downloadInfo)
                    idToItem.put(downloadInfo.id!!, item)
                    finishedItemList.add(item)
                }
            }
        }
    }

    internal fun submit(url: String) {
        YCDownloader.submit(url, path, null, this)
    }

    private fun doUpdateCallback(id: Long, updateCallback: (item: DownloadItem) -> DownloadItem) {
        val item = idToItem.get(id)?.let(updateCallback) ?: return
        val index = downloadItemList.indexOf(item)
        if (index == -1) {
            finishedItemList.remove(item)
            downloadItemList.add(0, item)
        } else if (index != -1) {
            downloadItemList[index] = item
        }
    }

    override fun onDownloadConnecting(id: Long) {
        doUpdateCallback(id) { item ->
            item.downloadState = CONNECTING
            item
        }
    }

    override fun onDownloadProgressUpdate(id: Long, total: Long, cur: Long, bps: Double) {
        doUpdateCallback(id) { item ->
            item.totalSize = total
            item.downloadedSize = cur
            item.bps = bps
            item
        }
    }

    override fun onDownloadUpdateInfo(info: DownloadInfo) {
        doUpdateCallback(info.id) {
            downloadInfoToItem(info).apply {
                idToItem.put(it.id, this)
            }
        }
    }

    override fun onDownloadError(id: Long, reason: Int, fatal: Boolean) {
        doUpdateCallback(id) { item ->
            item.downloadState = ERROR
            item.errorCode = reason
            item
        }
    }

    override fun onDownloadStart(info: DownloadInfo) {
        doUpdateCallback(info.id) {
            downloadInfoToItem(info).apply {
                idToItem.put(info.id, this)
            }
        }
    }

    override fun onDownloadStopping(id: Long) {
        doUpdateCallback(id) { item ->
            item.downloadState = STOPPING
            item
        }
    }

    override fun onDownloadPaused(id: Long) {
        doUpdateCallback(id) { item ->
            item.downloadState = PAUSED
            item
        }
    }

    override fun onDownloadTaskWait(id: Long) {
        doUpdateCallback(id) { item ->
            item.downloadState = WAITING
            item
        }
    }

    override fun onDownloadCanceled(id: Long) {
        val item = idToItem.get(id)
        downloadItemList.remove(item)
        finishedItemList.remove(item)
        idToItem.remove(item!!.id, item)
    }

    override fun onDownloadFinished(downloadInfo: DownloadInfo) {
        val item = idToItem.get(downloadInfo.id!!)
        if (item != null && downloadItemList.remove(item)) {
            finishedItemList.add(0, downloadInfoToItem(downloadInfo))
        }
    }

    override fun submitSuccess(downloadInfo: DownloadInfo?) {
        if (downloadInfo != null) {
            val item = downloadInfoToItem(downloadInfo)
            downloadItemList.add(0, item)
            idToItem.put(item.id, item)
        }
    }

    override fun submitFail(e: Exception) {
        e.printStackTrace()
        failLiveData.postValue(e.message)
        failLiveData.postValue(null)
    }

    private fun downloadInfoToItem(info: DownloadInfo): DownloadItem {
        return DownloadItem(
            info.id!!,
            info.path,
            info.filename ?: DownloadStringUtil.parseFilenameFromUrl(info.url),
            info.url,
            0.0,
            info.totalSize,
            info.downloadedSize,
            info.createdTime,
            info.finishedTime,
            info.downloadItemState,
            info.errorCode
        )
    }

    internal fun pause(id: Long) {
        YCDownloader.pause(id)
    }

    internal fun start(id: Long) {
        YCDownloader.startOrResume(id, false)
    }

    internal fun cancel(id: Long) {
        YCDownloader.cancel(id)
    }

    internal fun delete(id: Long, deleteFile: Boolean) {
        val downloadItem = idToItem[id]
        if (downloadItem != null) {
            YCDownloader.delete(id, deleteFile)
            idToItem.remove(id)
            downloadItemList.remove(downloadItem)
            finishedItemList.remove(downloadItem)
        } else {
            Log.w("MainViewModel", "cannot find Download item.")
        }
    }

    internal fun restart(id: Long) {
        YCDownloader.startOrResume(id, true)
    }

    override fun onCleared() {
        YCDownloader.unregisterDownloadListener(this)
        super.onCleared()
    }
}
