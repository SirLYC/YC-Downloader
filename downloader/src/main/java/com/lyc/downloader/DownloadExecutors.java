package com.lyc.downloader;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author liuyuchuan
 * @date 2019/4/1
 * @email kevinliu.sir@qq.com
 */
class DownloadExecutors {

    private static final String PREFIX = "yuchuan_downloader-io-";

    private static AtomicInteger ioId = new AtomicInteger(1);

    static final Executor io = Executors.newCachedThreadPool(r -> new Thread(PREFIX + ioId.getAndIncrement()) {
        @Override
        public void run() {
            r.run();
        }
    });
    private static Handler handler = new Handler(Looper.getMainLooper());
    static final Executor androidMain = command -> handler.post(command);
}
