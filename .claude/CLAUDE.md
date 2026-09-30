# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

RuoYi-Flowable-Plus 基于 [RuoYi-Vue-Plus](https://gitee.com/dromara/RuoYi-Vue-Plus) 二次开发，在脚手架基础上扩展了 Flowable 工作流能力（流程设计、在线表单设计、流程发起/审批/跟踪等）。MIT 协议，当前处于开发阶段，主要面向学习与个人使用。

技术栈：

- 后端：Spring Boot 2.7.11 + Java 8（`<java.version>1.8</java.version>`），Maven 多模块工程，根 artifact `com.ruoyi:ruoyi-flowable-plus`，当前版本 0.8.3
- 工作流：Flowable 6.8.0（`flowable-spring-boot-starter-process`）
- 认证鉴权：Sa-Token（整合 JWT），**不是 Spring Security**（`security.excludes` 是 Sa-Token 过滤器的放行白名单）
- ORM：MyBatis-Plus 3.5.3.1 + dynamic-datasource（master/slave 动态数据源）+ p6spy（SQL 分析，dev 默认开启）
- 缓存 / 分布式锁：Redisson + lock4j
- 定时任务：xxl-job；监控：Spring Boot Admin；接口文档：springdoc-openapi（Swagger）
- 前端：Vue 2.6 + Element UI 2.15 + Vue CLI 4（`ruoyi-ui`，独立于 Maven）

## 构建与运行

后端（在仓库根目录）：

```bash
# 完整构建（跳过测试）
mvn clean package -DskipTests

# 指定环境 profile（dev 是默认，无需显式指定）
mvn clean package -DskipTests -Pprod

# 启动服务（开发期也可直接用 IDE 运行主类 com.ruoyi.RuoYiApplication）
mvn spring-boot:run -pl ruoyi-admin -am
```

后端入口是 `ruoyi-admin` 模块的主类 [RuoYiApplication.java](../ruoyi-admin/src/main/java/com/ruoyi/RuoYiApplication.java)，默认端口 `8080`，context-path `/`。打包后可用 [script/bin/ry.sh](../script/bin/ry.sh)（`start|stop|restart|status`）部署，另有 Windows 版 `ry.bat`。

前端（`ruoyi-ui/`）：

```bash
npm install
npm run dev          # 开发，代理到 http://localhost:8080
npm run lint         # eslint 检查 src 下 .js/.vue
npm run build:prod   # 生产构建
```

运行测试：

```bash
mvn test                 # 默认 profile dev 下的测试（见下方「测试约定」）
mvn -pl ruoyi-admin -Dtest=DemoUnitTest test   # 只跑单个测试类
```

## 环境与数据库

- MySQL 8：dev 默认 `localhost:3306/ry-flowable-plus`，账号 `root/root`（在 `ruoyi-admin/src/main/resources/application-dev.yml` 中配置）
- Redis：`localhost:6379`
- 配置入口 [application.yml](../ruoyi-admin/src/main/resources/application.yml)（通用 + Flowable + Sa-Token + swagger 等），按环境拆分到 `application-dev.yml` / `application-prod.yml`。环境由 Maven profile 注入：`spring.profiles.active: @profiles.active@`，根 `pom.xml` 定义了 `local`/`dev`(默认)/`prod` 三个 profile（注意：仓库未提交 `application-local.yml`，实际运行环境只有 dev/prod）
- 建表脚本在 [script/sql/mysql](../script/sql/mysql/)：主库结构 `mysql_ry_v0.8.X.sql`，历史升级脚本在 `script/sql/mysql/update/`。Flowable 的 `act_*` 表默认由应用启动时自动创建（`flowable.database-schema-update: true`），若首次未生成可用 `flowable_6.7.2_mysql(首次启动程序未自动生成表，可执行此脚本).sql` 兜底
- 演示账号：`admin/admin123`（超管）

## 模块架构

多模块依赖关系（低层 → 高层）：

```
ruoyi-common   —— 基础库，不依赖任何内部模块
ruoyi-framework —— 依赖 common（+ flowable）
ruoyi-flowable  —— 依赖 common
ruoyi-oss / ruoyi-sms / ruoyi-generator / ruoyi-job —— 各自仅依赖 common
ruoyi-system  —— 依赖 common + flowable + oss + sms
ruoyi-demo    —— 依赖 common + sms
ruoyi-admin   —— 顶层聚合模块，依赖以上所有模块
ruoyi-extend  —— 两个独立 Spring Boot 应用（不参与主 reactor）
ruoyi-ui      —— Vue 前端（非 Maven 模块）
```

各模块职责：

- [ruoyi-common](../ruoyi-common/)：通用工具与核心抽象。含统一响应 `R<T>`、`BaseController`、`BaseEntity`、登录态 `LoginUser`、`RuoYiConfig`、异常体系、枚举、XSS 过滤、EasyExcel/POI、加密、翻译（`translation`）、验证码等
- [ruoyi-framework](../ruoyi-framework/)：Spring 装配层，所有 `@Configuration` 集中在此（MyBatis-Plus、Sa-Token、Redisson、Swagger、异步线程池、校验器、国际化、Jackson、Undertow、过滤器、`aspectj` 切面如日志/限流/数据权限/防重复提交、全局异常处理）
- [ruoyi-system](../ruoyi-system/)：两大业务块 —— `com.ruoyi.system`（用户/部门/菜单/角色/字典等系统管理）与 `com.ruoyi.workflow`（工作流业务层，详见 [WORKFLOW.md](WORKFLOW.md)）
- [ruoyi-flowable](../ruoyi-flowable/)：Flowable **低层封装**（引擎服务注入、BPMN 遍历算法、监听器、流程图渲染），供 system 层复用，详见 [WORKFLOW.md](WORKFLOW.md)
- [ruoyi-generator](../ruoyi-generator/)：代码生成器（velocity 模板在 resources/vm）
- [ruoyi-job](../ruoyi-job/)：xxl-job 执行器（`@XxlJob` 定时任务）
- [ruoyi-oss](../ruoyi-oss/) / [ruoyi-sms](../ruoyi-sms/)：对象存储 / 短信（阿里云、腾讯云等）
- [ruoyi-demo](../ruoyi-demo/)：演示模块，作为业务开发范式示例
- [ruoyi-admin](../ruoyi-admin/)：唯一可启动的后端应用，所有 REST Controller 在 `com.ruoyi.web.controller` 下（`system`/`monitor`/`workflow`/`common` 等子包），以及 `application*.yml`、`logback-plus.xml`、`banner.txt`
- [ruoyi-extend](../ruoyi-extend/)：`ruoyi-monitor-admin`（Spring Boot Admin，端口 9090）与 `ruoyi-xxl-job-admin`（XXL-Job 调度中心，端口 9100）

## 工作流架构

工作流代码分布在 `ruoyi-flowable`、`ruoyi-system` 的 `com.ruoyi.workflow` 和 `ruoyi-admin` 的 `controller/workflow` 三个位置、分三层（Controller → 业务 Service → Flowable 封装）。**详细的三层职责、数据模型、关键机制（审批人解析、会签、退回/驳回/撤回、流程图高亮）和快速定位索引见 [WORKFLOW.md](WORKFLOW.md)。**

## 代码约定

- 统一响应 `R<T>`（`code`/`msg`/`data`，`R.ok()`/`R.fail()`），Controller 继承 [BaseController](../ruoyi-common/src/main/java/com/ruoyi/common/core/controller/BaseController.java) 提供 `toAjax`、`getLoginUser` 等公共方法
- MyBatis-Plus 约定（见 `application.yml` 的 `mybatis-plus` 配置）：扫包 `com.ruoyi.**.mapper` 与 `com.ruoyi.**.domain`，XML 在 `resources/mapper/**/*Mapper.xml`；Mapper 继承 `BaseMapperPlus`，实体继承 `BaseEntity`（含 createTime/updateTime 等公共字段）
- 全局逻辑删除：`del_flag` 字段，删除值 `2`、未删除值 `0`；主键默认雪花 ID（`ASSIGN_ID`）
- Bean 校验用分组 `AddGroup`/`EditGroup`/`QueryGroup`（[ruoyi-common](../ruoyi-common/src/main/java/com/ruoyi/common/core/validate/)）
- 常用技术栈写法：Lombok（`@Data`、`@RequiredArgsConstructor`）、SLF4J（`@Slf4j`）、Hutool、权限注解 `@SaCheckPermission`
- 单类不超过 800 行、单方法不超过 100 行

## 测试约定

测试用 JUnit 5，位于 `ruoyi-admin/src/test/java`。surefire 通过 `<groups>${profiles.active}</groups>` 与 `<excludedGroups>exclude</excludedGroups>` 按 `@Tag` 过滤测试：

- 无 `@Tag` 的测试在任何 profile 下都会执行
- `@Tag("dev")` / `@Tag("local")` / `@Tag("prod")` 仅在对应 profile 激活时执行（dev 为默认）
- `@Tag("exclude")` 永远被排除

因此默认 `mvn test` 跑「无标签 + `@Tag("dev")`」的测试；用 `-Plocal`/`-Pprod` 切换。

注意：`ruoyi-admin/src/test/java/com/ruoyi/test/` 下的 `AssertUnitTest` 等是作者留的注解用法演示，其中多条断言**故意会失败**，不要把它们当作必须通过的回归测试。

## Git / 协作约定

- 项目仓库：Gitee `KonBAI-Q/ruoyi-flowable-plus`，GitHub `KonBAI-Q/RuoYi-Flowable-Plus`
- 提 PR 统一提交到 `develop` 开发分支（README 约定）；本地主跟踪分支为 `0.8.X`
- 提交信息遵循 Angular 规范，英文描述变更，不超过 50 字（用户偏好）

## 专题文档（惰性加载，需要时读取）

L@WORKFLOW.md
L@FLOWABLE-TABLES.md