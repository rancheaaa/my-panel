package com.cq.panel.admin.server;

import com.cq.panel.admin.server.common.utils.Md5PasswordEncoder;
import com.cq.panel.admin.server.common.utils.sign.Md5Utils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Md5PasswordEncoder 加密算法测试")
@Slf4j
class Md5PasswordEncoderTest {

    private static final String SALT = "1234567890123456";

    @Nested
    @DisplayName("MD5第一次哈希测试")
    class FirstHashTests {

        @Test
        @DisplayName("admin123的MD5哈希值应为0192023a7bbd73250516f069df18b500")
        void admin123FirstHash() {
            String result = Md5Utils.hash("admin123");
            assertEquals("0192023a7bbd73250516f069df18b500", result);
        }

        @Test
        @DisplayName("guest123的MD5哈希值应为fcf41657f02f88137a1bcf068a32c0a3")
        void guest123FirstHash() {
            String result = Md5Utils.hash("guest123");
            assertEquals("fcf41657f02f88137a1bcf068a32c0a3", result);
        }

        @Test
        @DisplayName("cq123456的MD5哈希值应为a74863fbe920b4e7fded049541ddbf7d")
        void cq123456FirstHash() {
            String result = Md5Utils.hash("cq123456");
            assertEquals("a74863fbe920b4e7fded049541ddbf7d", result);
        }
    }

    @Nested
    @DisplayName("MD5+Salt加密测试")
    class EncryptPasswordTests {

        @Test
        @DisplayName("admin123加密后应为4abe69d979b4691367bc1affd2afc596")
        void admin123Encrypted() {
            String result = Md5PasswordEncoder.encryptPassword("admin123", SALT);
            assertEquals("4abe69d979b4691367bc1affd2afc596", result);
        }

        @Test
        @DisplayName("guest123加密后应为c994a2483fffd4a53b68873832856b82")
        void guest123Encrypted() {
            String result = Md5PasswordEncoder.encryptPassword("guest123", SALT);
            assertEquals("c994a2483fffd4a53b68873832856b82", result);
        }

        @Test
        @DisplayName("cq123456加密后应为48ab697edd9e6595fea6371109cea04b")
        void cq123456Encrypted() {
            String result = Md5PasswordEncoder.encryptPassword("cq123456", SALT);
            assertEquals("48ab697edd9e6595fea6371109cea04b", result);
        }
    }

    @Nested
    @DisplayName("密码验证测试(BCrypt双重加密)")
    class MatchesTests {

        @Test
        @DisplayName("BCrypt验证：md5+salt加密后应匹配BCrypt哈希")
        void admin123WithBcryptMatches() {
            String md5WithSalt = Md5PasswordEncoder.encryptPassword("admin123", SALT);
            String bcryptHash = BCrypt.hashpw(md5WithSalt, BCrypt.gensalt());
            boolean result = Md5PasswordEncoder.matches(md5WithSalt, bcryptHash);
            assertTrue(result);
        }

        @Test
        @DisplayName("BCrypt验证：不同密码不应匹配")
        void differentPasswordShouldNotMatch() {
            String md5WithSalt = Md5PasswordEncoder.encryptPassword("admin123", SALT);
            String bcryptHash = BCrypt.hashpw(md5WithSalt, BCrypt.gensalt());
            String differentMd5 = Md5PasswordEncoder.encryptPassword("otherpass", SALT);
            boolean result = Md5PasswordEncoder.matches(differentMd5, bcryptHash);
            assertFalse(result);
        }

        @Test
        @DisplayName("BCrypt验证：相同md5+salt值应正确验证")
        void sameMd5SaltShouldMatch() {
            String md5WithSalt = Md5PasswordEncoder.encryptPassword("guest123", SALT);
            String bcryptHash = BCrypt.hashpw(md5WithSalt, BCrypt.gensalt());
            boolean result = BCrypt.checkpw(md5WithSalt, bcryptHash);
            assertTrue(result);
        }

        @Test
        @DisplayName("打印BCrypt哈希值")
        public void printBcryptHash() {
            String md5WithSalt = Md5PasswordEncoder.encryptPassword("admin123", SALT);
            String bcryptHash = BCrypt.hashpw(md5WithSalt, BCrypt.gensalt());
            log.info("原始密码 admin123 BCrypt哈希值: {}", bcryptHash);

            md5WithSalt = Md5PasswordEncoder.encryptPassword("guest123", SALT);
            bcryptHash = BCrypt.hashpw(md5WithSalt, BCrypt.gensalt());
            log.info("原始密码 guest123 BCrypt哈希值: {}", bcryptHash);

            md5WithSalt = Md5PasswordEncoder.encryptPassword("cq123456", SALT);
            bcryptHash = BCrypt.hashpw(md5WithSalt, BCrypt.gensalt());
            log.info("原始密码 cq123456 BCrypt哈希值: {}", bcryptHash);
        }
    }

    @Nested
    @DisplayName("Salt生成测试")
    class GenerateSaltTests {

        @Test
        @DisplayName("生成的salt长度应为16位")
        void saltLength() {
            String salt = Md5PasswordEncoder.generateSalt();
            assertEquals(16, salt.length());
        }

        @Test
        @DisplayName("每次生成的salt应不同")
        void saltUniqueness() {
            String salt1 = Md5PasswordEncoder.generateSalt();
            String salt2 = Md5PasswordEncoder.generateSalt();
            assertNotEquals(salt1, salt2);
        }
    }

    @Nested
    @DisplayName("空Salt测试")
    class EmptySaltTests {

        @Test
        @DisplayName("空salt时返回md5(password)")
        void emptySaltReturnsMd5() {
            String result = Md5PasswordEncoder.encryptPassword("admin123", "");
            assertEquals("0192023a7bbd73250516f069df18b500", result);
        }

        @Test
        @DisplayName("null salt时返回md5(password)")
        void nullSaltReturnsMd5() {
            String result = Md5PasswordEncoder.encryptPassword("admin123", null);
            assertEquals("0192023a7bbd73250516f069df18b500", result);
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    @SuppressWarnings("all")
    class EdgeCaseTests {

        @Test
        @DisplayName("null密码应返回false")
        void nullPasswordMatchesReturnsFalse() {
            boolean result = Md5PasswordEncoder.matches(null, "somesalt");
            assertFalse(result);
        }

        @Test
        @DisplayName("空salt应返回false")
        void emptySaltMatchesReturnsFalse() {
            String md5WithSalt = Md5PasswordEncoder.encryptPassword("admin123", SALT);
            boolean result = Md5PasswordEncoder.matches(md5WithSalt, "somehash");
            assertFalse(result);
        }

        @Test
        @DisplayName("null salt应返回false")
        void nullSaltMatchesReturnsFalse() {
            String md5WithSalt = Md5PasswordEncoder.encryptPassword("admin123", SALT);
            boolean result = Md5PasswordEncoder.matches(md5WithSalt, "somehash");
            assertFalse(result);
        }

        @Test
        @DisplayName("null encodedPassword应返回false")
        void nullEncodedPasswordMatchesReturnsFalse() {
            String md5WithSalt = Md5PasswordEncoder.encryptPassword("admin123", SALT);
            boolean result = Md5PasswordEncoder.matches(md5WithSalt, null);
            assertFalse(result);
        }
    }
}