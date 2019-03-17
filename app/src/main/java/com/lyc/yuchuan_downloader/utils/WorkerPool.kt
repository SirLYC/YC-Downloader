package com.lyc.yuchuan_downloader.utils

import android.os.Handler
import java.util.concurrent.*

/**
 * @author liuyuchuan
 * @date 2019/3/16
 * @email kevinliu.sir@qq.com
 */
object WorkerPool {

    private val maxComputationThreadCnt = Math.max(Runtime.getRuntime().availableProcessors() - 1, 2)
    private val handler = Handler()

    val diskIO = Executors.newCachedThreadPool()

    val computation = ThreadPoolExecutor(
        maxComputationThreadCnt / 2,
        maxComputationThreadCnt,
        1,
        TimeUnit.MINUTES,
        LinkedBlockingQueue(8)
    )

    val main = Executor {
        handler.post(it)
    }
}
