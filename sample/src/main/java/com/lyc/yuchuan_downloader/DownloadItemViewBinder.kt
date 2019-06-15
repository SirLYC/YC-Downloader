package com.lyc.yuchuan_downloader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lyc.downloader.DownloadTask.*
import com.lyc.downloader.YCDownloader
import com.lyc.downloader.utils.DownloadStringUtil
import kotlinx.android.synthetic.main.item_download.view.*
import me.drakeet.multitype.ItemViewBinder

/**
 * Created by Liu Yuchuan on 2019/5/20.
 */
class DownloadItemViewBinder(
    private val onItemButtonClickListener: OnItemButtonClickListener
) : ItemViewBinder<DownloadItem, DownloadItemViewBinder.ViewHolder>() {
    override fun onBindViewHolder(holder: ViewHolder, item: DownloadItem) {
        holder.bind(item)
    }

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): ViewHolder {
        return ViewHolder(inflater.inflate(R.layout.item_download, parent, false), onItemButtonClickListener)
    }

    class ViewHolder(
        itemView: View,
        private val onItemButtonClickListener: OnItemButtonClickListener
    ) :
        RecyclerView.ViewHolder(itemView), View.OnClickListener, View.OnLongClickListener {

        override fun onLongClick(v: View) = item?.let {
            onItemButtonClickListener.onItemLongClicked(it, v)
        } ?: false

        private val name: TextView = itemView.name
        private val state: TextView = itemView.state
        private val downloadProgressBar = itemView.dpb.apply {
            setOnClickListener(this@ViewHolder)
        }

        init {
            itemView.setOnLongClickListener(this)
        }

        private var item: DownloadItem? = null

        internal fun bind(item: DownloadItem) {
            this.item = item
            name.text = item.filename
            val progress: Int
            val cur = item.downloadedSize
            val total = item.totalSize.toDouble()

            val downloadState = item.downloadState
            var stateString: String?
            if (total <= 0 || downloadState == FINISH) {
                progress = 0
                stateString = DownloadStringUtil.byteToString(total)
            } else {
                progress = Math.max((cur / total * 100).toInt(), 0)
                stateString =
                    DownloadStringUtil.byteToString(cur.toDouble()) + "/" + DownloadStringUtil.byteToString(total)
            }

            downloadProgressBar.progress = progress

            when (item.downloadState) {
                PENDING, CONNECTING -> stateString = "$stateString | 连接中"
                RUNNING -> stateString = stateString + " | " + DownloadStringUtil.bpsToString(item.bps)
                PAUSING -> stateString = "$stateString | 正在暂停"
                PAUSED -> stateString = "$stateString | 已暂停"
                WAITING -> stateString = "$stateString | 等待中"
                CANCELED -> stateString = "已取消"
                ERROR, FATAL_ERROR -> {
                    var errorMessage: String? = YCDownloader.translateErrorCode(item.errorCode!!)
                    if (errorMessage == null) {
                        errorMessage = "下载失败"
                    }
                    stateString = errorMessage
                }
            }

            downloadProgressBar.isEnabled =
                downloadState == PAUSED || downloadState == ERROR || downloadState == FATAL_ERROR ||
                        downloadState == RUNNING || downloadState == CONNECTING || downloadState == WAITING
            state.text = stateString
            if (item.downloadState == FINISH) {
                downloadProgressBar.visibility = View.GONE
            } else {
                downloadProgressBar.visibility = View.VISIBLE
                downloadProgressBar.active = (downloadState == PENDING
                        || downloadState == RUNNING
                        || downloadState == CONNECTING
                        || downloadState == WAITING)
            }
        }

        override fun onClick(v: View) {
            item?.let {
                val state = it.downloadState
                if (v.id == R.id.dpb) {
                    if (state == PAUSED || state == ERROR || state == FATAL_ERROR) {
                        onItemButtonClickListener.startItem(it)
                    } else if (state == RUNNING || state == CONNECTING || state == WAITING) {
                        onItemButtonClickListener.pauseItem(it)
                    }
                }
            }
        }
    }

    interface OnItemButtonClickListener {
        fun pauseItem(item: DownloadItem)

        fun startItem(item: DownloadItem)

        fun onItemLongClicked(item: DownloadItem, view: View): Boolean
    }
}
