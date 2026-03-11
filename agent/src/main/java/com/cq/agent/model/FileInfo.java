package com.cq.agent.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * File information model.
 */
public class FileInfo {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private String name;
    private String path;
    private boolean directory;
    private long size;
    private String modifiedTime;
    private long modifiedTimestamp;
    private String permissions;
    private boolean readable;
    private boolean writable;
    private boolean executable;
    private boolean hidden;

    public FileInfo() {
    }

    public static FileInfo fromPath(Path path) throws IOException {
        FileInfo info = new FileInfo();
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);

        info.name = path.getFileName() != null ? path.getFileName().toString() : path.toString();
        info.path = path.toAbsolutePath().toString();
        info.directory = attrs.isDirectory();
        info.size = attrs.size();
        info.modifiedTimestamp = attrs.lastModifiedTime().toMillis();
        info.modifiedTime = DATE_FORMAT.format(Instant.ofEpochMilli(info.modifiedTimestamp));
        info.readable = Files.isReadable(path);
        info.writable = Files.isWritable(path);
        info.executable = Files.isExecutable(path);
        info.hidden = Files.isHidden(path);

        try {
            info.permissions = PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
        } catch (UnsupportedOperationException e) {
            // Not a POSIX file system
            info.permissions = buildPermissionString(info);
        }

        return info;
    }

    private static String buildPermissionString(FileInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append(info.readable ? "r" : "-");
        sb.append(info.writable ? "w" : "-");
        sb.append(info.executable ? "x" : "-");
        return sb.toString();
    }

    // Getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isDirectory() {
        return directory;
    }

    public void setDirectory(boolean directory) {
        this.directory = directory;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getModifiedTime() {
        return modifiedTime;
    }

    public void setModifiedTime(String modifiedTime) {
        this.modifiedTime = modifiedTime;
    }

    public long getModifiedTimestamp() {
        return modifiedTimestamp;
    }

    public void setModifiedTimestamp(long modifiedTimestamp) {
        this.modifiedTimestamp = modifiedTimestamp;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public boolean isReadable() {
        return readable;
    }

    public void setReadable(boolean readable) {
        this.readable = readable;
    }

    public boolean isWritable() {
        return writable;
    }

    public void setWritable(boolean writable) {
        this.writable = writable;
    }

    public boolean isExecutable() {
        return executable;
    }

    public void setExecutable(boolean executable) {
        this.executable = executable;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}
