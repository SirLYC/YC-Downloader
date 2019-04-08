package com.lyc.downloader;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * @author liuyuchuan
 * @date 2019/4/7
 * @email kevinliu.sir@qq.com
 */
public class DownloadBuffer {
    private final BlockingQueue<Segment> readBufferQueue;
    private final BlockingQueue<Segment> writeBufferQueue;

    public DownloadBuffer(int threadCount, int bufferSize) {
        readBufferQueue = new ArrayBlockingQueue<>(threadCount);
        writeBufferQueue = new ArrayBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            writeBufferQueue.add(new Segment(bufferSize));
        }
    }

    public Segment availableWriteSegment() throws InterruptedException {
        return writeBufferQueue.take();
    }

    public Segment availableReadSegment() throws InterruptedException {
        return readBufferQueue.take();
    }

    public void enqueueReadSegment(Segment segment) {
        readBufferQueue.offer(segment);
    }

    public void enqueueWriteSegment(Segment segment) {
        writeBufferQueue.offer(segment);
    }
}
