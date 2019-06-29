package com.lyc.downloader;

import java.util.concurrent.TimeUnit;

/**
 * @author liuyuchuan
 * @date 2019-06-28
 * @email kevinliu.sir@qq.com
 */
public final class Configuration {
    public static int MAX_SUPPORT_TASK_COUNT = Math.max(Runtime.getRuntime().availableProcessors(), 1) * 4;
    public static int DEFAULT_MAX_RUNNING_TASK = Math.min(MAX_SUPPORT_TASK_COUNT, 4);
    public static boolean DEFAULT_ALLOW_DOWNLOAD = true;
    public static boolean DEFAULT_AVOID_FRAME_DROP = true;
    public static long DEFAULT_SEND_MESSAGE_INTERVAL = TimeUnit.MILLISECONDS.toNanos(333);
    public static int DEFAULT_SPEED_LIMIT = 0;
    public static boolean DEFAULT_MULTI_PROCESS = true;


    final int maxRunningTask;
    final long speedLimit;
    final boolean allowDownload;
    final boolean avoidFrameDrop;
    final long sendMessageIntervalNanos;
    final boolean multiProcess;

    private Configuration(int maxRunningTask, long speedLimit, boolean allowDownload, boolean avoidFrameDrop, long sendMessageIntervalNanos, boolean multiProcess) {
        this.maxRunningTask = maxRunningTask;
        this.speedLimit = speedLimit;
        this.allowDownload = allowDownload;
        this.avoidFrameDrop = avoidFrameDrop;
        this.sendMessageIntervalNanos = sendMessageIntervalNanos;
        this.multiProcess = multiProcess;
    }

    public static class Builder {
        private int maxRunningTask = DEFAULT_MAX_RUNNING_TASK;
        private long speedLimit = DEFAULT_SPEED_LIMIT;
        private boolean allowDownload = DEFAULT_ALLOW_DOWNLOAD;
        private boolean avoidFrameDrop = DEFAULT_AVOID_FRAME_DROP;
        private long sendMessageIntervalNanos = DEFAULT_SEND_MESSAGE_INTERVAL;
        private boolean multiProcess = DEFAULT_MULTI_PROCESS;

        public Builder setMaxRunningTask(int maxRunningTask) {
            this.maxRunningTask = Math.max(0, maxRunningTask);
            return this;
        }

        public Builder setSpeedLimit(long speedLimit) {
            this.speedLimit = Math.max(0, speedLimit);
            return this;
        }

        public Builder setAllowDownload(boolean allowDownload) {
            this.allowDownload = allowDownload;
            return this;
        }

        public Builder setAvoidFrameDrop(boolean avoidFrameDrop) {
            this.avoidFrameDrop = avoidFrameDrop;
            return this;
        }

        public Builder setSendMessageIntervalNanos(long sendMessageIntervalNanos) {
            this.sendMessageIntervalNanos = Math.max(sendMessageIntervalNanos, TimeUnit.MILLISECONDS.toNanos(16));
            return this;
        }

        public Builder setMultiProcess(boolean multiProcess) {
            this.multiProcess = multiProcess;
            return this;
        }

        public Configuration build() {
            return new Configuration(maxRunningTask, speedLimit, allowDownload, avoidFrameDrop, sendMessageIntervalNanos, multiProcess);
        }
    }
}
