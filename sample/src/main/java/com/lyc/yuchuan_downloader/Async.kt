package com.lyc.yuchuan_downloader

import android.os.Handler
import android.os.Looper

import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Created by Liu Yuchuan on 2019/5/18.
 */
object Async {
    internal val cache: Executor = Executors.newCachedThreadPool()
    private val HANDLER = Handler(Looper.getMainLooper())
    internal val main: Executor = Executor { command -> HANDLER.post(command) }
}
