package com.lyc.yuchuan_downloader;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Created by Liu Yuchuan on 2019/5/18.
 */
public class Async {
    static final Executor cache = Executors.newCachedThreadPool();
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    static final Executor main = new Executor() {
        @Override
        public void execute(Runnable command) {
            HANDLER.post(command);
        }
    };

    private Async() {
    }
}
