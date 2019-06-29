# YC-Downloader

[![](https://jitpack.io/v/SirLYC/YC-Downloader.svg)](https://jitpack.io/#SirLYC/YC-Downloader)

A multi-thread, multi-task and multi-process downloader. It supports HTTP, download speed limit

## Features
- [x] HTTP/HTTPS download
- [x] multi-thread download
- [x] download thread and disk-io thread separated
- [x] multi download task
- [x] support for HTTP (resume from break-point)
- [x] message control to avoid ui frame drops 
- [x] multi-process support
- [x] download speed limit
- [ ] other protocol download maybe...

## Run
**`app` module** 
    
    A simple apk instance which uses download library.

**`downloader` module**
    
    Download library.


## Install
**Step 1.** Add the JitPack repository to your build file

Add it in your root build.gradle at the end of repositories:

``` groovy
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

**Step 2.** Add the dependency

``` groovy
dependencies {
    implementation 'com.github.SirLYC:Yuchuan-Downloader:latest.release'
}
```

> [Check release notes here](https://github.com/SirLYC/YC-Downloader/releases)

**Step3.** Install YCDownloader

In `manifest`:

``` xml
<manifest>
    <!--required-->
    <uses-permission android:name="android.permission.INTERNET"/>
    <!--not required but important-->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
    ...
    <application>
        ...   
        <!--One of them is required-->
        <!--When you want service to run in your app process.-->
        <service android:name="com.lyc.downloader.LocalDownloadService"/>
    
        <!--When you want service to run In another process. Attribute process must be defined and different from you package name-->
        <service android:name="com.lyc.downloader.RemoteDownloadService"
                 android:process=":remote"/>
        ...
    </application>
</manifest>
```

It's recommended to install in `Application` class

``` kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = Configuration.Builder()
            // If service running in another process;
            // Default value:
            // Decided by service in your manifest;
            // If both remote and local services are defined in your manifest,
            // remote one will be selected;
            .setMultiProcess(true)
            // If allow download. Default true;
            .setAllowDownload(true)
            // If avoid frame drop. Default true;
            .setAvoidFrameDrop(true)
            // Default 4;
            .setMaxRunningTask(4)
            // Send progress update message to main thread interval time in nano. Default 333ms;
            .setSendMessageIntervalNanos(TimeUnit.MILLISECONDS.toNanos(333))
            // Speed limit. If <= 0, no limit;
            .setSpeedLimit(1024)
            .build()

        YCDownloader.install(this, config)

        // multiProcess is selected by YCDownloader;
        // Other params are default values;
//        YCDownloader.install(this)
    }
}
```

Then Just learn apis!

## Main API

You can check all apis in file [YCDownloader.java](https://github.com/SirLYC/YC-Downloader/blob/master/downloader/src/main/java/com/lyc/downloader/YCDownloader.java)


**Start download**
``` java
private SubmitListener submitListener = new SubmitListener() {
        @Override
        public void submitSuccess(DownloadInfo downloadInfo) {
            Log.d("Submit", "success submit, id = " + downloadInfo.getId());
        }

        @Override
        public void submitFail(Exception e) {
            Log.e("Submit", "submit failed", e);
        }
};
// path: parent directory to store your file
// filename: can be null; if not null, downloader will use it to save your file
YCDownloader.submit(url, path, filename, submitListener);
``` 

**Listen to download progress or state change**
``` java
DownloadListener downloadListener = ...;
YCDownloader.registerDownloadListener(downloadListener);

// you should unregister it to avoid memory leak
// such as Activity.OnDestroy
YCDownloader.unregisterDownloadListener(downloadListener);
```

**Query download info**
``` java
// attention: these methods should be called in worker thread
// query by id
YCDownloader.queryDownloadInfo(long id);
// state != CANCELED && state != FINISH
YCDownloader.queryActiveDownloadInfoList();
// state == DELETED
YCDownloader.queryDeletedDownloadInfoList();
// state == FINISH
YCDownloader.queryFinishedDownloadInfoList();
```

**Configuration**
``` java
// limit your running task to 4
YCDownloader.setMaxRunningTask(4);

// limit your download speed to 512KB
// param <= 0 means no limit, run as fast as possible
YCDownloader.setSpeedLimit(512 * 1024);

// if true, the speed of sending progress-update message will slow down
YCDownloader.setAvoidFrameDrop(true);
// send progress update message interval at least 500ms
// only valid when setAvoidFrameDrop(true)
YCDownloader.setSendMessageInterval(500, TimeUnit.MILLISECONDS);

// If false, all running tasks are in WAITING state
YCDownloaer.setAllowDownload(true)

// Use configuration to update
YCDownloader.updateByConfiguration(Configuration);
```

## Important classes

**YCDownloader**: [`com.lyc.downloader.YCDownloader`](https://github.com/SirLYC/YC-Downloader/blob/master/downloader/src/main/java/com/lyc/downloader/YCDownloader.java)

Export main apis. 

**DownloadListener**: [`com.lyc.downloader.DownloadListener`](https://github.com/SirLYC/YC-Downloader/blob/master/downloader/src/main/java/com/lyc/downloader/DownloadListener.java)

Callback of the download progress and state change of every downloadTask.

**DownloadTasksChangeListener**: [`com.lyc.downloader.DownloadTasksChangeListener`](https://github.com/SirLYC/YC-Downloader/blob/master/downloader/src/main/java/com/lyc/downloader/DownloadTasksChangeListener.java)

Callback of the change in downloadTasks: created or removed. It's a good feature to implement a function like eventBus.

**DownloadInfo**: [`com.lyc.downloader.db.DownloadInfo`](https://github.com/SirLYC/YC-Downloader/blob/master/downloader/src/main/java/com/lyc/downloader/db/DownloadInfo.java)

Database entity, also `Parcelable` to pass between **multi-process**.

**Configuration**: [`com.lyc.downloader.Configuration`](https://github.com/SirLYC/YC-Downloader/blob/master/downloader/src/main/java/com/lyc/downloader/Configuration.java)

An immutable class to pass params of downloader.

## Permissions

```xml
<!--required-->
<uses-permission android:name="android.permission.INTERNET"/>
<!--not required but important-->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
```


## Licence
```
MIT License

Copyright (c) 2019 Liu

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

```
