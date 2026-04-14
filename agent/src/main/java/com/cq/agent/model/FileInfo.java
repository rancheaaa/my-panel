package com.cq.agent.model;

import lombok.Data;

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
@Data
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
        return (info.readable ? "r" : "-") +
                (info.writable ? "w" : "-") +
                (info.executable ? "x" : "-");
    }
}
