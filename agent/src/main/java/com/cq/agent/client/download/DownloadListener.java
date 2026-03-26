package com.cq.agent.client.download;

public interface DownloadListener {

    default void onBeforeSend(DownloadTask task) {};

    void onProgress(int total, int downloaded, double progress);

    void onComplete(DownloadTask result);

    void onError(String message);
}