package com.lyc.yuchuan_downloader;

import android.app.Application;
import com.lyc.downloader.YCDownloader;

/**
 * Created by Liu Yuchuan on 2019/5/18.
 */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // multi process
        YCDownloader.install(this, true);
        // single process
//        YCDownloader.install(this, false);
        // or
//        YCDownloader.install(this);
    }
}
