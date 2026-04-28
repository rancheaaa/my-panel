package com.cq.panel.admin.server.web.domain.vo.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "进程内存分布信息")
public class ProcessMemoryVO {
    @Schema(description = "进程RSS(常驻物理内存)大小(bytes)")
    private long rss;

    @Schema(description = "进程RSS(MB)")
    private double rssMb;

    @Schema(description = "各内存区域分布")
    private List<MemoryRegionVO> regions;

    @Schema(description = "内核资源详情")
    private KernelResourceDetail kernelResource;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "内核资源详情")
    public static class KernelResourceDetail {
        @Schema(description = "打开的文件描述符/句柄数")
        private long openFileDescriptors;

        @Schema(description = "文件描述符占用内存估算(bytes)", example = "5242880")
        private long fileDescriptorBytes;

        @Schema(description = "文件描述符占用(MB)")
        private double fileDescriptorMb;

        @Schema(description = "活跃Socket连接数")
        private int socketCount;

        @Schema(description = "Socket缓冲区占用内存估算(bytes)")
        private long socketBufferBytes;

        @Schema(description = "Socket缓冲区占用(MB)")
        private double socketBufferMb;

        @Schema(description = "内存映射文件数")
        private int mappedFileCount;

        @Schema(description = "内存映射文件占用(bytes)")
        private long mappedFileBytes;

        @Schema(description = "内存映射文件占用(MB)")
        private double mappedFileMb;

        @Schema(description = "共享内存段大小(bytes)")
        private long sharedMemoryBytes;

        @Schema(description = "共享内存占用(MB)")
        private double sharedMemoryMb;

        @Schema(description = "页表占用(bytes)")
        private long pageTableBytes;

        @Schema(description = "页表占用(MB)")
        private double pageTableMb;

        @Schema(description = "内核资源总占用(bytes)")
        private long totalKernelBytes;

        @Schema(description = "内核资源总占用(MB)")
        private double totalKernelMb;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "内存区域")
    public static class MemoryRegionVO {
        @Schema(description = "区域名称", example = "堆内存")
        private String name;

        @Schema(description = "占用大小(bytes)", example = "536870912")
        private long bytes;

        @Schema(description = "占用大小(MB)", example = "512.0")
        private double mb;

        @Schema(description = "占比(%)", example = "45.2")
        private double percent;
    }
}
