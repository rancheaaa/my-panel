package com.cq.panel.admin.server.listener;

import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.common.utils.SystemEnvUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

@Component
public class AdminPortListener {

    private static final Logger logger = LoggerFactory.getLogger(AdminPortListener.class);

    private volatile String appRootPath;

    @EventListener
    public void portReady(WebServerInitializedEvent event) {
        int realPort = event.getWebServer().getPort();
        String springSqlInitMode = SystemEnvUtils.getSpringSqlInitMode();
        final String projectRootPath = SystemEnvUtils.getProjectRootPath();
        logger.info("SPRING_SQL_INIT_MODE:[{}]", springSqlInitMode);
        logger.info("PROJECT_ROOT_PATH:[{}]", projectRootPath);
        logger.info("✅ my-panel-admin 实际启动端口:[{}]", realPort);
        if (StringUtils.isEmpty(projectRootPath)) {
            return;
        }
        try {
            appRootPath = projectRootPath;
            logger.info("项目根目录:[{}]", appRootPath);
            final File initFile = Paths.get(appRootPath, ".init.txt").toAbsolutePath().toFile();
            if (!initFile.exists()) {
                final boolean result = initFile.createNewFile();
                logger.info("第一次初始化程序数据库，创建初始化标志完成文件.init.txt :[{}]", result ? "成功" : "失败");
            } else {
                logger.info("初始化标志完成文件.init.txt 已存在，数据库已初始化成功");
            }
        } catch (IOException e) {
            logger.error("创建初始化标志完成文件.init.txt 失败", e);
        }
    }
}