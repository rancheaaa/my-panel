package com.cq.agent.batch.scanner;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 *
 * @author cq 2026/5/13 15:54
 * @since 1.0.0
 */
@Data
@ToString
@EqualsAndHashCode
public class ScannedFile {

    private String fileName;
    private long fileSize;
    private long lastModified;
    private String absolutePath;
}
