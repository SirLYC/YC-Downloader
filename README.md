# Yuchuan-Downloader

[![](https://jitpack.io/v/SirLYC/Yuchuan-Downloader.svg)](https://jitpack.io/#SirLYC/Yuchuan-Downloader)

A multi-thread downloader which supports for HTTP.

## Features
- [x] HTTP/HTTPS download
- [x] multi-thread download
- [x] download thread and disk-io thread separated
- [x] multi download task
- [x] support for HTTP (resume from break-poAint)
- [x] message control to avoid ui frame drops 
- [x] multi-process support
- [ ] other protocol download maybe...

## Run
**`app` module** 
    
    A simple apk instance which uses download library.

**`downloader` module**
    
    Download library.


## Install
**Step 1.** Add the JitPack repository to your build file

Add it in your root build.gradle at the end of repositories:

```
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

**Step 2.** Add the dependency

```
dependencies {
    implementation 'com.github.SirLYC:Yuchuan-Downloader:latest.release'
}
```

> [Check release notes here](https://github.com/SirLYC/Yuchuan-Downloader/releases)

**Step3.** Install YCDownloader

It's recommended to install in `Application` class

``` kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // multi process
        YCDownloader.install(this, true)
        // single process
        //        YCDownloader.install(this, false);
        // or
        //        YCDownloader.install(this);
    }
}
```

Then Just learn apis!

## Main API

You can check all apis in file [YCDownloader.java](https://github.com/SirLYC/Yuchuan-Downloader/blob/master/downloader/src/main/java/com/lyc/downloader/YCDownloader.java)

**install**
```
// it is recommended to install it in your Application
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
```

**start download**
```
private SubmitListener submitListener = new SubmitListener() {
        @Override
        public void submitSuccess(DownloadInfo downloadInfo) {
            Log.d("Submit", "success submit, id = " + downloadInfo.getId());
        }

        @Override
        public void submitFail(Exception e) {
            Log.e("Submit", "success failed", e);
        }
};
// path: parent directory to store your file
// filename: can be null; if not null, downloader will use it to save your file
YCDownloader.submit(url, path, filename, submitListener);
``` 

**listen to download progress or state change**
```
DownloadListener downloadListener = ...;
YCDownloader.registerDownloadListener(downloadListener);

// you should unregister it to avoid memory leak
// such as Activity.OnDestroy
YCDownloader.unregisterDownloadListener(downloadListener);
```

**query download info**
```
// attention: these methods should be called in worker thread
// query by id
YCDownloader.queryDownloadInfo(long id);
// state != CANCELED && state != FINISHED
YCDownloader.queryActiveDownloadInfoList();
// state == DELETED
YCDownloader.queryDeletedDownloadInfoList();
// state == FINISHED
YCDownloader.queryFinishedDownloadInfoList();
```

**other api**
- set max running task
- set if you want to avoid main thread receive too much progress update message
- other... 

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
