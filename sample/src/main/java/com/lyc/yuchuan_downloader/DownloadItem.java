package com.lyc.yuchuan_downloader;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

/**
 * @author liuyuchuan
 * @date 2019-04-23
 * @email kevinliu.sir@qq.com
 */
@Data
@AllArgsConstructor
public class DownloadItem {
    private long id;
    private String path;
    private String filename;
    private String url;
    private double bps;
    private long totalSize;
    private long downloadedSize;
    private Date createdTime;
    private Date finishedTime;
    private int downloadState;
    private String errorMessage;
}
