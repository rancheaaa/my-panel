package com.cq.agent.client.upload;

import com.cq.agent.client.download.DownloadTask;

/**
 * Listener for tracking upload progress.
 */
public interface UploadListener {

    default void onBeforeSend(UploadTask task) {};

    /**
     * Called when the upload progress changes.
     *
     * @param totalChunks     Total number of chunks for the file.
     * @param uploadedChunks  Number of chunks already uploaded.
     * @param progress        The progress percentage (0.0 to 100.0).
     */
    void onProgress(int totalChunks, int uploadedChunks, double progress);

    /**
     * Called when the upload is successfully completed (after merging).
     *
     * @param task The result of the successful upload.
     */
    void onComplete(UploadTask task);

    /**
     * Called when an error occurs during the upload.
     *
     * @param errorMessage A message describing the error.
     */
    void onError(String errorMessage);
}
