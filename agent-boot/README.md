# cms-agent（Spring Boot 智能体）

> 本目录是云章 CMS 的 **Spring Boot 智能体实现**，可独立构建运行。课程项目的 Spring MVC 主系统位于仓库根目录（`../`），课程设计报告在 `../course-report/`。两者互不依赖。

企业级 CMS 课程项目里的 **M4 智能体与运维** 模块及其依赖的公共层，使用 Spring Boot 单独实现，打包为可执行 JAR、由内嵌 Tomcat 在 8090 端口运行，无需外置 Tomcat。

智能体不做"聊天机器人"：它的每张卡片、每条建议都指向站内一个真实存在的页面，路径和权限码在启动时校验，对不上就启动失败——避免"卡片与现实脱节"留到演示当天才暴露。

## 技术栈

| 层 | 选型 |
|---|---|
| 运行环境 | JDK 11+、Maven 3.8+、Spring Boot 2.7.18（内嵌 Tomcat）、MySQL 8 |
| Web | Spring MVC、Thymeleaf、Bean Validation |
| 持久层 | MyBatis-Plus 3.5.3.1、HikariCP（分页 / 逻辑删除 / 自动填充） |
| 鉴权 | Session + HandlerInterceptor + AOP `@RequirePermission` |
| 密码 | spring-security-crypto 的 BCrypt |
| 前端 | Thymeleaf + 原生 JavaScript，无 npm 构建链 |

## 目录结构

```
agent-boot/
├── src/main/java/com/ylf/cms/
│   ├── catalog/        # 分类树与分类计数
│   ├── content/        # 文章实体、查询与业务服务
│   ├── security/       # 登录、权限切面、用户 / 角色
│   ├── config/         # MyBatis-Plus、Web MVC、审计字段填充
│   └── common/         # 统一返回、全局异常、工具类
├── src/main/resources/ # application.yml、mapper XML、sql、模板与静态资源
├── src/test/           # 单元测试与边界测试（11 个测试类）
├── docs/               # 推进计划、阶段文档与 ai-log
├── scripts/            # 临时数据库验证脚本
└── pom.xml
```

## 快速开始

### 1. 配置数据库连接

仓库里不写任何口令，连接信息全部由环境变量提供：

```bash
export CMS_DB_HOST=localhost
export CMS_DB_PORT=3306
export CMS_DB_NAME=cms_db
export CMS_DB_USER=你的数据库账号
export CMS_DB_PASSWORD=你的数据库口令
```

### 2. 构建与启动

```bash
mvn clean package
java -jar target/cms-agent-1.0.0.jar
```

服务启动后监听 **8090**（选用 8090 是为避开本机 8080 上的系统级 Tomcat）。

## 测试与验证

- `mvn test` 覆盖权限边界、统一异常处理、参数校验、数据层与分类计数等，纯单元测试不依赖外部数据库。
- `scripts/verify-db.sh`：在临时 MariaDB 实例中导入 `schema.sql` + `data.sql` 并校验表与外键，不碰本机已有的库。
- `scripts/verify-category-count.sh`：在临时库中跑 `CategoryCountDbTest`，断言分类树上的文章数与库里的 COUNT 同源。

## 文档导航

- 推进计划与卡点：`docs/计划.md`、`docs/ai-log.md`
- 阶段设计：`docs/M1-S1-schema-entities.md`、`docs/M2-计划.md`、`docs/M2-ai-log.md`
- 课程主系统与报告：`../`（Spring MVC）、`../course-report/`