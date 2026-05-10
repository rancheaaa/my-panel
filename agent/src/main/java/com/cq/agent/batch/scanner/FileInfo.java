package com.cq.agent.batch.scanner;

import java.nio.file.Path;

/**
 * 文件信息
 */
public class FileInfo {
    private Path path;
    private String fileName;
    private long size;
    private long lastModified;
    
    public FileInfo() {}
    
    public FileInfo(Path path, String fileName, long size, long lastModified) {
        this.path = path;
        this.fileName = fileName;
        this.size = size;
        this.lastModified = lastModified;
    }
    
    public Path getPath() { return path; }
    public void setPath(Path path) { this.path = path; }
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    
    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }
    
    @Override
    public String toString() {
        return "FileInfo{path=" + path + ", name=" + fileName + 
               ", size=" + size + ", modified=" + lastModified + "}";
    }
}
