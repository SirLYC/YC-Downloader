# YC-Downloader

[![](https://jitpack.io/v/SirLYC/YC-Downloader.svg)](https://jitpack.io/#SirLYC/YC-Downloader)

多线程、多任务、多进程下载库，支持断点续传、限速等...

> 一个使用这个库开发的完整的下载器App [https://github.com/SirLYC/EveryDownload](https://github.com/SirLYC/EveryDownload)

## Features
- [x] HTTP/HTTPS下载
- [x] 多线程下载
- [x] 下载（生产者）线程与写磁盘（消费者）线程分离
- [x] 多任务管理
- [x] 断点续传
- [x] 消息控制避免UI卡顿
- [x] 多进程支持
- [x] 下载限速
- [x] 自动重试（下载和连接）
- [ ] 有时间支持其他协议下载...

## 项目
**`sample` module** 
    
    一个用该库实现的简单app，显示了库的特性。

**`downloader` module**
    
    下载库。


## 集成
**Step 1.** 添加JitPack repository到根目录的build.gradle

``` groovy
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

**Step 2.** 添加依赖

``` groovy
dependencies {
    implementation 'com.github.SirLYC:Yuchuan-Downloader:latest.release'
}
```

> [Check release notes here](https://github.com/SirLYC/YC-Downloader/releases)

**Step3.** 安装库

在`manifest`文件中:

``` xml
<manifest>
    <!--必须有-->
    <uses-permission android:name="android.permission.INTERNET"/>
    <!--没有会影响正常运行。6.0以上需要验证运行时权限-->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
    ...
    <application>
        ...   
        <!--下面两个中至少一个需要添加，根据需要添加即可-->
        <!--只需要在app的主进程运行时，添加这个Service-->
        <service android:name="com.lyc.downloader.LocalDownloadService"/>
    
        <!--当需要在另一个进程添加下载服务时，需要添加这个。进程名可任意指定，但不能是包名-->
        <service android:name="com.lyc.downloader.RemoteDownloadService"
                 android:process=":remote"/>
        ...
    </application>
</manifest>
```

推荐在`Application`中集成

``` kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = Configuration.Builder()
            // 是否使用多进程
            // 如果使用YCDownloader.install(Context)
            // 这个参数的值由你在manifest中指定的Service为准
            // 如果都指定了，为true
            // 如果使用YCDownloader.install(Context, Configuration)
            // 传入参数对应的Service必须指定
            .setMultiProcess(false)
            // 是否允许下载，如果不允许，所有任务都在等待状态
            // 这个参数可以用来做运营商网络等待wifi连接
            .setAllowDownload(true)
            // 是否控制进度更新消息
            .setAvoidFrameDrop(true)
            // 最大同时运行任务数量
            .setMaxRunningTask(4)
            // 通过Listener接收进度更新消息间隔，纳秒为单位
            .setSendMessageIntervalNanos(TimeUnit.MILLISECONDS.toNanos(333))
            // 限速，字节/秒为单位。如果传0或没有设置，不限速
//            .setSpeedLimit(2048 * 1024)
            .setSpeedLimit(0)
            .build()

        YCDownloader.install(this, config)

//        YCDownloader.install(this)
    }
}
```

## 混淆配置

`Proguard` 已经在依赖中配置，无须单独配置。

## 主要API

所有的API都可以在这个文件中看到： [YCDownloader.java](https://github.com/SirLYC/YC-Downloader/blob/master/downloader/src/main/java/com/lyc/downloader/YCDownloader.java)


**提交任务**
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
// path: 存储文件的路径
// filename: 可以是null；当为null时，文件名由下载器决定
YCDownloader.submit(url, path, filename, submitListener);
``` 

**监听下载进度**
``` java
DownloadListener downloadListener = ...;
YCDownloader.registerDownloadListener(downloadListener);

// 及时注销避免内存泄漏
// 比如在Activity.OnDestroy
YCDownloader.unregisterDownloadListener(downloadListener);
```

**查询任务信息**
``` java
// 注意：这些方法不能再主线程使用
// query by id
YCDownloader.queryDownloadInfo(long id);
// state != CANCELED && state != FINISH
YCDownloader.queryActiveDownloadInfoList();
// state == DELETED
YCDownloader.queryDeletedDownloadInfoList();
// state == FINISH
YCDownloader.queryFinishedDownloadInfoList();
```

**配置**

在下载器运行时，一些配置是可以改变的。这些方法都是线程安全的。

``` java
// 最大任务数设置为4
YCDownloader.setMaxRunningTask(4);

// 限速512KB/s
YCDownloader.setSpeedLimit(512 * 1024);

// 避免主线程接收过多进度更新消息而卡顿
YCDownloader.setAvoidFrameDrop(true);
//接收进度更新间隔
YCDownloader.setSendMessageInterval(500, TimeUnit.MILLISECONDS);

// 如果为false，所有任务都在等待状态
YCDownloaer.setAllowDownload(true)

// 批量更新参数，如果需要一次更新多个参数，这个方法效率更高
YCDownloader.updateByConfiguration(Configuration);
```

## Important classes

**YCDownloader**: [`com.lyc.downloader.YCDownloader`](https://github.com/SirLYC/YC-Downloader/blob/master/downloader/src/main/java/com/lyc/downloader/YCDownloader.java)

导出API。

**DownloadListener**: [`com.lyc.downloader.DownloadListener`](https://github.com/SirLYC/YC-Downloader/blob/master/downloader/src/main/java/com/lyc/downloader/DownloadListener.java)

监听进度更新。**所有的回调方法都在主线程调用。**

**DownloadTasksChangeListener**: [`com.lyc.downloader.DownloadTasksChangeListener`](https://github.com/SirLYC/YC-Downloader/blob/master/downloader/src/main/java/com/lyc/downloader/DownloadTasksChangeListener.java)

当任务管理器的任务添加或移除时的回调，可以用来监听更新列表变化。**所有的回调方法都在主线程调用。**

**SubmitListener**: [`com.lyc.downloader.SubmitListener`](https://github.com/SirLYC/YC-Downloader/blob/master/downloader/src/main/java/com/lyc/downloader/SubmitListener.java)

通知提交任务成功或失败。 **所有的回调方法都在主线程调用。**

**DownloadInfo**: [`com.lyc.downloader.db.DownloadInfo`](https://github.com/SirLYC/YC-Downloader/blob/master/downloader/src/main/java/com/lyc/downloader/db/DownloadInfo.java)

数据库实体类，也是 `Parcelable`类型，用于多进程通信。

**Configuration**: [`com.lyc.downloader.Configuration`](https://github.com/SirLYC/YC-Downloader/blob/master/downloader/src/main/java/com/lyc/downloader/Configuration.java)

一个不可变类型的class,定义参数。

## Permissions

```xml
<!--必须有-->
<uses-permission android:name="android.permission.INTERNET"/>
<!--没有会影响正常运行。6.0以上需要验证运行时权限-->
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
