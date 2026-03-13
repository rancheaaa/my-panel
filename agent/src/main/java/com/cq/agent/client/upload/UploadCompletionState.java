package com.cq.agent.client.upload;

/**
 * Represents the completion state of an upload after all chunks have been sent.
 */
public enum UploadCompletionState {
    /**
     * The merge API call was successful, but the final file has not yet been verified by the client.
     */
    MERGED,
    /**
     * The client has verified that the final file exists on the remote server after the merge.
     */
    VERIFIED
}
