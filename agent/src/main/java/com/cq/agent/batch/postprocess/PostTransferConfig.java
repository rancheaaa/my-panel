package com.cq.agent.batch.postprocess;

import lombok.Data;

@Data
public class PostTransferConfig
{
    private String action;
    private String sourceBaseDir;
    private String backupDir;
    private String backupMode;
    private boolean preserveDirStructure = true;
}
