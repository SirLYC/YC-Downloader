package com.lyc.yuchuan_downloader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_group_header.view.*
import me.drakeet.multitype.ItemViewBinder

/**
 * Created by Liu Yuchuan on 2019/5/20.
 */
class GroupHeaderItemViewBinder : ItemViewBinder<String, GroupHeaderItemViewBinder.ViewHolder>() {
    override fun onBindViewHolder(holder: ViewHolder, item: String) {
        holder.text.text = item
    }

    override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): ViewHolder {
        return ViewHolder(inflater.inflate(R.layout.item_group_header, parent, false))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        internal val text = itemView.tv_group
    }
}
