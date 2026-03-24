package com.cq.agent.client.download;

public enum DownloadTaskStatus {

    PREPARED,
    INIT_DOWNLOADING,
    INIT_DOWNLOAD_COMPLETED,
    DOWNLOADING_CHUNKS,
    DOWNLOAD_CHUNKS_COMPLETED,
    MERGING,
    VERIFYING,
    DOWNLOAD_SUCCESS,
    FAILED
}