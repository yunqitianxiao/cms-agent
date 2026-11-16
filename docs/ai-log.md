# AI 使用日志

按天记录：做了什么、为什么这么做、卡在哪、怎么解决的。

## D1 · 2026-11-01 · 仓库骨架

**做了什么**：建仓库、`pom.xml`、`CmsApplication`、`application.yml`、README 与本计划。

**为什么单独成仓**：智能体是"横切"模块——它要读全站的卡片、权限码和统计数字。放在主仓里时，`@GuideCard` 如果写在 `ops` 包内，M1–M3 的每个 Controller 都要 import 智能体模块，等于被观测者依赖观测者。所以注解放在 `common`，`ops` 只读不写。这个边界决定了本仓库的依赖面：`common` 必带，`security` 必带（要权限码判断"这个用户能不能看这张卡"），`catalog`、`content` 各带一个 Service（建议里的分类与文章数字）。

**卡点**：`ops` 单独拎出来编译不过——它 import 了 `LoginUser`、`RoleService`、`CategoryService`、`ArticleService`。核对了一遍依赖：`common` 27 处、`security` 7 处、`catalog`/`content` 各 1 处。结论是"M4 独立仓库"实际等于"整个后端"（除前端页面外），这决定了 15 天的切法：先上公共层，最后才轮到智能体本身。

**安全**：原项目的 `application.yml` 里写着数据库明文口令。本仓库改成 `${CMS_DB_USER}` / `${CMS_DB_PASSWORD}` 占位，`.env` 与 `*.log` 进 `.gitignore`。判据是"仓库里搜不到口令"：拿原口令去 `git grep`，必须 0 命中（这条能失败——把口令原样贴回来就会命中）。

## D2 · 2026-11-02 · 公共层·结果与异常

**做了什么**：`Result`、`ResultCode`、`BizException`、`GlobalExceptionHandler` 四个类从主工程原样搬入，另加一个不启 Spring 上下文的 `GlobalExceptionHandlerTest`（两个用例：未捕获异常、业务异常）。

**为什么这一块排第二**：后面每个模块抛错都走这四个类，改它们的时机越晚返工面越大。`ResultCode` 把业务码（5 位分段）和 HTTP 状态码分开——智能体的卡片校验失败是"业务上没这张页"，不是"服务器坏了"，两者混用会让前端只能靠字符串猜。

**判据**：构造一个消息里带 `select * from sys_user where password = 'admin'`、cause 里带 `.java:88` 堆栈帧的未捕获异常，断言响应体里搜不到 `select` / `SQL` / `java.` / `IllegalState` / `at com.ylf` / `sys_user` / `password`。

**这条判据真的能红**：把 `handleOther` 临时改成 `Result.fail(ResultCode.INTERNAL, e.toString())`，测试立即失败，报错原文就是整条 SQL：

> 响应体泄露了「select」：{"code":50000,"message":"java.lang.IllegalStateException: nested exception is java.sql.SQLException: select * from sys_user where password = 'admin'", ...}

验证完已改回。`mvn -o package` 绿，`Tests run: 2, Failures: 0`。

## D3 · 2026-11-04 · 公共层·校验与工具

**做了什么**：`PageQuery` / `PageResult`、`TraceIdFilter`、`@GuideCard`、`IpUtil` / `MaskUtil` / `SortWhitelist` 原样搬入，新增两个测试类（`PageQueryValidationTest` 3 条、`CommonLayerTest` 9 条）。

**为什么这一块排第三**：分页参数和异常处理是一对——D2 只保证"出错时怎么说"，D3 才保证"什么时候算错"。`pageSize` 不设上限不会报错，只会让一次列表请求把整张表读进内存，这类问题在演示时看不出来、在数据量上来时才发现。`SortWhitelist` 同理：`ORDER BY` 后面不能放占位符，唯一可行的防线是白名单，晚一天加就多一天裸奔。

**判据**：`GET /api/articles?pageSize=99999` 返回 400，响应体带 `code:40000` 与「每页最多 200 条」；同时断言默认值 1/10 和合法值 2/20 仍返回 200，避免"全拒"也能通过的假绿。

**这条判据真的能红**：把 `PageQuery` 上的 `@Max(value = 200)` 临时删掉，`oversizedPageSizeIsRejected` 立即失败：

> expected: <400> but was: <200>

验证完已改回，`mvn -o package` 绿，`Tests run: 14, Failures: 0`（D2 的 2 条 + 今天的 12 条）。

**卡点**：GitHub 的 22 端口直连超时（`ssh -T git@github.com` 20 秒无响应），本机代理只开了 HTTP/SOCKS，SSH 走不出去。改为 HTTPS 远程经 `127.0.0.1:7897` 推送；仓库的 `origin` 仍保留 SSH 地址，换机器时再按网络情况切换。

**日期**：D3 实际完成于 11-04，比计划表晚一天。按规则 4 顺延，不压缩后续内容。

## D4 · 2026-11-05 · 数据层

**做了什么**：`schema.sql`（六张主表 + `sys_log` + 两张关联表）、`data.sql`（4 角色 / 15 权限点 / 5 用户 / 10 分类 / 20 文章 / 7 条日志）、`MybatisPlusConfig`、`AuditMetaObjectHandler`，另加 `DataLayerTest` 4 条与 `scripts/verify-db.sh`。

**为什么这一块排第四**：后面每一块都要落表，表结构定晚了返工面最大。三个设计点是刻意的：`sys_permission` 运行期只读（D11 的站点地图校验依赖"权限码集合稳定"）；文章用 `version` 做乐观锁而不是行锁；所有业务表都有 `create_time`/`update_time`，且由 `MetaObjectHandler` 统一下发——业务代码手写时间戳，迟早有一处漏。

**审计填充的切法**：源项目的 `AuditMetaObjectHandler` 还填 `create_by`/`update_by`，这依赖 D5 才进来的 `UserContext`。今天先只填时间，D5 把登录态接上后补两行；否则数据层单独编译不过。

**判据一（建表）**：`scripts/verify-db.sh` 起一个**用户级临时 MariaDB 实例**（随机端口 + socket，跑完即删），在空库里跑 `schema.sql` 与 `data.sql`，断言 8 张表、6 个外键、各表行数与脚本一致。不碰本机已有的 `cms_db`——`cmsapp` 只有该库权限，`root` 也不接受 socket 登录，所以不能建临时库，只能另起实例。

**判据二（审计填充）**：测试实体标 `@TableField(fill = INSERT/INSERT_UPDATE)`，调 handler 后断言两个时间戳都被填上、更新时 `createTime` 不被覆盖、`id` 不被误改。

**两条判据都真的能红**：
- 把 `insertFill` 里两行 `strictInsertFill` 删掉 → `expected: not <null>`（createTime 没有自动填充）。
- 往 `schema.sql` 末尾追加一句 `THIS IS NOT SQL;` → 脚本退出码 1，报 `ERROR 1064 ... near 'THIS IS NOT SQL'`。
验证完均已还原，`mvn -o package` 绿，`Tests run: 18, Failures: 0`；`scripts/verify-db.sh` 输出 `OK`。

**日期**：D4 实际完成于 11-05，计划表写 11-04，同样顺延一天。

## D5 · 2026-11-08 · 安全层·身份

**做了什么**：`LoginUser` / `UserContext`、`LoginInterceptor`、`PasswordConfig`（只引 `spring-security-crypto` 拿 BCrypt）、`WebMvcConfig`、`AuthService` / `AuthController`（登录、注册、退出、`/me`）以及它们依赖的 `SysUser` / `SysRole` 实体与三个 mapper、`SessionKeys` / `RoleCode`。新增 `LoginInterceptorTest` 5 条、`AuthBoundaryTest` 4 条。

**为什么这一块排第五**：身份是后面所有块的前置——D6 的权限码要挂在登录态上，D8 的审计要取 `createBy`，D10 的站点地图要校验卡片上的权限码是否存在。而且身份层一旦定错（比如把整个 `LoginUser` 塞进 Session），后面每改一次权限就得让用户重新登录，演示时很难解释。

**两个刻意的设计点**：
- Session 里**只存 userId**，每次请求由 `LoginInterceptor` 回查数据库重新装载。代价是每请求多两条查询，换来"停用账号立刻踢下线、改角色立刻生效"。用户被删除或停用时直接 `invalidate()` 旧 Session，不留半死状态。
- 账号不存在与密码错误返回**同一个错误码**（`40101`）。分开提示等于送给攻击者一个用户名枚举接口。
- 登录成功后先 `invalidate()` 旧 Session 再建新的，防会话固定。

**补完 D4 的遗留**：`AuditMetaObjectHandler` 现在会填 `createBy` / `updateBy`，从 `UserContext` 取。取不到（未登录的注册、定时任务）就不填，而不是填 0——0 会被当成一个真实用户 id。

**今天没搬的东西**：`AuthController` 上的 `@OperLog` 注解留到 D8（日志落库）再加；`SysRoleMapper` 里与角色分配相关的 VO 查询留到 D6。这样 D5 单独一块编译得过，也不会把后面几天的判据提前"绿掉"。

**判据**：未登录访问 `/api/**` 返回 401，响应体带 `code:40100`。分两层验：
- 单元层（`LoginInterceptorTest`）：无 Session 直接 401 且**不穿透到数据库**；停用用户 401 且 Session 作废；有效用户放行且请求结束后 `UserContext` 被清空；非 `/api` 页面路径 302 到 `/login?next=...`。
- 集成层（`AuthBoundaryTest`）：真的走 `WebMvcConfig` 注册的拦截器，断言 `/api/auth/me`、**不存在的 `/api/does-not-exist`**、`/api/auth/logout` 都是 401；而 `/api/auth/login`、`/api/auth/register`（空 body 应到校验层返回 400）与 `/api/portal/**`（404）确实被放行。

**判据真的能红（两次注入）**：
1. 把 `WebMvcConfig` 的 `addPathPatterns("/api/**", "/app/**")` 改成只留 `/app/**` → `unknownApiPathIsStillProtected` 红（`expected:<401> but was:<404>`）、`logoutRequiresLogin` 红（`expected:<401> but was:<200>`）。
2. 注释掉 `LoginInterceptor.reject` 里写响应体的那一行 → 两条单元/集成断言都红（`响应体应带 UNAUTHORIZED 错误码，实际：`）。

**一个值得记的坑**：注入 1 之后 `meRequiresLogin` **仍然是绿的**。原因是它打到 `/api/auth/me`，拦截器没生效时 Controller 里的 `UserContext.require()` 自己也会抛 `UNAUTHORIZED`，照样 401。也就是说只写这一条断言的话，"拦截器根本没注册"这个故障是查不出来的。真正能证明边界的是**不存在的 `/api` 路径**和**不需要登录态就能成功的 `logout`**——判据要挑"没有别的代码替它兜底"的那条。

验证完两次注入均已还原，`mvn -o package` 绿，`Tests run: 27, Failures: 0`（D2–D4 的 18 条 + 今天的 9 条）。

**日期**：D5 实际完成于 11-08，计划表写 11-05。按规则 4 顺延，不压缩后续内容。

## D6 · 2026-11-09 · 安全层·权限

**做了什么**：`Perm` 权限码常量、`@RequirePermission` 注解、`PermissionAspect`（`@Around("@annotation")`），以及 `RoleService` / `RoleController` 与它们依赖的 `SysPermission` 实体、`SysPermissionMapper` / `SysRolePermissionMapper`、四个 VO、两个 DTO；`SysRoleMapper` 补了三条批量查询（角色权限行、角色人数、角色被引用数）。新增 `PermissionAspectTest` 5 条、`RolePermissionBoundaryTest` 5 条。

**为什么排第六**：D5 只回答"你是谁"，今天才回答"你能干什么"。这两件事必须分开——身份是一个请求内稳定的事实，权限是可能随时被管理员改的策略；把它们塞进同一个过滤器，改权限就得重新登录，演示时很难解释。今天的做法是：身份每请求重查（D5），权限在切面里按**当前**上下文比对，所以改完角色下一个请求就生效。

**三个刻意的设计点**：
- **401 与 403 不混**。`PermissionAspect` 第一步是 `UserContext.require()`，没有身份直接 401；有身份但不满足权限码才是 403。把未登录当成"没有权限"返回 403，会让前端不知道该跳登录页还是该提示无权限。
- **ANY / ALL 是注解里的语义，不是切面的默认行为**。`@RequirePermission({ROLE_MANAGE, USER_MANAGE})` 表示"二者之一"，这样"角色列表"既能给角色管理员看，也能给用户管理员看（分配角色时要选）；写成 `Logic.ALL` 时缺一个就拒。语义写在调用点，切面只负责执行。
- **`@Order(2)` 是有意的**：D8 的日志切面会排在它外面（`@Order(1)`），这样被 403 拦下的请求仍会被记为 `DENIED`。越权尝试不留痕，等于没有日志。

**切面不能只判"接口"，还要判"数据"**：`article:update:own` 与 `article:update:any` 都是权限码，切面只知道"这个人有改稿权限"，不知道"这篇是不是他写的"。数据级判定必须留在 Service（D7），切面越权去猜数据行，就会变成第二个业务层。

**今天没搬的东西**：`RoleController` 四个写接口上的 `@OperLog` 留到 D8——现在加了也没有切面接收，只会让"日志没记上"看起来像 bug。原定判据里的"`sys_log` 有一条 DENIED"同理顺延。

**判据**（拆成两层）：
- 单元层（`PermissionAspectTest`）：作者缺 `article:audit` → 403 且**业务方法一次都没执行**（`verify(pjp, never()).proceed()`，防"先执行再报错"）；有权限时原样返回；ANY 命中一个即放行；ALL 缺一个即拒；无身份 → 401。
- 集成层（`RolePermissionBoundaryTest`）：真的走 D5 的 `LoginInterceptor`（Session 里放 userId，`AuthService` mock 出对应身份），断言作者 `GET /api/roles` → 403/40300 且 `RoleService` 零调用；`role:manage` 与 `user:manage` 都能读；未登录 → 401/40100；`/api/roles/permissions` 同样受控。

**判据真的能红（两次注入）**：
1. 把 `allowed` 改成恒 `true` → 4 条红（单元 2 条 + 集成 2 条），报 `Status expected:<403> but was:<200>`。
2. 删掉 `RoleController.list()` 上的 `@RequirePermission` → `authorIsForbidden` 红，`roleManagerCanRead` 仍然绿——正好说明"只测有权限的人能进来"是废判据。

**一个值得记的坑**：`@WebMvcTest` 的切片里**没有 AOP 自动配置**。第一次跑集成测试时，`@Import(PermissionAspect.class)` 把切面注册成了普通 Bean，`@Aspect` 不生效、所有请求直接放行——表现为"作者也能读角色列表"。也就是说，如果测试只写"管理员返回 200"这一面，这个故障会被当成通过。凡是"加了注解但没生效"的场景，判据必须是**该被拒绝的那一方**。

**合并别人分支时撞了三个文件**：推送被拒，远程 `main` 上已经有 chuanzi908 的 PR #1（M1-S1：实体与建表脚本对齐），里面同样有 `SysPermission`、`SysPermissionMapper`、`SysRolePermissionMapper`。前两个与今天新增的语义等价，取他的版本（注释更全，且他补了 `SysRolePermission` / `SysUserRole` 两个实体和一条"实体与 `schema.sql` 对不上就红"的测试）。第三个他做成 `BaseMapper<SysRolePermission>`，而 D6 需要按复合主键的三个便捷方法——合并成"`BaseMapper` + 三个方法"，并把 `insert` 改名为 `insertIgnore`：`BaseMapper` 已经注入了一个叫 `insert` 的语句，同名声明会撞 statement id；而且这里要的是 `insert ignore` 的幂等插入，与"重复键抛异常"是两种不同的失败语义。合并后 `Tests run: 39`（两人的用例都在），`BUILD SUCCESS`。

**日期**：D6 实际完成于 11-09，计划表写 11-06。按规则 4 顺延。

## D7 · 2026-11-16 · 业务层·分类只读与计数

**做了什么**：`catalog/category` 包——`CmsCategory` 实体、`CmsCategoryMapper`（`selectAllAlive` 与按分类分组的 `countPublishedByCategory`）、`CategoryCountRow`、`CategoryVO`（带 `articleCount` 与 `children`）、`CategoryService`（`tree` / `listFlat` / `descendantIds` / `requireEnabled`）。新增 `CategoryTreeTest` 10 条、`CategoryCountDbTest` 3 条（真实库，平时跳过）与 `scripts/verify-category-count.sh`。

**范围为什么变了**：拉下来的 `main` 上已有 M2（wangchu67162-netizen）的 D1–D3，`ArticleService` 的**分页列表与 `countByAuthor` 已经落地**；计划表里 D7 的另一半因此不需要重写。今天只补智能体真正要读、而两个模块都还没提供的分类侧：它要回答"站点配了分类没有"（`listFlat().isEmpty()`）和"某分类下有多少篇文章"（树上的数字）。分类的新增/改名/删除属于分类管理，不在今天的范围，所以这一版是只读的。

**计数口径写死在三处**（Service javadoc、Mapper SQL、测试断言）：节点的数字 = 自身 + 全部子孙下**已发布且未删除**的文章数；与分类自身是否启用无关——停用只是前端不给选，不改变历史文章的归属。口径必须唯一，否则智能体会报一个"看起来对、和后台列表对不上"的数字。

**三个刻意的设计点**：
- `onlyEnabled=true` 时，停用分类**连同整棵子树**移除，而不是把子节点挂到根上。挂根会让"后端"看起来像个一级分类，用户会以为站点结构变了；计数在剪枝之前算好，所以父节点的数字仍包含被隐藏子树。
- 父分类被逻辑删除时，子分类挂到根上并发保留自己的数字——否则删一个中间分类会让它的子树凭空消失。
- `descendantIds` 用显式栈 + `visited`，`parent_id` 指向自己或形成环时不重复展开、不死循环；分类不存在返回空列表而不是抛异常，调用方在拼 `IN` 条件时天然得到"没有结果"。

**判据（两层）**：
- 逻辑层（`CategoryTreeTest`）：树形状、`技术 = 后端 9 + 前端 1 = 10`、空叶子是 0 而不是 null、剪枝后数字不变、子树 id 覆盖三级、自环不死循环、孤儿挂根、停用分类发布时拒（40000）。
- 真实库（`CategoryCountDbTest` + `scripts/verify-category-count.sh`）：起临时 MariaDB 导入 `schema.sql` + `data.sql`，断言树上每个节点的数字 == 用 `JdbcTemplate` 独立算出的子树 `COUNT`。**子树 id 在测试里写死，不复用被测的 `descendantIds`**——否则尺子和被测对象是同一把，汇总漏一级时两边一起错、一起绿。

**两次注入，都真的能红**：
1. 删掉 `rollUp` 里的 `total += rollUp(child)` → `CategoryTreeTest` 2 条红（`后端 = 9` 变 0），脚本随之退出码 1（`分类 技术 的数字与库里的 COUNT 对不上 ==> expected: <10> but was: <0>`）。
2. 把 Mapper 的 `AND status = 'PUBLISHED'` 去掉（口径改成全部状态）→ 真实库 2 条红：`Java 应为 6；算上未发布就是 9 ==> expected: <6> but was: <9>`、`分类 技术 ... expected: <10> but was: <17>`。
两次验证完均已还原，`mvn -o package` 绿，`Tests run: 50, Failures: 0, Errors: 0, Skipped: 1`（跳过的那条就是真实库判据，没有库时不跑）。

**一个值得记的坑**：`scripts/verify-category-count.sh` 第一版用 `mvn -q test`，只看退出码——但 `@EnabledIfSystemProperty` 不满足条件时测试是 **skipped**，`mvn` 照样返回 0，于是"没量"会被当成"量过了"。改成跑完必须 `grep` 到 `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`，否则脚本自己 `exit 1` 并打印实际条数。

**没做的事**：`ArticleService` 的 `countByAuthor` 口径与智能体分状态计数（驳回数、草稿数）的对齐留给 M2——他们 D4–D6 正在改这个类的分页与测试，同一天改同一处只会互相踩。今天的 DB 判据已经把"分类数字与库里同源"钉住，D10 接建议服务时再核对文章侧。
