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

## D2 · 2026-11-13 · 文章 DTO 与 Service 契约

**做了什么**：
- `content/dto`：`ArticleSaveDTO`（标题/摘要/正文/分类，**不含 status**）、`ArticleUpdateDTO`（继承前者 + id + version 必填）、`ArticleQueryDTO`（继承 common 的 `PageQuery`，含 title/status/categoryId/authorId + 内部 `categoryIds`）；
- `content/service/ArticleService`：9 个方法的接口契约（创建/详情/分页/编辑/删除/提交审核/审核/下架/按作者计数）；
- 编译（JDK11）通过后提交，日期按 7 天计划记为 11-13。

**为什么**：
- 分页参数直接继承 main 的 `PageQuery`，pageSize≤200 的校验只维护一处，不各模块各写一套；
- 状态字段不进 DTO：状态只能由服务端按动作推导，客户端能传状态就等于能自己发布；
- `version` 在契约层强制必填，缺了直接 400——不传 version 等于放弃并发保护，不能静默放过；
- `categoryIds` 标 `@JsonIgnore`：前端只传 categoryId，子分类展开由服务端做，防止传一个跨父分类的任意 id 集合绕开分类树约束。

**卡点**：
- `ArticleUpdateDTO` 继承 `ArticleSaveDTO` 时 Lombok 的 `@Data` 不会自动带上父类字段的 equals/hashCode → 加 `@EqualsAndHashCode(callSuper = true)`，否则比较两个 DTO 会漏掉父类字段；
- 校验注解用 `javax.validation`（main 是 Spring Boot 2.x + JDK11，不是 jakarta），依赖走 main 已有的 spring-boot-starter-validation。

## D3 · 2026-11-14 · 状态机与乐观锁

**做了什么**：
- `content/service/impl/ArticleServiceImpl`：实现全部 9 个方法；
- 状态流转统一走 `transition(existing, target, extraFields)`：带 version 做 CAS，受影响行数为 0 抛 40900；
- 越权判断：作者本人可编辑/删除/提交自己的稿子，管理级操作校验 `Perm.ARTICLE_UPDATE_ANY/DELETE_ANY/SUBMIT/AUDIT/PUBLISH`，越权 40301；
- 未发布详情对外按"不存在"处理（40400），不泄露"该 id 有稿子"；
- 提交时将计划日期记为 11-14。

**为什么**：
- 状态机、乐观锁、越权三件事都收在 Service：Controller 只接收参数与权限注解，以后加定时任务/工具调用也走同一套规则；
- 所有"读-改-写"都经 `transition` 或显式带 version 的 `updateById`，避免某处漏 setVersion 让乐观锁形同虚设；
- 草稿/驳回才允许编辑与删除，已发布只能下架——这是验收用例 ①（草稿直接审核通过 → 40901）的实现依据。

**卡点**：
- 分页在 D3 先做单表分页（`selectPage + toVO`），作者昵称/分类名暂空：一开始想直接在 D3 里逐条补名字，但那正是 N+1，决定留到 D4 用联查一次解决，D3 只保证"分页+count 两条 SQL"的骨架；
- `selectCount` 在 MP 3.5.x 返回 `Long` 而非 `int`，`countByAuthor` 返回类型定为 `long`，注意 null 兜底；
- 审核驳回必须带意见、下架必须带原因，缺了给 40000 而不是 40901——这两者是"参数缺失"而非"状态不允许"，错误码区分开便于前端提示。
