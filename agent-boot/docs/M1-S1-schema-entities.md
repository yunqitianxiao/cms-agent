# M1-S1 · 账号与权限五表 + 数据层

## 这次交了什么

`sys_user` / `sys_role` / `sys_permission` / `sys_user_role` / `sys_role_permission`
五张表的实体与 Mapper 补齐，外加一条不需要数据库的漂移检查。

| 路径 | 说明 |
|---|---|
| `src/main/java/com/ylf/cms/security/user/entity/SysPermission.java` | 权限点实体，对应 `sys_permission` |
| `src/main/java/com/ylf/cms/security/user/entity/SysUserRole.java` | 用户-角色关联实体，复合主键，对应 `sys_user_role` |
| `src/main/java/com/ylf/cms/security/user/entity/SysRolePermission.java` | 角色-权限关联实体，复合主键，对应 `sys_role_permission` |
| `src/main/java/com/ylf/cms/security/user/mapper/SysPermissionMapper.java` | `BaseMapper<SysPermission>`，运行期只读 |
| `src/main/java/com/ylf/cms/security/user/mapper/SysRolePermissionMapper.java` | `BaseMapper<SysRolePermission>`，只用按条件的方法 |
| `src/test/java/com/ylf/cms/security/user/EntitySchemaTest.java` | 实体与建表脚本一致性测试 |

**没有改动任何已有文件。**

## 和原始交付包的差异

原始交付包（`M1-S1-schema-entities.zip`，13 个文件）是按"空仓库"写的，
直接解压会覆盖 `main` 上已经合并的版本，所以本 PR 只取其中 `main` 还缺的部分，
并让它和 `main` 现有的 `schema.sql` 对齐：

| 原始包里的文件 | 本 PR 的处理 | 原因 |
|---|---|---|
| `sql/schema.sql`、`sql/data.sql` | **未纳入** | `main` 的脚本已经是 8 张表（多 `cms_category` / `cms_article` / `sys_log`），原始包只有 5 张；覆盖会删掉已有的表和种子数据 |
| `entity/SysUser.java`、`entity/SysRole.java` | **未纳入** | `main` 版本多了 `@TableField(select = false)` 等细节，覆盖等于回退 |
| `mapper/SysUserMapper.java`、`mapper/SysRoleMapper.java`、`mapper/SysUserRoleMapper.java` | **未纳入** | `main` 版本带密码查询、"权限取并集"等注解 SQL，原始包只有空 `BaseMapper` |
| `entity/SysPermission.java` | **适配后纳入** | 去掉 `status` / `deleted` / `update_time`：`main` 的 `sys_permission` 没有这三列，这张表运行期只读 |
| `entity/SysUserRole.java`、`entity/SysRolePermission.java` | **适配后纳入** | 去掉自增 `id` 与 `create_time`：`main` 的关联表是纯复合主键，时间痕迹记在 `sys_log` |
| `mapper/SysPermissionMapper.java`、`mapper/SysRolePermissionMapper.java` | **原样纳入** | 与 `main` 不冲突 |
| `EntitySchemaTest.java` | **适配后纳入** | 断言从"五表齐全 + 外键在本组内"改为"五表齐全"，因为外键校验会被 `cms_article` 等表干扰 |
| `README.md` | **改名为本文件** | 仓库根 README 是项目总览，不能被模块交付说明覆盖 |

## 怎么验

```bash
mvn -o test -Dtest=EntitySchemaTest
```

或者整个 `mvn -o package`（27 条既有检查 + 这 2 条，全绿）。

`EntitySchemaTest` 不需要数据库——它读打包进 classpath 的 `schema.sql`，
拿实体的 `@TableName` 和字段名去对表和列。

已实测：把 `SysUser` 的 `@TableName("sys_user")` 改成 `"sys_users"`，`mvn test` 立刻失败，
报 `实体与建表脚本不一致：SysUser：@TableName 是 sys_users，约定是 sys_user`。

## 注意

- `main` 的 `data.sql` 里种子账号口令是 `123456`，库里存 BCrypt 密文（60 字符）。
  这是给评审登录演示用的，别照着抄进生产。
- `main` 的 `schema.sql` 是**从零初始化**（先按外键倒序 `DROP TABLE`），不要在有数据的库上直接跑。
- `sys_user.username` 是唯一索引 + 逻辑删除，所以**删除后用户名不能复用**，
  再注册同名会返回 `40902`。这是有意的取舍：账号名不复用比"允许同名重建"更安全。
- `sys_user` 的 `create_time` / `update_time` 在 `main` 的表结构里有数据库默认值，
  但应用层仍由 `AuditMetaObjectHandler` 填，两边口径以 handler 为准。
