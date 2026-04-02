package com.cq.agent.dto;

import lombok.Data;

/**
 * Request body for store unique file.
 */

@Data
public class StouRequest {

    private String directory;
    private String prefix;
    private String content;
    private String encoding;

}
