# M1 开发日志（账号与权限）

只记 M1 这一块：做了什么、为什么这么定、卡在哪、怎么解决。
M4 的进度在 `docs/计划.md` 与 `docs/ai-log.md`，两者不混写。

## S1 · 权限五表与数据层

**做了什么**：`src/main/resources/sql/schema.sql`（五表 + 外键 + 索引）、`data.sql`（admin / author 两个账号、两个角色、15 个权限码），
`security/user/entity` 下 5 个实体，`security/user/mapper` 下 5 个 `BaseMapper`，外加一条 `EntitySchemaTest`。

**为什么先立表**：审核流程、AOP 判权、智能体的站点地图启动校验，读的都是同一套"谁有什么权限"的数据。
表结构定错，四个人的代码会一起返工；先把五表和权限码清单钉死，之后每个模块只读不写。

**为什么判据是"实体对 schema"而不是"能连库"**：连库只能证明 MySQL 装了，证明不了实体和表没漂移。
`EntitySchemaTest` 读打包进 classpath 的 `schema.sql`，拿 `@TableName` 和字段名对列，不需要数据库，`mvn package` 阶段就能红。

**判据真的能红**：把 `SysUser` 的 `@TableName` 临时改成 `sys_users`，`mvn test` 立刻失败：

> 实体与建表脚本不一致：SysUser：@TableName 是 sys_users，约定是 sys_user

验证完已改回，`Tests run: 4, Failures: 0`。

**两个刻意的取舍**：
- `create_time` / `update_time` 设成 `NOT NULL` 且**不给数据库默认值**——手写 SQL 忘了填就该立刻报错，而不是让数据库时钟悄悄兜底。
  两种时间来源混在一起，"这条数据谁先写的"就说不清了。填充交给 S2 的 `AuditMetaObjectHandler`。
- 关联表 `sys_user_role` / `sys_role_permission` 不做逻辑删除：解绑就是真删，历史归属靠 `sys_log` 记，不靠关联表回滚。

## S2 · 公共层补齐（口令、追踪、审计、分页）

**做了什么**：`PasswordUtil`（BCrypt）、`TraceIdFilter`（MDC + X-Trace-Id）、`AuditMetaObjectHandler`（审计填充）、
`MybatisPlusConfig`（分页插件 + 单页上限 500），以及 `content/service/ArticleService` 与它的临时桩。

**为什么这三样归 M1**：口令、traceId、审计时间每个模块都会用到。各写一套的后果是"登录用 BCrypt、导入用户用 MD5"，
或者"一半接口的响应带 traceId、一半不带"——放在 common 里由 M1 维护，判据一次跑完。

**为什么桩要带开关**：`ArticleService` 是 M2 的契约，本地没有实现时返回 0 会让"删有稿账号 → 40903"这条验收
永远是绿的（因为删除永远成功）。所以桩读 `cms.stub.article-count`，演示时用 `--cms.stub.article-count=3` 启动，
ApiFox 里就能真实点到守卫分支，代码里不出现写死的假数据。

**判据与红验证**：
- 口令：同一口令两次密文不同、密文里不含明文、脏密文返回 false 不抛异常。
- 追踪：没上游时生成 16 位并回写响应头；有上游时沿用；请求结束 MDC 被清理。
- 审计：插入时填、显式值不被覆盖、没有 `updateTime` 的关联表实体不抛异常。
- 分页：插件真的注册了，`maxLimit=500`。

把 `TraceIdFilter` 里的 `setHeader` 注释掉后 `mvn test`：`Tests run: 16, Failures: 2, Errors: 1`；改回后全绿。

**待与组员确认**：分工说明里"停用账号后用旧会话请求 → 40192"，而仓库 `ResultCode` 里是
`ACCOUNT_DISABLED(40102)`。码段规则是 `401xx`，40192 不在这个段位里，判断是笔误，暂按 40102 实现。
如果主工程确实是 40192，改一行枚举值即可，拦截器逻辑不受影响。
