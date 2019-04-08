package com.lyc.downloader;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author liuyuchuan
 * @date 2019/4/1
 * @email kevinliu.sir@qq.com
 */
public class DownloadExecutors {

    private static final String PREFIX = "yuchuan_downloader-io-";

    private static AtomicInteger ioId = new AtomicInteger(1);

    public static Executor io = Executors.newCachedThreadPool(r -> new Thread(PREFIX + ioId.getAndIncrement()) {
        @Override
        public void run() {
            r.run();
        }
    });
}
