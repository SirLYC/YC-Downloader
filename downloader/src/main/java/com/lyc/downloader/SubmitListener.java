package com.lyc.downloader;

import com.lyc.downloader.db.DownloadInfo;

public interface SubmitListener {
    void submitSuccess(DownloadInfo downloadInfo);

    void submitFail(Exception e);
}

