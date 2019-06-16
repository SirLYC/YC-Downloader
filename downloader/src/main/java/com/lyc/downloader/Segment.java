package com.lyc.downloader;

/**
 * @author liuyuchuan
 * @date 2019/4/7
 * @email kevinliu.sir@qq.com
 */
class Segment {
    final byte[] buffer;
    int readSize;
    long startPos;
    int tid;

    Segment(int bufferSize) {
        this.buffer = new byte[bufferSize];
    }
}
