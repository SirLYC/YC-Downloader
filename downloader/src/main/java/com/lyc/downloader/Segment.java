package com.lyc.downloader;

/**
 * @author liuyuchuan
 * @date 2019/4/7
 * @email kevinliu.sir@qq.com
 */
public class Segment {
    public final byte[] buffer;
    public int readSize;
    public long startPos;

    public Segment(int bufferSize) {
        if (bufferSize <= 0) {
            buffer = null;
        } else {
            this.buffer = new byte[bufferSize];
        }
    }
}
