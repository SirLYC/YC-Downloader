package com.lyc.downloader;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author liuyuchuan
 * @date 2019/4/7
 * @email kevinliu.sir@qq.com
 */
public class DownloadBuffer {
    private final BlockingQueue<Segment> readBufferQueue;
    private final BlockingQueue<Segment> writeBufferQueue;

    public DownloadBuffer(int bufferSize) {
        readBufferQueue = new ArrayBlockingQueue<>(2);
        writeBufferQueue = new ArrayBlockingQueue<>(2);
        for (int i = 0; i < 2; i++) {
            writeBufferQueue.add(new Segment(bufferSize));
        }
    }

    public Segment availableWriteSegment(long timeout) throws InterruptedException {
        if (timeout <= 0) {
            return writeBufferQueue.take();
        } else {
            return writeBufferQueue.poll(timeout, TimeUnit.SECONDS);
        }
    }

    public Segment availableReadSegment(long timeout) throws InterruptedException {
        if (timeout <= 0) {
            return readBufferQueue.take();
        } else {
            return readBufferQueue.poll(timeout, TimeUnit.SECONDS);
        }
    }

    public void enqueueReadSegment(Segment segment) {
        readBufferQueue.offer(segment);
    }

    public void enqueueWriteSegment(Segment segment) {
        writeBufferQueue.offer(segment);
    }

}
