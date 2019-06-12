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

    private static Handler mainHandler = new Handler(Looper.getMainLooper());

    static final Executor message = new RunOrPostExecutor("message");
    static final Executor command = new RunOrPostExecutor("command");

    static void init() {
        ((Thread) message).start();
        ((Thread) command).start();
    }

    static final Executor io = Executors.newCachedThreadPool();

    static final Executor androidMain = command -> mainHandler.post(command);

    static boolean isMessageThread() {
        return Thread.currentThread() == message;
    }

    static void removeCallback(Executor executor, Runnable runnable) {
        if (executor instanceof RunOrPostExecutor) {
            ((RunOrPostExecutor) executor).handler.removeCallbacks(runnable);
        }
    }

    private static class RunOrPostExecutor extends HandlerThread implements Executor {
        private final CountDownLatch countDownLatch = new CountDownLatch(1);
        private Handler handler;

        RunOrPostExecutor(String name) {
            super(name);
            setDaemon(true);
        }

        @Override
        protected void onLooperPrepared() {
            handler = new Handler(getLooper());
            countDownLatch.countDown();
        }

        @Override
        public void execute(Runnable command) {
            if (Thread.currentThread() == this) {
                command.run();
                return;
            }

            while (countDownLatch.getCount() > 0) {
                try {
                    countDownLatch.await();
                } catch (InterruptedException e) {
                    // do nothing
                }
            }

            handler.post(command);
        }
    }
}
