# Flowable 自带表清单

> 本文是 [WORKFLOW.md](WORKFLOW.md) 的补充，列出 Flowable 引擎涉及的全部数据表、所属引擎、用途，以及本项目默认启用情况。

## 版本与来源说明

- 项目依赖 **Flowable 6.8.0**（根 `pom.xml` 的 `flowable.version`），只显式引入 `flowable-spring-boot-starter-process`（即 **Process 引擎**）。
- 配置 [application.yml](../ruoyi-admin/src/main/resources/application.yml) 中 `flowable.database-schema-update: true`，即**数据库表结构由应用启动时自动创建/升级**。
- 另有项目作者提供的兜底脚本 `script/sql/mysql/flowable_6.7.2_mysql(首次启动程序未自动生成表，可执行此脚本).sql`（文件名标注 6.7.2 是历史遗留，核心表与 6.8.0 一致），含全部 47 张表，本文表清单以该脚本为准，并结合 6.8.0 官方建表 SQL 核对。

## 引擎分组总览

| 分组（前缀） | 表数量 | 所属引擎 | 本项目默认 |
| --- | --- | --- | --- |
| `ACT_GE_*` | 2 | Process（通用） | ✅ 自动创建 |
| `ACT_RE_*` | 3 | Process（仓库） | ✅ 自动创建 |
| `ACT_RU_*` | 13 | Process（运行时） | ✅ 自动创建 |
| `ACT_HI_*` | 10 | Process（审计） | ✅ 自动创建 |
| `ACT_PROCDEF_INFO` / `ACT_EVT_LOG` | 2 | Process（辅助） | ✅ 自动创建（`ACT_EVT_LOG` 默认不记录） |
| `ACT_ID_*` | 9 | IDM（身份管理） | ❌ `flowable.idm.enabled: false`，不创建 |
| `FLW_*` | 8 | 事件注册引擎 + 批量服务 | ❌ 未引入事件注册引擎；批量表随 Process 引擎 |

合计 **47 张**。

## 一、Process 引擎表（本项目运行时使用）

### 通用数据

| 表名 | 用途 |
| --- | --- |
| `ACT_GE_BYTEARRAY` | 二进制资源存储（BPMN 流程图 XML、部署资源等） |
| `ACT_GE_PROPERTY` | 引擎属性（Schema 版本号、自增 ID 起始值等） |

### 仓库（Repository）—— 模型、部署、定义

| 表名 | 用途 |
| --- | --- |
| `ACT_RE_DEPLOYMENT` | 部署记录（一次部署包含若干资源；对应前文 `wf_deploy_form.deploy_id`） |
| `ACT_RE_MODEL` | 流程模型（BPMN 设计器保存的设计稿，`WfModelService` 直接操作） |
| `ACT_RE_PROCDEF` | 流程定义（部署后解析出的可运行定义，含 KEY、版本、挂起状态） |

### 运行时（Runtime）

| 表名 | 用途 |
| --- | --- |
| `ACT_RU_EXECUTION` | 执行实例（流程实例与每个分支的执行路径） |
| `ACT_RU_ACTINST` | 活动实例（正在运行/激活的节点） |
| `ACT_RU_TASK` | 运行中的用户任务（待办） |
| `ACT_RU_VARIABLE` | 运行时流程变量 |
| `ACT_RU_IDENTITYLINK` | 运行时参与者关联（assignee / candidateUsers / candidateGroups） |
| `ACT_RU_EVENT_SUBSCR` | 事件订阅 |
| `ACT_RU_JOB` | 异步作业（Job） |
| `ACT_RU_TIMER_JOB` | 定时作业（Timer） |
| `ACT_RU_SUSPENDED_JOB` | 挂起/暂停的作业 |
| `ACT_RU_DEADLETTER_JOB` | 死信作业（重试多次仍失败） |
| `ACT_RU_HISTORY_JOB` | 已完成、待清理的作业 |
| `ACT_RU_EXTERNAL_JOB` | 外部 Worker 作业 |
| `ACT_RU_ENTITYLINK` | 运行时实体引用（如流程实例与模型/任务的关联） |

### 审计（History）

> 官方命名为 History，但 `ACT_HI_*` 自流程实例启动即写入并持续更新（含运行中的实例/任务），不止于"已结束的历史"，语义上是一条贯穿全生命周期的**审计轨迹**，故本文称其为审计表。

| 表名 | 用途 |
| --- | --- |
| `ACT_HI_PROCINST` | 审计流程实例（启动即写入，含运行中与已结束实例轨迹） |
| `ACT_HI_ACTINST` | 审计活动实例（节点流转轨迹） |
| `ACT_HI_TASKINST` | 审计任务实例（任务创建即写入，含运行中任务；已办列表来源） |
| `ACT_HI_VARINST` | 审计流程变量 |
| `ACT_HI_DETAIL` | 审计详情（变量变更明细） |
| `ACT_HI_COMMENT` | 审计意见/评论（审批意见，对应 `FlowComment`） |
| `ACT_HI_ATTACHMENT` | 审计附件 |
| `ACT_HI_IDENTITYLINK` | 审计参与者关联 |
| `ACT_HI_ENTITYLINK` | 审计实体引用 |
| `ACT_HI_TSK_LOG` | 任务日志 |

### 辅助

| 表名 | 用途 |
| --- | --- |
| `ACT_PROCDEF_INFO` | 流程定义扩展信息（JSON，如分类、表单 formKey 等） |
| `ACT_EVT_LOG` | 事件日志（需配置 `enableEventLog` 开启才记录，默认关闭） |

## 二、IDM 引擎表（本项目关闭）

`flowable.idm.enabled: false`，注释明确「数据库不会创建 act_id_* 表，流程流转不会使用 act_id_* 相关表」。仅为完整性列出：

| 表名 | 用途 |
| --- | --- |
| `ACT_ID_USER` | 用户 |
| `ACT_ID_GROUP` | 组 |
| `ACT_ID_MEMBERSHIP` | 用户-组关系 |
| `ACT_ID_INFO` | 用户扩展信息 |
| `ACT_ID_BYTEARRAY` | IDM 二进制资源 |
| `ACT_ID_PRIV` | 权限 |
| `ACT_ID_PRIV_MAPPING` | 权限映射 |
| `ACT_ID_PROPERTY` | IDM 引擎属性 |
| `ACT_ID_TOKEN` | Token |

> 本项目的用户/角色/部门数据在 RuoYi 侧（`sys_user`/`sys_role`/`sys_dept` 等）维护，不依赖 Flowable IDM（IdentityService 未启用）。

## 三、事件注册引擎 + 批量服务表

| 表名 | 归属 | 用途 |
| --- | --- | --- |
| `FLW_EVENT_DEFINITION` | 事件注册引擎 | 事件定义 |
| `FLW_EVENT_DEPLOYMENT` | 事件注册引擎 | 事件部署 |
| `FLW_EVENT_RESOURCE` | 事件注册引擎 | 事件资源 |
| `FLW_CHANNEL_DEFINITION` | 事件注册引擎 | 通道定义 |
| `FLW_EV_DATABASECHANGELOG` | 事件注册引擎 | Liquibase 变更日志 |
| `FLW_EV_DATABASECHANGELOGLOCK` | 事件注册引擎 | Liquibase 变更锁 |
| `FLW_RU_BATCH` | 批量服务（Batch） | 批量操作（如批量迁移/删除流程实例） |
| `FLW_RU_BATCH_PART` | 批量服务（Batch） | 批量操作分片 |

> 「事件注册引擎」（Event Registry，`flowable-event-registry`）本项目未引入，对应 `FLW_EVENT_*`/`FLW_EV_*`/`FLW_CHANNEL_*` 表默认不会自动创建；`FLW_RU_BATCH`/`FLW_RU_BATCH_PART` 属于 Flowable 6.8 的批量服务，随 Process 引擎引入。

## 与 RuoYi 自定义表的配合

Flowable 自带的 `act_*`/`flw_*` 表只承载「引擎机制」（部署、定义、运行/审计实例、任务、变量、作业），而 RuoYi 业务层额外维护了 `wf_*` 自定义表承载业务信息：

- `wf_category` —— 流程分类
- `wf_form` —— 在线表单定义
- `wf_copy` —— 抄送记录
- `wf_deploy_form` —— 部署/节点与表单的快照关联

三层数据关系（以「发起一个流程」为例）：`ACT_RE_PROCDEF`（定义）→ `ACT_RU_PROCINST/EXECUTION/TASK`（运行）→ `ACT_HI_*`（审计）自流程发起即写入并全程跟踪至结束；`wf_form`/`wf_deploy_form` 在部署阶段由业务代码写入；`wf_copy` 在抄送时写入。详见 [WORKFLOW.md](WORKFLOW.md) 的「数据模型」一节。

## 专题文档（惰性加载，需要时读取）
L@WORKFLOW.md
