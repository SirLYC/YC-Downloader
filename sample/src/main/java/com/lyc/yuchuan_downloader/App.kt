package com.lyc.yuchuan_downloader

import android.app.Application
import com.lyc.downloader.Configuration
import com.lyc.downloader.YCDownloader
import java.util.concurrent.TimeUnit

/**
 * Created by Liu Yuchuan on 2019/5/18.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = Configuration.Builder()
            // If service running in another process;
            // Default value:
            // Decided by service in your manifest;
            // If both remote and local services are defined in your manifest,
            // remote one will be selected;
            .setMultiProcess(false)
            // If allow download. Default true;
            .setAllowDownload(true)
            // If avoid frame drop. Default true;
            .setAvoidFrameDrop(true)
            // Default 4;
            .setMaxRunningTask(4)
            // Send progress update message to main thread interval time in nano. Default 333ms;
            .setSendMessageIntervalNanos(TimeUnit.MILLISECONDS.toNanos(333))
            // Speed limit(bytes/s). If <= 0, no limit;
//            .setSpeedLimit(2048 * 1024)
            .setSpeedLimit(0)
            .build()

        YCDownloader.install(this, config)

        // multiProcess is selected by YCDownloader;
        // Other params are default values;
//        YCDownloader.install(this)
    }
}
