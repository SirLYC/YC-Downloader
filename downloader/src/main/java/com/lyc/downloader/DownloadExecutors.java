package com.lyc.downloader;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author liuyuchuan
 * @date 2019/4/1
 * @email kevinliu.sir@qq.com
 */
class DownloadExecutors {

    private static CountDownLatch messageReadyLatch = new CountDownLatch(1);

    private static Handler mainHandler = new Handler(Looper.getMainLooper());
    private static HandlerThread messageThread = new HandlerThread("DownloadMessageThread") {
        @Override
        protected void onLooperPrepared() {
            messageHandler = new Handler(getLooper());
            messageReadyLatch.countDown();
        }
    };
    private static Handler messageHandler;

    static void init() {
        messageThread.setDaemon(true);
        messageThread.start();
    }

    static boolean isMessageThread() {
        return Thread.currentThread() == messageThread;
    }

    static final Executor io = Executors.newCachedThreadPool();

    static final Executor androidMain = command -> mainHandler.post(command);

    static final Executor message = new Executor() {
        @Override
        public void execute(Runnable command) {
            try {
                messageReadyLatch.await();
            } catch (InterruptedException e) {
                if (BuildConfig.DEBUG) {
                    e.printStackTrace();
                }
            }
            messageHandler.post(command);
        }
    };
}
