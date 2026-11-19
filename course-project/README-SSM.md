# 云章 CMS（Spring MVC）

本目录是课程项目的 SSM 实现。使用 Java 11 编译，部署到外置 Tomcat 9，不依赖 Spring Boot 或内嵌 Tomcat。主代码按 security、content、catalog、ops 四个业务域组织，跨域通过 Service 接口协作。

## 运行环境

- JDK 11 或更高版本
- Maven 3.8+
- MySQL 8，数据库结构见 src/main/resources/sql/schema.sql
- Apache Tomcat 9

为保留现有数据，db/upgrade-20261119.sql 只给 sys_log 增加 trace_id。该脚本不清空表。db/init.sh 是初始化脚本，会重建数据库；首次初始化时必须显式设置 DB_PASS，不要在已有数据库上执行。

## 配置

通过环境变量提供数据库连接，连接信息不会写入仓库：

    CMS_DB_URL=jdbc:mysql://127.0.0.1:3306/cms_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    CMS_DB_USERNAME=你的数据库账号
    CMS_DB_PASSWORD=你的数据库密码

可选环境变量 DEEPSEEK_API_KEY 提供站内助手模型密钥。不配置时，站内助手使用离线规则路由。

## 构建与部署

在本目录执行：

    mvn clean test package

构建结果为 target/cms.war。将 WAR 部署到外置 Tomcat 9 的 webapps/ROOT.war，启动 Tomcat 后访问 http://localhost:8080/。如使用其他端口，以 Tomcat Connector 配置为准。

当前页面和接口以 ROOT 上下文为准；将 WAR 命名为 ROOT.war 部署在站点根路径。如需 /cms 子路径，需要先把页面路由和登录跳转统一改为上下文感知 URL。

## 演示账号

已初始化的课程演示数据库包含 admin、auditor、author、guest，初始密码为 123456。生产或共享环境请先修改演示账号密码。

## 验证

mvn test 包含状态迁移、敏感信息识别、日志递归脱敏、文章服务保护和业务域边界测试。浏览器端到端验证记录与截图会随课程报告更新。

## 相关文档

- docs/api.md：接口和统一响应说明
- docs/ui-layout.md：界面断点、网格列数与位置计算方式
- docs/ai-log.md：已有 AI 辅助过程记录
- db/upgrade-20261119.sql：保留数据的增量迁移
- apifox/cms-openapi.json：OpenAPI/Apifox 接口描述
