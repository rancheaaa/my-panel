package com.cq.agent.batch.scanner;

import lombok.Data;
import java.util.Date;

@Data
public class ScannedFile
{
    private String relativePath;
    private String absolutePath;
    private long sizeBytes;
    private Date lastModified;
    private String md5;
}
