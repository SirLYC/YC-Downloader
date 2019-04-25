package com.lyc.yuchuan_downloader;

import androidx.recyclerview.widget.ListUpdateCallback;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * @author liuyuchuan
 * @date 2019-04-24
 * @email kevinliu.sir@qq.com
 */
public class ObservableList<E> extends AbstractList<E> {
    private final List<E> realList;
    private final List<ListUpdateCallback> listUpdateCallbacks = new ArrayList<>();

    public ObservableList(List<E> realList) {
        if (realList == null) {
            throw new NullPointerException();
        }
        this.realList = realList;
    }

    public void addCallback(ListUpdateCallback callback) {
        listUpdateCallbacks.add(callback);
    }

    public void removeCallback(ListUpdateCallback callback) {
        listUpdateCallbacks.remove(callback);
    }

    @Override
    public void add(int index, E element) {
        realList.add(index, element);
        for (ListUpdateCallback listUpdateCallback : listUpdateCallbacks) {
            listUpdateCallback.onInserted(index, 1);
        }
    }

    @Override
    public E get(int index) {
        return realList.get(index);
    }

    @Override
    public E set(int index, E element) {
        E result = realList.set(index, element);
        for (ListUpdateCallback listUpdateCallback : listUpdateCallbacks) {
            listUpdateCallback.onChanged(index, 1, null);
        }
        return result;
    }

    @Override
    public E remove(int index) {
        E result = realList.remove(index);
        for (ListUpdateCallback listUpdateCallback : listUpdateCallbacks) {
            listUpdateCallback.onRemoved(index, 1);
        }
        return result;
    }

    @Override
    public int size() {
        return realList.size();
    }

    public void onChange(int position, int count, Object payload) {
        for (ListUpdateCallback listUpdateCallback : listUpdateCallbacks) {
            listUpdateCallback.onChanged(position, count, payload);
        }
    }
}
