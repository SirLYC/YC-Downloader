// ISubmitCallback.aidl
package com.lyc.downloader;

import com.lyc.downloader.db.DownloadInfo;

interface ISubmitCallback {

    void submitSuccess(in DownloadInfo downloadInfo);

    void submitFail(String reason);
}
