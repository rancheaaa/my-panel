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

    /**
     * 原始绝对路径（隐藏文件前的路径）。
     * 当文件被标记为传输中（重命名为隐藏文件）时，此字段保存原始路径，
     * absolutePath 则更新为隐藏后的路径。
     * 如果文件未被隐藏，此字段为null。
     */
    private String originalAbsolutePath;

    /**
     * 获取原始绝对路径，如果未设置则返回当前绝对路径
     */
    public String getOriginalAbsolutePath() {
        return originalAbsolutePath != null ? originalAbsolutePath : absolutePath;
    }
}
