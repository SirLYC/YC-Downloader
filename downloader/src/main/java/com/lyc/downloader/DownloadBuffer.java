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
    private final DownloadListener downloadListener;
    private final long minInformInterval;
    public volatile long lastUpdateSpeedTime = -1;
    private volatile long downloadSize;

    public DownloadBuffer(int threadCount, int bufferSize, DownloadListener listener, long minInforInterval) {
        readBufferQueue = new ArrayBlockingQueue<>(threadCount);
        writeBufferQueue = new ArrayBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            writeBufferQueue.add(new Segment(bufferSize));
        }
        downloadListener = listener;
        this.minInformInterval = minInforInterval;
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
        if (segment.readSize > 0) {
            synchronized (this) {
                long cur = System.nanoTime();
                long interval = cur - lastUpdateSpeedTime;
                downloadSize += segment.readSize;
                if (interval > minInformInterval) {
                    lastUpdateSpeedTime = cur;
                    downloadListener.onSpeedChange(downloadSize / (interval / 1000000000.0));
                    downloadSize = 0;
                }
            }
        }
        readBufferQueue.offer(segment);
    }

    public void enqueueWriteSegment(Segment segment) {
        writeBufferQueue.offer(segment);
    }
}
