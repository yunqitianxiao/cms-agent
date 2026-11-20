package com.ylf.cms.security.user;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ylf.cms.security.user.entity.SysPermission;
import com.ylf.cms.security.user.entity.SysRole;
import com.ylf.cms.security.user.entity.SysRolePermission;
import com.ylf.cms.security.user.entity.SysUser;
import com.ylf.cms.security.user.entity.SysUserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 判据：账号与权限五张表的实体不许和 schema.sql 漂移。
 *
 * 这条测试不需要数据库——它读的是打包进 classpath 的建表脚本，
 * 拿实体的 @TableName 和字段名去对 DDL 里的表和列。写得对不对、列名有没有改漏，
 * 在 `mvn package` 阶段就能红，不必等到启动时在 MySQL 上炸。
 *
 * 它能失败：把 SysUser 的 @TableName 改成 "sys_users"，或给实体加一个
 * schema.sql 里不存在的字段（如 avatar），下面第一条断言立刻变红。
 */
class EntitySchemaTest {

    private static final Map<Class<?>, String> ENTITIES = new LinkedHashMap<>();

    static {
        ENTITIES.put(SysUser.class, "sys_user");
        ENTITIES.put(SysRole.class, "sys_role");
        ENTITIES.put(SysPermission.class, "sys_permission");
        ENTITIES.put(SysUserRole.class, "sys_user_role");
        ENTITIES.put(SysRolePermission.class, "sys_role_permission");
    }

    @Test
    @DisplayName("五张表都在 schema.sql 里，且实体与列一一对应")
    void entityMatchesSchema() throws Exception {
        String sql = readClasspath("sql/schema.sql");
        Map<String, String> tables = splitTables(sql);

        List<String> problems = new ArrayList<>();
        for (Map.Entry<Class<?>, String> e : ENTITIES.entrySet()) {
            Class<?> entity = e.getKey();
            String expectedTable = e.getValue();

            String ddl = tables.get(expectedTable);
            if (ddl == null) {
                problems.add(expectedTable + "：schema.sql 里没有这张表");
                continue;
            }

            TableName ann = entity.getAnnotation(TableName.class);
            assertNotNull(ann, entity.getSimpleName() + " 缺少 @TableName");
            if (!expectedTable.equals(ann.value())) {
                problems.add(entity.getSimpleName() + "：@TableName 是 " + ann.value()
                        + "，约定是 " + expectedTable);
            }

            for (String column : columnsOf(entity)) {
                if (!Pattern.compile("(?m)^\\s+" + Pattern.quote(column) + "\\s+")
                        .matcher(ddl).find()) {
                    problems.add(expectedTable + "：缺列 " + column
                            + "（实体 " + entity.getSimpleName() + " 有该字段）");
                }
            }
        }

        assertTrue(problems.isEmpty(), "实体与建表脚本不一致：\n  " + String.join("\n  ", problems));
    }

    @Test
    @DisplayName("权限五表齐全，缺一张就红")
    void fiveTablesPresent() throws Exception {
        Map<String, String> tables = splitTables(readClasspath("sql/schema.sql"));
        for (String t : ENTITIES.values()) {
            assertTrue(tables.containsKey(t), "schema.sql 缺少表：" + t);
        }
    }

    private static List<String> columnsOf(Class<?> entity) {
        List<String> cols = new ArrayList<>();
        for (Field f : entity.getDeclaredFields()) {
            if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            cols.add(snake(f.getName()));
        }
        return cols;
    }

    private static String snake(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (char c : name.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 把 schema.sql 按 CREATE TABLE 切成"表名 → 该表 DDL 片段" */
    private static Map<String, String> splitTables(String sql) {
        Map<String, String> tables = new HashMap<>();
        Matcher m = Pattern.compile("CREATE TABLE\\s+(\\w+)\\s*\\(").matcher(sql);
        int start = -1;
        String name = null;
        while (m.find()) {
            if (name != null) {
                tables.put(name, sql.substring(start, m.start()));
            }
            name = m.group(1);
            start = m.start();
        }
        if (name != null) {
            tables.put(name, sql.substring(start));
        }
        return tables;
    }

    private static String readClasspath(String path) throws Exception {
        try (InputStream in = EntitySchemaTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "classpath 上找不到 " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
