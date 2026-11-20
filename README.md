# cms-agent

> 本仓库现还包含课程项目快照与报告工作副本，见 README-course-project.md。课程项目的 Spring MVC 运行说明见 course-project/README.md。

企业级 CMS 课程项目里的 **M4 智能体与运维** 模块及其依赖的公共层，单独抽出来按 15 天推进。

智能体不做"聊天机器人"：它的每张卡片、每条建议都指向站内一个真实存在的页面，路径和权限码在启动时校验，对不上就启动失败——这样"卡片与现实脱节"不会留到演示当天才被发现。

## 现在到哪一步了

第 6 天，安全层·权限已就位：身份由 Session 决定（只存 userId，每请求回查），能不能碰某个接口由 `@RequirePermission` + AOP 判定——作者调角色管理接口是 403，未登录是 401，两者不混。`mvn -o package` 跑 39 条检查；建表可用 `bash scripts/verify-db.sh` 在临时 MariaDB 实例里验证，不会碰本机已有的库。每天的进度写在 [`docs/计划.md`](docs/计划.md)，每天的卡点写在 [`docs/ai-log.md`](docs/ai-log.md)。

## 本地启动

仓库里没有任何口令，数据库账号由环境变量提供：

```bash
export CMS_DB_HOST=localhost
export CMS_DB_PORT=3306
export CMS_DB_NAME=cms_db
export CMS_DB_USER=<你的账号>
export CMS_DB_PASSWORD=<你的口令>

mvn -q package
java -jar target/cms-agent-1.0.0.jar
```

服务起在 8090（本机 8080 被系统级 tomcat9 占着）。

## 技术栈

Spring Boot 2.7.18 · MyBatis-Plus 3.5.3.1 · MySQL 8 · Thymeleaf · JDK 11
