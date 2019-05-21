package com.lyc.yuchuan_downloader.util

import androidx.recyclerview.widget.ListUpdateCallback

/**
 * Created by Liu Yuchuan on 2019/5/20.
 */
class ObservableList<T>(private val realList: MutableList<T>) : AbstractMutableList<T>(), ListUpdateCallback {

    private val callbacks = mutableListOf<ListUpdateCallback>()

    fun addCallback(callback: ListUpdateCallback): Boolean = callbacks.add(callback)
    fun removeCallback(callback: ListUpdateCallback): Boolean = callbacks.remove(callback)
    var enable = true

    override val size: Int
        get() = realList.size

    override fun get(index: Int): T = realList[index]

    override fun add(index: Int, element: T) {
        realList.add(index, element)
        this.onInserted(index, 1)
    }

    override fun set(index: Int, element: T): T {
        val result = realList.set(index, element)
        this.onChanged(index, 1, element)
        return result
    }

    override fun removeAt(index: Int): T {
        val result = realList.removeAt(index)
        this.onRemoved(index, 1)
        return result
    }

    fun changeItem(index: Int, payload: Any?) {
        this.onChanged(index, 1, payload)
    }

    /* ============================== batch notify ============================== */

    override fun addAll(elements: Collection<T>): Boolean {
        val oldSize = size
        val result = realList.addAll(elements) // return false if elements is empty
        if (result) {
            this.onInserted(oldSize, elements.size)
        }
        return result
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        val result = realList.addAll(index, elements) // return false if elements is empty
        if (result) {
            this.onInserted(index, elements.size)
        }
        return result
    }

    override fun clear() {
        val oldSize = size
        if (oldSize == 0) {
            return
        }

        realList.clear()
        this.onRemoved(0, oldSize)
    }

    override fun removeRange(fromIndex: Int, toIndex: Int) {
        // assert fromIndex < toIndex
        var index = fromIndex
        while (index < toIndex) {
            realList.removeAt(index)
            index++
        }

        this.onRemoved(0, toIndex - fromIndex)
    }

    /* ============================== ListUpdateCallback ============================== */

    override fun onInserted(position: Int, count: Int) {
        if (enable) {
            callbacks.forEach { it.onInserted(position, count) }
        }
    }

    override fun onRemoved(position: Int, count: Int) {
        if (enable) {
            callbacks.forEach { it.onRemoved(position, count) }
        }
    }

    override fun onMoved(fromPosition: Int, toPosition: Int) {
        if (enable) {
            callbacks.forEach { it.onMoved(fromPosition, toPosition) }
        }
    }

    override fun onChanged(position: Int, count: Int, payload: Any?) {
        if (enable) {
            callbacks.forEach { it.onChanged(position, count, payload) }
        }
    }

    /* ============================== extension ============================== */

    fun replaceAll(elements: Collection<T>) {
        clear()
        addAll(elements)
    }
}
