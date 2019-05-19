// ISubmitCallback.aidl
package com.lyc.downloader;

import com.lyc.downloader.db.DownloadInfo;

interface ISubmitCallback {

    void submitSuccess(inout DownloadInfo downloadInfo);

    void submitFail(String reason);
}
