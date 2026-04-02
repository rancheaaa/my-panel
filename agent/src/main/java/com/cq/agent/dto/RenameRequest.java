package com.cq.agent.dto;

import lombok.Data;

/**
 * Request body for rename.
 */

@Data
public class RenameRequest {

    private String from;
    private String to;

}
