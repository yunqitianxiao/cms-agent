package com.ylf.cms.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S2 的判据：口令只在 encode/matches 两处出现，且密文里看不出明文。
 *
 * 能失败：把 PasswordUtil.encode 改成返回 rawPassword，第二条断言立刻变红；
 * 把 matches 改成 equals，第三条（错误口令）立刻变红。
 */
class PasswordUtilTest {

    private final PasswordUtil util = new PasswordUtil();

    @Test
    @DisplayName("同一口令两次加密结果不同，且密文里不含明文")
    void encodeIsSalted() {
        String a = util.encode("123456");
        String b = util.encode("123456");

        assertNotEquals(a, b, "BCrypt 每次加盐，两次结果相同说明盐被写死了");
        assertEquals(60, a.length(), "BCrypt 密文固定 60 字符");
        assertTrue(a.startsWith("$2a$10$"), "强度应为 10：" + a);
        assertFalse(a.contains("123456"), "密文里出现了明文：" + a);
    }

    @Test
    @DisplayName("正确口令通过、错误口令与脏密文都不通过")
    void matchesOnlyRightPassword() {
        String hash = util.encode("123456");

        assertTrue(util.matches("123456", hash));
        assertFalse(util.matches("1234567", hash));
        assertFalse(util.matches(" 123456", hash));
    }

    @Test
    @DisplayName("脏密文返回 false 而不是抛异常")
    void dirtyHashIsNotFatal() {
        assertFalse(util.matches("123456", "123456"));
        assertFalse(util.matches("123456", ""));
        assertFalse(util.matches("123456", null));
        assertFalse(util.matches(null, util.encode("123456")));
    }

    @Test
    @DisplayName("空口令拒绝入库")
    void blankRejected() {
        assertThrows(IllegalArgumentException.class, () -> util.encode(" "));
        assertThrows(IllegalArgumentException.class, () -> util.encode(null));
    }
}
