# M2 AI 使用日志

按天记录：做了什么、为什么这么做、卡在哪里、怎么解决的。

## D1 · 2026-11-12 · 内容模块骨架与数据层

**做了什么**：
- 读 main 现状：单模块 `cms-agent`、`schema.sql` 已含 `cms_article`/`cms_category`、`MybatisPlusConfig` 已配分页+乐观锁、`AuditMetaObjectHandler` 已配审计字段填充；
- 落地 M2 数据层：`content/entity/CmsArticle`、`content/enums/ArticleStatusEnum`、`content/vo/ArticleVO`、`content/mapper/CmsArticleMapper`（`@Mapper`）、`resources/mapper/CmsArticleMapper.xml`（详情联查）；
- 补 `docs/M2-计划.md`（7 天安排）与本日志。

**为什么**：
- M2 原先自建的是 Maven 多模块工程（自包含伪 M1/M3 便于独立跑），但小组共享的 main 是**单模块 + 包结构**，且 M1（登录/用户/权限）已由 M4 实现。为不覆盖他人 pom/README、不让伪 M1 与真 M1 打架，M2 改为**只交 content 包**、复用 main 的公共层与登录态；
- 状态字段以表为准：表是 `VARCHAR(16)` 且含 `REJECTED`，M2 原设计的 `Integer` 数字码（0/1/2/3）必须改成字符串枚举，否则与 M1 建的表对不上、与 M3 前台联调时口径也不一致；
- 联查 SQL 放 XML 并从第一版就用 LEFT JOIN 带出作者昵称/分类名，后面分页直接复用同一套列，不返工。

**卡点**：
- 结构冲突：M2 多模块 vs main 单模块，`pom.xml`/`README.md` 同名会互相覆盖 → 决定 M2 只在 main 上新增 `content` 包与 `resources/mapper`，不动根文件；
- 字段差异：main 表有 `summary/auditor_id/audit_opinion/audit_time/submit_time/publish_time/offline_reason/view_count/create_by/update_by`，M2 原实体没有 → 按表补齐 10 个字段，`status` 从 `Integer` 改为 `String`；
- Mapper 注册方式：main 用 `@Mapper` 注解而非启动类 `@MapperScan`（根包扫描会把 Service 接口误注册成 Mapper，启动期报 Invalid bound statement）→ M2 Mapper 同样只标 `@Mapper`；
- XML 必须放 `src/main/resources/mapper`（`mapper-locations: classpath*:/mapper/**/*.xml`），放在 java 目录下打包不会进 classes，运行期"Invalid bound statement"。
