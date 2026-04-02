package com.cq.agent.dto;

import lombok.Data;

/**
 * Response for RETR (retrieve file as base64).
 */

@Data
public class RetrResponse {

    private boolean success;
    private String data;
    private int size;
    private String encoding;

}
