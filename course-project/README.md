# 云章 CMS（Spring MVC / SSM）

《JAVA 框架技术（一）》课程项目。基于传统 **Spring + Spring MVC + MyBatis-Plus（SSM）** 的内容管理系统，使用 Java 11 编译、打包为 WAR 部署到外置 Tomcat 9，**不依赖 Spring Boot 或内嵌 Tomcat**。系统包含文章全流程管理、三级分类、基于角色的权限控制、操作日志，以及一个能感知全站结构的站内引导智能体。

本目录是该课程项目的独立工程，与仓库根的 cms-agent（Spring Boot 模块）互不依赖，可单独构建。

## 技术栈

| 层 | 选型 |
|---|---|
| 运行环境 | JDK 11+、Maven 3.8+、外置 Tomcat 9、MySQL 8 |
| Web | Spring 5.3、Spring MVC、Thymeleaf |
| 持久层 | MyBatis-Plus 3.5.3.1（分页 / 乐观锁 / 逻辑删除 / 自动填充 / 防全表更新） |
| 鉴权 | Session + HandlerInterceptor + AOP `@RequirePermission` |
| 密码 | spring-security-crypto 的 BCrypt（不引入完整 Spring Security） |
| 前端 | Thymeleaf + 原生 ES Modules，无 npm 构建链 |

## 目录结构

```
course-project/
├── src/                          # 代码，标准 Maven 结构
│   ├── main/java/com/ylf/cms/    # 按业务域分包：auth、content、catalog、ops、security、guide
│   ├── main/resources/           # 配置、模板、静态资源、sql 脚本
│   └── test/                     # 单元测试
├── docs/                         # 过程文档
│   ├── requirements/             # 需求分析、任务书
│   ├── design/                   # 表结构说明、UI 布局、流程图
│   ├── api/                      # OpenAPI 描述与接口文档
│   ├── db/                       # 建库脚本与增量迁移
│   └── 测试记录.md / 演示脚本.md / ai-log.md
├── pom.xml
└── README.md
```

## 快速开始

### 1. 配置数据库连接

连接信息通过环境变量提供，不写入仓库：

```bash
export CMS_DB_URL="jdbc:mysql://127.0.0.1:3306/cms_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
export CMS_DB_USERNAME="你的数据库账号"
export CMS_DB_PASSWORD="你的数据库密码"
```

首次建库可执行 `docs/db/init.sh`（需 root，会重建数据库并导入演示数据，勿在已有库上执行）；保留数据的增量迁移见 `docs/db/upgrade-20261119.sql`。

### 2. 构建与部署

在本目录执行：

```bash
mvn clean test package
```

产物为 `target/cms.war`。将其重命名为 `ROOT.war` 部署到外置 Tomcat 9 的 `webapps/`，启动后访问站点根路径（具体端口以 Tomcat Connector 为准，本项目独立实例使用 8081）。

### 3. 演示账号

初始密码统一为 `123456`：

| 账号 | 角色 | 权限 |
|---|---|---|
| admin | 管理员 | 全部 |
| author | 作者 | 写稿、改自己的稿、提交审核 |
| auditor | 审核员 | 审核、发布、下架 |
| guest | 游客 | 只读 + 站内引导 |

## 文档导航

- 需求与任务：`docs/requirements/`
- 设计与流程图：`docs/design/`
- 接口文档与 OpenAPI：`docs/api/`（OpenAPI 3.0 可直接导入 ApiFox）
- 数据库脚本：`docs/db/`
- 课程设计报告（LaTeX 源码与配图）：仓库根 `../course-report/`，主文件 `../course-report/课程设计报告.tex`

## 测试

`mvn test` 覆盖状态迁移、敏感信息识别、日志递归脱敏、文章保护与业务域边界，均为纯单元测试、不依赖数据库；端到端联调记录见 `docs/测试记录.md`。