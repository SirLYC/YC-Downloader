package com.lyc.downloader;

import java.io.File;

/**
 * @author liuyuchuan
 * @date 2019/4/23
 * @email kevinliu.sir@qq.com
 */
public class FileExitsException extends Exception {
    public final File file;

    FileExitsException(File file) {
        this.file = file;
    }
}
