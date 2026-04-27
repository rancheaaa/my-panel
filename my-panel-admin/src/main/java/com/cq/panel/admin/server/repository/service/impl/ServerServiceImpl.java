package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.common.utils.Arith;
import com.cq.panel.admin.server.common.utils.DateUtils;
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
import oshi.util.Util;
import java.lang.management.ManagementFactory;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.List;
import java.util.Properties;

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

        jvm.setTotal(Arith.div(totalMemory, (1024 * 1024), 2));
        jvm.setMax(Arith.div(maxMemory, (1024 * 1024), 2));
        jvm.setFree(Arith.div(freeMemory, (1024 * 1024), 2));
        jvm.setUsed(Arith.div(usedMemory, (1024 * 1024), 2));
        jvm.setUsage(Arith.mul(Arith.div(usedMemory, totalMemory, 4), 100));
        jvm.setVersion(props.getProperty("java.version"));
        jvm.setHome(props.getProperty("java.home"));
        jvm.setName(ManagementFactory.getRuntimeMXBean().getVmName());
        jvm.setPid(ProcessHandle.current().pid());
        jvm.setStartTime(DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, DateUtils.getServerStartDate()));
        jvm.setRunTime(DateUtils.timeDistance(new Date(), DateUtils.getServerStartDate()));
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
            sysFile.setUsage(Arith.mul(Arith.div(used, total, 4), 100));
            sysFiles.add(sysFile);
        }
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
