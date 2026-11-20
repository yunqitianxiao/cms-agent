# 云章 CMS

《JAVA 框架技术（一）》课程项目——一个内容管理系统（CMS），提供文章全流程管理、三级分类、基于角色的权限控制、操作日志，以及能感知全站结构的站内引导智能体。

本仓库包含**两套架构实现，各自独立、可单独构建**：

| 架构 | 位置 | 打包与运行 | 详细说明 |
|---|---|---|---|
| **Spring MVC / SSM（课程主系统）** | 仓库根（`src/`、`pom.xml`） | WAR，外置 Tomcat 9（8081） | [README-mvc.md](README-mvc.md) |
| **Spring Boot 智能体** | [`agent-boot/`](agent-boot/) | 可执行 JAR，内嵌 Tomcat（8090） | [agent-boot/README.md](agent-boot/README.md) |

## 仓库结构

```
cms-agent/
├── src/  docs/  pom.xml          # Spring MVC 主系统（位于仓库根）
├── README-mvc.md                 # Spring MVC 详细说明
├── agent-boot/                   # Spring Boot 智能体（含独立 README）
├── course-report/                # 课程设计报告：LaTeX 源码、PDF、配图
├── README.md                     # 本文件（总入口）
└── .gitignore
```

## 快速导航

- Spring MVC 主系统构建与部署：[README-mvc.md](README-mvc.md)
- Spring Boot 智能体启动：[agent-boot/README.md](agent-boot/README.md)
- 课程设计报告：[`course-report/课程设计报告.pdf`](course-report/课程设计报告.pdf)（LaTeX 源码在同目录）
- 演示账号：`admin / author / auditor / guest`，初始密码均为 `123456`

## 两套架构的区别

- **Spring MVC 主系统**是课程要求的主实现，遵循传统 SSM 分层，依赖外置 Tomcat，不使用 Spring Boot。
- **Spring Boot 智能体**是把 M4 智能体与运维模块单独抽出的实现，内嵌 Tomcat、一条 `java -jar` 即可运行；其卡片与建议在启动时校验真实页面路径和权限码，对不上即启动失败。