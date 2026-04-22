package com.cq.panel.common.utils;

/**
 *
 * @author cq 2026/4/22 9:20
 * @since 1.0.0
 */
public class SystemEnvUtils {

    public static final String PROJECT_ROOT_PATH_KEY = "ROOT_DIR";

    public static String getProjectRootPath() {
        return System.getenv(PROJECT_ROOT_PATH_KEY);
    }
}
