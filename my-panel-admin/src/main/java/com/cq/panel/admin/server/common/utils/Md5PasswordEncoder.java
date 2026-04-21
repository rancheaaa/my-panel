package com.cq.panel.admin.server.common.utils;

import com.cq.panel.admin.server.common.utils.sign.Md5Utils;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class Md5PasswordEncoder {

    private static final Logger log = LoggerFactory.getLogger(Md5PasswordEncoder.class);

    public static String generateSalt() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String encryptPassword(String rawPassword, String salt) {
        if (salt == null || salt.isEmpty()) {
            return Md5Utils.hash(rawPassword);
        }
        return Md5Utils.hash(Md5Utils.hash(rawPassword) + salt);
    }

    public static boolean matches(String rawPassword, String encodedPassword) {
        if (encodedPassword == null || rawPassword == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, encodedPassword);
        } catch (Exception e) {
            log.error("密码匹配失败, 原始密码: {}, 编码后的密码: {}", rawPassword, encodedPassword, e);
            return false;
        }
    }
}