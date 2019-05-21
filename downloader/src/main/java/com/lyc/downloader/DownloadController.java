package com.lyc.downloader;

import java.util.Map;

/**
 * Created by Liu Yuchuan on 2019/5/18.
 */
public interface DownloadController {
    void startOrResume(long id, boolean restart);

    void pause(long id);

    void startAll();

    void pauseAll();

    void cancel(long id);

    void submit(String url, String path, String filename, Map<String, String> customerHeaders, SubmitListener listener);

    void delete(long id, boolean deleteFile);
}
