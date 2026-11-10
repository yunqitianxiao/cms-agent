package com.ylf.cms.common.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 口令工具。全站只有这一处能碰口令明文：
 * 注册时 encode 一次，登录时 matches 一次，除此之外任何地方都不该出现明文比较。
 *
 * 用 BCrypt 而不是 MD5/SHA：BCrypt 自带随机盐，同一口令每次 encode 结果都不同，
 * 拿到库也反查不了彩虹表。代价是校验不能"加密后比字符串"，只能 matches。
 */
@Component
public class PasswordUtil {

    /** BCrypt 强度 10（默认值）：单次校验约 60–100ms，挡暴力破解又不拖慢登录 */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(10);

    /** 把明文换成 60 字符的 BCrypt 密文。入参为空时直接拒绝，不要静默存空口令。 */
    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("口令不能为空");
        }
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验明文与密文是否匹配。
     * 密文格式非法（手工改库、迁移遗留）时返回 false 而不是抛异常——
     * 一次脏数据不该让整个登录接口 500。
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        try {
            return ENCODER.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
