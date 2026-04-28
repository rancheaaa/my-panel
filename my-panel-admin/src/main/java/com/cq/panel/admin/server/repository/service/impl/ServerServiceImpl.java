package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.common.utils.MyMathUtils;
import com.cq.panel.admin.server.common.utils.MyDateUtils;
import com.cq.panel.admin.server.common.utils.ip.MyIpUtils;
import com.cq.panel.admin.server.repository.service.IServerService;
import com.cq.panel.admin.server.web.domain.vo.monitor.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.CentralProcessor.TickType;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import oshi.software.os.OSProcess;
import oshi.util.Util;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.net.UnknownHostException;
import java.util.*;

/**
 * 服务器监控服务实现
 * 
 * @author cq
 */
@Service
public class ServerServiceImpl implements IServerService {

    private static final int OSHI_WAIT_SECOND = 1000;

    @Value("${server.port}")
    private int port;

    @Override
    public ServerVO getServerInfo() throws Exception {
        ServerVO serverVO = new ServerVO();
        
        SystemInfo si = new SystemInfo();
        HardwareAbstractionLayer hal = si.getHardware();

        setCpuInfo(hal.getProcessor(), serverVO.getCpu());

        setMemInfo(hal.getMemory(), serverVO.getMem());

        setSysInfo(serverVO.getSys());

        setJvmInfo(serverVO.getJvm());

        setSysFiles(si.getOperatingSystem(), serverVO.getSysFiles());
        
        return serverVO;
    }

    /**
     * 设置CPU信息
     */
    private void setCpuInfo(CentralProcessor processor, CpuVO cpu) {
        // CPU信息
        long[] prevTicks = processor.getSystemCpuLoadTicks();
        Util.sleep(OSHI_WAIT_SECOND);
        long[] ticks = processor.getSystemCpuLoadTicks();
        long nice = ticks[TickType.NICE.getIndex()] - prevTicks[TickType.NICE.getIndex()];
        long irq = ticks[TickType.IRQ.getIndex()] - prevTicks[TickType.IRQ.getIndex()];
        long softirq = ticks[TickType.SOFTIRQ.getIndex()] - prevTicks[TickType.SOFTIRQ.getIndex()];
        long steal = ticks[TickType.STEAL.getIndex()] - prevTicks[TickType.STEAL.getIndex()];
        long cSys = ticks[TickType.SYSTEM.getIndex()] - prevTicks[TickType.SYSTEM.getIndex()];
        long user = ticks[TickType.USER.getIndex()] - prevTicks[TickType.USER.getIndex()];
        long iowait = ticks[TickType.IOWAIT.getIndex()] - prevTicks[TickType.IOWAIT.getIndex()];
        long idle = ticks[TickType.IDLE.getIndex()] - prevTicks[TickType.IDLE.getIndex()];
        long totalCpu = user + nice + cSys + idle + iowait + irq + softirq + steal;
        cpu.setCpuNum(processor.getLogicalProcessorCount());
        cpu.setTotal(totalCpu);
        cpu.setSys(cSys);
        cpu.setUsed(user);
        cpu.setWait(iowait);
        cpu.setFree(idle);
    }

    /**
     * 设置内存信息
     */
    private void setMemInfo(GlobalMemory memory, MemVO mem) {
        mem.setTotal(memory.getTotal());
        mem.setUsed(memory.getTotal() - memory.getAvailable());
        mem.setFree(memory.getAvailable());
    }

    /**
     * 设置服务器信息
     */
    private void setSysInfo(SysVO sys) {
        Properties props = System.getProperties();
        sys.setComputerName(MyIpUtils.getHostName());
        sys.setComputerIp(MyIpUtils.getHostIp() + ":" + port);
        sys.setOsName(props.getProperty("os.name"));
        sys.setOsArch(props.getProperty("os.arch"));
        sys.setUserDir(props.getProperty("user.dir"));
    }

    /**
     * 设置Java虚拟机
     */
    private void setJvmInfo(JvmVO jvm) throws UnknownHostException {
        Properties props = System.getProperties();
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;

        jvm.setTotal(MyMathUtils.div(totalMemory, (1024 * 1024), 2));
        jvm.setMax(MyMathUtils.div(maxMemory, (1024 * 1024), 2));
        jvm.setFree(MyMathUtils.div(freeMemory, (1024 * 1024), 2));
        jvm.setUsed(MyMathUtils.div(usedMemory, (1024 * 1024), 2));
        jvm.setUsage(MyMathUtils.mul(MyMathUtils.div(usedMemory, totalMemory, 4), 100));
        jvm.setVersion(props.getProperty("java.version"));
        jvm.setHome(props.getProperty("java.home"));
        jvm.setName(ManagementFactory.getRuntimeMXBean().getVmName());
        jvm.setPid(ProcessHandle.current().pid());
        jvm.setStartTime(MyDateUtils.parseDateToStr(MyDateUtils.YYYY_MM_DD_HH_MM_SS, MyDateUtils.getServerStartDate()));
        jvm.setRunTime(MyDateUtils.timeDistance(new Date(), MyDateUtils.getServerStartDate()));
        jvm.setInputArgs(ManagementFactory.getRuntimeMXBean().getInputArguments().toString());
    }

    /**
     * 设置磁盘信息
     */
    private void setSysFiles(OperatingSystem os, List<SysFileVO> sysFiles) {
        FileSystem fileSystem = os.getFileSystem();
        List<OSFileStore> fsArray = fileSystem.getFileStores();
        for (OSFileStore fs : fsArray) {
            long free = fs.getUsableSpace();
            long total = fs.getTotalSpace();
            long used = total - free;
            SysFileVO sysFile = new SysFileVO();
            sysFile.setDirName(fs.getMount());
            sysFile.setSysTypeName(fs.getType());
            sysFile.setTypeName(fs.getName());
            sysFile.setTotal(convertFileSize(total));
            sysFile.setFree(convertFileSize(free));
            sysFile.setUsed(convertFileSize(used));
            sysFile.setUsage(MyMathUtils.mul(MyMathUtils.div(used, total, 4), 100));
            sysFiles.add(sysFile);
        }
    }

    @Override
    public ProcessMemoryVO getProcessMemoryInfo() {
        long rss = getProcessRss();
        List<ProcessMemoryVO.MemoryRegionVO> regions = analyzeMemoryRegions(rss);
        ProcessMemoryVO.KernelResourceDetail kernelResource = analyzeKernelResources();
        double rssMb = MyMathUtils.div(rss, (1024 * 1024), 2);
        return new ProcessMemoryVO(rss, rssMb, regions, kernelResource);
    }

    private long getProcessRss() {
        try {
            SystemInfo si = new SystemInfo();
            OSProcess process = si.getOperatingSystem().getProcess((int) ProcessHandle.current().pid());
            return process.getResidentSetSize();
        } catch (Exception e) {
            Runtime runtime = Runtime.getRuntime();
            return runtime.totalMemory() - runtime.freeMemory();
        }
    }

    private long getCurrentPid() {
        return ProcessHandle.current().pid();
    }

    private boolean isLinux() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("linux");
    }

    private List<ProcessMemoryVO.MemoryRegionVO> analyzeMemoryRegions(long rss) {
        List<ProcessMemoryVO.MemoryRegionVO> regions = new ArrayList<>();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long heapCommitted = heapUsage.getCommitted();
        addRegion(regions, "堆内存", heapCommitted);

        Map<String, Long> nonHeapMap = new LinkedHashMap<>();
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean pool : pools) {
            if (pool.getType().name().equals("NON_HEAP")) {
                String name = pool.getName();
                MemoryUsage usage = pool.getUsage();
                if (usage != null && usage.getUsed() > 0) {
                    nonHeapMap.merge(name, usage.getUsed(), Long::sum);
                }
            }
        }

        long metaspace = nonHeapMap.getOrDefault("Metaspace", 0L);
        if (metaspace > 0) {
            addRegion(regions, "元空间", metaspace);
        }

        final Long compressedClassSpace = nonHeapMap.getOrDefault("Compressed Class Space", 0L);
        if (compressedClassSpace > 0) {
            addRegion(regions, "Compressed Class Space", compressedClassSpace);
        }

        long codeCache = nonHeapMap.getOrDefault("Code Cache", 0L);
        if (codeCache > 0) {
            addRegion(regions, "代码缓存", codeCache);
        }

        int threadCount = threadMXBean.getThreadCount();
        long defaultStackSize = getDefaultThreadStackSize();
        long threadStackBytes = (long) threadCount * defaultStackSize;
        if (threadStackBytes > 0) {
            addRegion(regions, "线程栈", threadStackBytes);
        }

        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        long knownNonHeap = metaspace + codeCache;
        long otherNonHeap = Math.max(0, nonHeapUsage.getUsed() - knownNonHeap);
        if (otherNonHeap > 0) {
            addRegion(regions, "其他非堆内存", otherNonHeap);
        }

        ProcessMemoryVO.KernelResourceDetail kernel = analyzeKernelResources();
        long kernelTotal = kernel.getTotalKernelBytes();
        if (kernelTotal > 0) {
            addRegion(regions, "内核资源(句柄/Socket/映射)", kernelTotal);
        }

        long totalAccounted = regions.stream()
                .mapToLong(ProcessMemoryVO.MemoryRegionVO::getBytes)
                .sum();
        long overhead = Math.max(0, rss - totalAccounted);
        if (overhead > 0) {
            addRegion(regions, "JVM内部开销", overhead);
        }

        long finalTotal = regions.stream()
                .mapToLong(ProcessMemoryVO.MemoryRegionVO::getBytes)
                .sum();
        for (ProcessMemoryVO.MemoryRegionVO region : regions) {
            region.setPercent(MyMathUtils.mul(MyMathUtils.div(region.getBytes(), finalTotal, 4), 100));
        }
        return regions;
    }

    private ProcessMemoryVO.KernelResourceDetail analyzeKernelResources() {
        long fdCount = 0L;
        long fdBytes = 0;
        int socketCount = 0;
        long socketBytes = 0;
        int mappedFileCount = 0;
        long mappedFileBytes = 0;
        long sharedMemBytes = 0;
        long pageTableBytes = 0;

        if (isLinux()) {
            long pid = getCurrentPid();
            fdCount = countOpenFilesFromProc(pid);
            socketCount = countSocketsFromProc(pid);
            fdBytes = estimateFileDescriptorMemory(fdCount);
            socketBytes = estimateSocketBufferMemory(socketCount);
            MappedFileResult mappedResult = analyzeMappedFilesFromProc(pid);
            mappedFileCount = mappedResult.count;
            mappedFileBytes = mappedResult.bytes;
            sharedMemBytes = analyzeSharedMemoryFromProc(pid);
            pageTableBytes = readPageTableFromStatus(pid);
        }

        long totalKernel = fdBytes + socketBytes + mappedFileBytes + sharedMemBytes + pageTableBytes;

        return new ProcessMemoryVO.KernelResourceDetail(
                (int) fdCount, fdBytes, toMb(fdBytes),
                socketCount, socketBytes, toMb(socketBytes),
                mappedFileCount, mappedFileBytes, toMb(mappedFileBytes),
                sharedMemBytes, toMb(sharedMemBytes),
                pageTableBytes, toMb(pageTableBytes),
                totalKernel, toMb(totalKernel)
        );
    }

    private long countOpenFilesFromProc(long pid) {
        File fdDir = new File("/proc/" + pid + "/fd");
        if (!fdDir.exists()) return 0;
        File[] files = fdDir.listFiles();
        return files != null ? files.length : 0;
    }

    private int countSocketsFromProc(long pid) {
        int count = 0;
        File fdDir = new File("/proc/" + pid + "/fd");
        if (!fdDir.exists()) return 0;
        try {
            File[] files = fdDir.listFiles();
            if (files == null) return 0;
            for (File f : files) {
                String target = java.nio.file.Files.readSymbolicLink(f.toPath()).toString();
                if (target.startsWith("socket:")) {
                    count++;
                }
            }
        } catch (Exception ignored) {
        }
        return count;
    }

    private static class MappedFileResult {
        int count;
        long bytes;
    }

    private MappedFileResult analyzeMappedFilesFromProc(long pid) {
        MappedFileResult result = new MappedFileResult();
        File mapsFile = new File("/proc/" + pid + "/maps");
        if (!mapsFile.exists()) return result;
        try (BufferedReader reader = new BufferedReader(new FileReader(mapsFile))) {
            String line;
            Set<String> seenPaths = new HashSet<>();
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 6) {
                    String path = parts[5];
                    if (!path.isEmpty() && !path.startsWith("[") && !path.equals("(deleted)")) {
                        if (seenPaths.add(path)) {
                            result.count++;
                            try {
                                String[] addrRange = parts[0].split("-");
                                long start = Long.parseLong(addrRange[0], 16);
                                long end = Long.parseLong(addrRange[1], 16);
                                result.bytes += (end - start);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    if (path.equals("[shm]") || path.contains("/dev/shm") || path.startsWith("/SYSV")) {
                        try {
                            String[] addrRange = parts[0].split("-");
                            long start = Long.parseLong(addrRange[0], 16);
                            long end = Long.parseLong(addrRange[1], 16);
                            result.bytes += (end - start);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private long analyzeSharedMemoryFromProc(long pid) {
        long sharedMem = 0;
        File smapsFile = new File("/proc/" + pid + "/smaps_rollup");
        if (!smapsFile.exists()) {
            smapsFile = new File("/proc/" + pid + "/smaps");
        }
        if (!smapsFile.exists()) return 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(smapsFile))) {
            String line;
            boolean inShm = false;
            while ((line = reader.readLine()) != null) {
                if (line.contains("[shm]") || line.contains("/dev/shm") || line.contains("Shared_Mem")) {
                    inShm = true;
                }
                if (inShm && line.startsWith("Rss:")) {
                    String kbStr = line.replaceAll("\\D+", "");
                    sharedMem += Long.parseLong(kbStr) * 1024L;
                }
                if (line.isEmpty()) inShm = false;
            }
        } catch (Exception ignored) {
        }
        return sharedMem;
    }

    private long readPageTableFromStatus(long pid) {
        File statusFile = new File("/proc/" + pid + "/status");
        if (!statusFile.exists()) return 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(statusFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("VmPTE:")) {
                    String kbStr = line.replaceAll("\\D+", "");
                    return Long.parseLong(kbStr) * 1024L;
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private long estimateFileDescriptorMemory(long fdCount) {
        return fdCount * 1536L;
    }

    private long estimateSocketBufferMemory(int socketCount) {
        return (long) socketCount * 256L * 1024L;
    }

    private long estimateMappedFileMemory(long fdCount) {
        return Math.min(fdCount * 64L * 1024L, 10L * 1024L * 1024L);
    }

    private double toMb(long bytes) {
        return MyMathUtils.div(bytes, (1024 * 1024), 2);
    }

    private void addRegion(List<ProcessMemoryVO.MemoryRegionVO> regions, String name, long bytes) {
        double mb = MyMathUtils.div(bytes, (1024 * 1024), 2);
        regions.add(new ProcessMemoryVO.MemoryRegionVO(name, bytes, mb, 0.0));
    }

    private long getDefaultThreadStackSize() {
        try {
            String xss = ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                    .filter(arg -> arg.startsWith("-Xss"))
                    .findFirst()
                    .orElse(null);
            if (xss != null) {
                String sizeStr = xss.substring(4).toUpperCase().replace("K", "").replace("M", "")
                        .replace("G", "");
                sizeStr = sizeStr.replace("g", "").replace("m", "").replace("k", "");
                long value = Long.parseLong(sizeStr);
                if (xss.contains("K") || xss.contains("k")) return value * 1024;
                if (xss.contains("M") || xss.contains("m")) return value * 1024 * 1024;
                if (xss.contains("G") || xss.contains("g")) return value * 1024 * 1024 * 1024;
                return value;
            }
        } catch (Exception ignored) {
        }
        return 1024L * 1024;
    }

    /**
     * 字节转换
     * 
     * @param size 字节大小
     * @return 转换后值
     */
    public String convertFileSize(long size) {
        long kb = 1024;
        long mb = kb * 1024;
        long gb = mb * 1024;
        if (size >= gb) {
            return String.format("%.1f GB", (float) size / gb);
        } else if (size >= mb) {
            float f = (float) size / mb;
            return String.format(f > 100 ? "%.0f MB" : "%.1f MB", f);
        } else if (size >= kb) {
            float f = (float) size / kb;
            return String.format(f > 100 ? "%.0f KB" : "%.1f KB", f);
        } else {
            return String.format("%d B", size);
        }
    }
}
