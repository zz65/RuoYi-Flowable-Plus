# 工作流架构详解

> 本文是 RuoYi-Flowable-Plus 工作流模块的详细说明，是 [CLAUDE.md](CLAUDE.md) 的补充。
> 工作流代码分布在三个 Maven 模块，分三层：**Controller（接口）→ Service（业务）→ Flowable 低层封装**。

## 一、三层架构总览

| 层 | 所在模块 / 包 | 职责 |
| --- | --- | --- |
| Controller 层 | `ruoyi-admin` 的 `com.ruoyi.web.controller.workflow` | REST 接口，`/workflow/*`，Sa-Token 权限校验、参数接收、响应封装 |
| 业务 Service 层 | `ruoyi-system` 的 `com.ruoyi.workflow` | 工作流业务规则，编排 Flowable API 与自定义表 CRUD |
| Flowable 封装层 | `ruoyi-flowable` 的 `com.ruoyi.flowable` | Flowable 引擎/服务注入封装、BPMN 模型遍历算法、监听器、流程图渲染 |

数据流：前端 → `WfXxxController`（admin）→ `IWfXxxService`（system 的业务 impl）→ `FlowServiceFactory` 拿到的 `RuntimeService`/`TaskService` 等（flowable 封装）+ `WfXxxMapper`（自定义表）。

## 二、Controller 层（ruoyi-admin）

包 [com/ruoyi/web/controller/workflow](../ruoyi-admin/src/main/java/com/ruoyi/web/controller/workflow/)，7 个控制器，均用 `@SaCheckPermission("workflow:xxx:yyy")` 做权限控制：

| Controller | 路径 | 主要端点 |
| --- | --- | --- |
| [WfCategoryController](../ruoyi-admin/src/main/java/com/ruoyi/web/controller/workflow/WfCategoryController.java) | `/workflow/category` | 流程分类 CRUD：`/list`、`/listAll`、`/export`、`/{categoryId}`、`POST`、`PUT`、`DELETE` |
| [WfFormController](../ruoyi-admin/src/main/java/com/ruoyi/web/controller/workflow/WfFormController.java) | `/workflow/form` | 在线表单 CRUD + `/addDeployForm`（表单挂到部署） |
| [WfModelController](../ruoyi-admin/src/main/java/com/ruoyi/web/controller/workflow/WfModelController.java) | `/workflow/model` | 流程模型（设计器）：`/list`、`/historyList`、`/{modelId}`、`/bpmnXml/{modelId}`、`/save`、`/latest`、`/deploy`、`/export` |
| [WfDeployController](../ruoyi-admin/src/main/java/com/ruoyi/web/controller/workflow/WfDeployController.java) | `/workflow/deploy` | 部署管理：`/list`、`/publishList`、`/changeState`（挂起/激活定义）、`/bpmnXml/{definitionId}`、`/form/{deployId}` |
| [WfInstanceController](../ruoyi-admin/src/main/java/com/ruoyi/web/controller/workflow/WfInstanceController.java) | `/workflow/instance` | 实例管理：`/updateState`（挂起/激活）、`/stopProcessInstance`、`/delete`、`/detail` |
| [WfProcessController](../ruoyi-admin/src/main/java/com/ruoyi/web/controller/workflow/WfProcessController.java) | `/workflow/process` | **流程中心（核心）**：`/list`(可发起)、`/ownList`(我发起)、`/todoList`(待办)、`/claimList`(待签)、`/finishedList`(已办)、`/copyList`(抄送)、`/getProcessForm`、`/start/{processDefId}`、`/detail`、`/bpmnXml/{processDefId}`，以及各列表的 `/xxxExport` |
| [WfTaskController](../ruoyi-admin/src/main/java/com/ruoyi/web/controller/workflow/WfTaskController.java) | `/workflow/task` | 任务操作：`/complete`(审批)、`/reject`(驳回)、`/return`(退回)、`/returnList`(可回退节点)、`/claim`(签收)、`/unClaim`、`/delegate`(委派)、`/transfer`(转办)、`/stopProcess`(终止)、`/revokeProcess`(撤回)、`/processVariables/{taskId}`、`/diagram/{processId}` |

## 三、业务 Service 层（ruoyi-system 的 com.ruoyi.workflow）

9 个 `IWf*Service` 接口 + 对应 impl，主要用 `@RequiredArgsConstructor` 注入 `FlowServiceFactory`：

| Service 接口 | 核心方法 | 职责 |
| --- | --- | --- |
| [IWfCategoryService](../ruoyi-system/src/main/java/com/ruoyi/workflow/service/IWfCategoryService.java) | `insertCategory` / `updateCategory` / `deleteWithValidByIds` / `checkCategoryCodeUnique` | 流程分类（表 `wf_category`） |
| [IWfFormService](../ruoyi-system/src/main/java/com/ruoyi/workflow/service/IWfFormService.java) | `insertForm` / `updateForm` | 在线表单（表 `wf_form`） |
| [IWfModelService](../ruoyi-system/src/main/java/com/ruoyi/workflow/service/IWfModelService.java) | `list` / `historyList` / `getModel` / `insertModel` / `updateModel` / `saveModel` / `latestModel` / `deployModel` / `queryBpmnXmlById` | 流程模型，底层是 Flowable 的 `ModelService`（存 `ACT_RE_MODEL` 表） |
| [IWfDeployService](../ruoyi-system/src/main/java/com/ruoyi/workflow/service/IWfDeployService.java) | `updateState` / `queryBpmnXmlById` / `deleteByIds` | 流程部署/定义的挂起、激活、删除 |
| [IWfDeployFormService](../ruoyi-system/src/main/java/com/ruoyi/workflow/service/IWfDeployFormService.java) | `insertWfDeployForm` / `saveInternalDeployForm` | 部署与表单的关联（表 `wf_deploy_form`，解析 BpmnModel 保存各节点表单） |
| [IWfInstanceService](../ruoyi-system/src/main/java/com/ruoyi/workflow/service/IWfInstanceService.java) | `stopProcessInstance` / `updateState` / `delete` / `getHistoricProcessInstanceById` / `queryDetailProcess` | 流程实例的终止、挂起/激活、删除、详情 |
| [IWfProcessService](../ruoyi-system/src/main/java/com/ruoyi/workflow/service/IWfProcessService.java) | `selectStartProcessList` / `selectOwnProcessList` / `selectTodoProcessList` / `selectClaimProcessList` / `selectFinishedProcessList` / `selectFormContent` / `startProcessByDefId` / `startProcessByDefKey` / `queryProcessDetail` | 流程中心各列表查询、发起流程、详情 |
| [IWfTaskService](../ruoyi-system/src/main/java/com/ruoyi/workflow/service/IWfTaskService.java) | `complete` / `taskReject` / `taskReturn` / `findReturnTaskList` / `deleteTask` / `claim` / `unClaim` / `delegateTask` / `transferTask` / `stopProcess` / `revokeProcess` / `diagram` / `getProcessVariables` / `startFirstTask` | 任务级操作（办理、退回、驳回、签收、委派、转办、终止、撤回、流程图、流程变量） |

领域对象在 `com.ruoyi.workflow.domain`，分 `domain`（实体）、`bo`（入参）、`vo`（出参）、`dto`（内部传输）。Mapper 在 `com.ruoyi.workflow.mapper`。

特殊组件 [MultiInstanceHandler](../ruoyi-system/src/main/java/com/ruoyi/workflow/handler/MultiInstanceHandler.java)（`@Component("multiInstanceHandler")`）：**会签（多实例）**人员解析，见下。

## 四、Flowable 封装层（ruoyi-flowable 的 com.ruoyi.flowable）

这是与 Flowable 引擎打交道的底层，被 `ruoyi-system` 和 `ruoyi-framework` 复用。

### 服务注入：[FlowServiceFactory](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/factory/FlowServiceFactory.java)

`@Component @Getter`，注入 8 个 Flowable 服务，业务层统一从这里取，而不是各自 `@Autowired`：

- `RepositoryService`（模型/部署/定义）
- `RuntimeService`（流程实例/执行/变量）
- `IdentityService`（用户/组）
- `TaskService`（任务）
- `FormService`（表单）
- `HistoryService`（历史）
- `ManagementService`（引擎管理）
- `ProcessEngine`（`@Qualifier("processEngine")`）

### 模型遍历与算法

| 类 | 职责 |
| --- | --- |
| [FlowableUtils](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/flow/FlowableUtils.java) | **最核心的工具类**。基于 BpmnModel 做节点遍历：`getElementIncomingFlows`/`getElementOutgoingFlows`（进出连线）、`iteratorFindParentUserTasks`/`iteratorFindChildUserTasks`（查找上/下游 UserTask）、`findRoad`（查找路径）、`iteratorFindDirtyRoads`/`dfsFindRejects`/`iteratorFindRejects`/`historicTaskInstanceClean`（DFS 查找驳回/已完成节点，用于**退回/驳回/撤回**） |
| [FindNextNodeUtil](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/flow/FindNextNodeUtil.java) | 查找下一审批节点：`getNextUserTasks` 入口，`next` 递归遍历（支持排他/并行网关、子流程、条件表达式 `expressionResult`），供"查找下一审批人"功能 |
| [ModelUtils](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/utils/ModelUtils.java) | BpmnModel 解析：`getStartEvent`/`getEndEvent`、`getUserTaskByKey`、`getFormKey`（取节点表单 key）、`isMultiInstance`、`findNextUserTasks`、`isSequentialReachable`（顺序会签可达判断） |
| [ProcessUtils](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/utils/ProcessUtils.java) | 根据 `ProcessQuery` 条件构建各类 Query（`ProcessDefinitionQuery`/`TaskQuery`/`HistoricTaskInstanceQuery` 等） |
| [TaskUtils](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/utils/TaskUtils.java) | `getUserId`（当前登录用户）、`getCandidateGroup`（角色/部门候选组） |
| [ProcessFormUtils](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/utils/ProcessFormUtils.java) | `fillFormData`：递归填充表单数据 |

### 流程图渲染

三个类配合，实现**高亮已流转节点/连线**（已走绿色、当前红色）：

- [CustomProcessDiagramGenerator](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/flow/CustomProcessDiagramGenerator.java) —— 继承 `DefaultProcessDiagramGenerator`，重写 `generateProcessDiagram` 使用自定义 Canvas
- [CustomProcessDiagramCanvas](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/flow/CustomProcessDiagramCanvas.java) —— 继承 `DefaultProcessDiagramCanvas`，定义高亮色（`HIGHLIGHT_SequenceFlow_COLOR = GREEN`、`HIGHLIGHT_COLOR1 = RED` 等）
- [MyDefaultProcessDiagramCanvas](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/config/MyDefaultProcessDiagramCanvas.java) —— 自定义连线绘制颜色

### 配置与监听

| 类 | 职责 |
| --- | --- |
| [FlowableConfig](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/flow/FlowableConfig.java) | `EngineConfigurationConfigurer`，设置流程图字体为「宋体」（解决中文乱码） |
| [GlobalEventListenerConfig](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/config/GlobalEventListenerConfig.java) | 在 `ContextRefreshedEvent` 时注册 `PROCESS_COMPLETED` 全局监听 |
| [GlobalEventListener](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/listener/GlobalEventListener.java) | 流程结束回调：若流程变量 `processStatus` 为 `running`，置为 `completed` |
| [UserTaskListener](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/listener/UserTaskListener.java) | `@Component("userTaskListener")` 用户任务监听器示例（TODO，业务待实现） |

### 常量与枚举

| 类 | 关键内容 |
| --- | --- |
| [ProcessConstants](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/common/constant/ProcessConstants.java) | 审批人类型 `assignee`/`candidateUsers`/`candidateGroups`；自定义属性名 `dataType`/`userType`/`localScope`/`processStatus`；会签 `multiInstance`/`userList`；bpmn 命名空间 |
| [TaskConstants](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/common/constant/TaskConstants.java) | 发起人 `initiator`；候选组前缀 `ROLE`（角色组）/`DEPT`（部门组） |
| [ProcessStatus](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/common/enums/ProcessStatus.java) | 流程状态：`running`/`terminated`/`completed`/`canceled` |
| [FlowComment](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/common/enums/FlowComment.java) | 审批意见类型：`NORMAL`/`REBACK`(退回)/`REJECT`(驳回)/`DELEGATE`(委派)/`TRANSFER`(转办)/`STOP`(终止)/`REVOKE`(撤回) |
| [FormType](../ruoyi-flowable/src/main/java/com/ruoyi/flowable/common/enums/FormType.java) | 表单类型：`PROCESS`(流程表单)/`EXTERNAL`(外置表单)/`INDEPENDENT`(节点独立表单) |

## 五、数据模型

**自定义表**（在 `ruoyi-system` 的 `com.ruoyi.workflow.domain` 中以 `@TableName` 声明）：

| 表 | 实体 | 用途 |
| --- | --- | --- |
| `wf_category` | `WfCategory` | 流程分类 |
| `wf_form` | `WfForm` | 在线表单定义（JSON 内容） |
| `wf_copy` | `WfCopy` | 抄送记录（title、processId、instanceId、taskId、userId、originatorId） |
| `wf_deploy_form` | `WfDeployForm` | 部署/节点与表单的关联（deployId、formKey、nodeKey、content） |

**Flowable 自带表**（`act_*`，启动时 `flowable.database-schema-update: true` 自动创建，完整清单见 [FLOWABLE-TABLES.md](FLOWABLE-TABLES.md)）：

- [`ACT_RE_MODEL` / `ACT_RE_DEPLOYMENT` / `ACT_RE_PROCDEF`](FLOWABLE-TABLES.md) —— 模型、部署、流程定义（`IWfModelService` 直接操作 `ModelService`）
- [`ACT_RU_TASK` / `ACT_RU_EXECUTION` / `ACT_RU_VARIABLE`](FLOWABLE-TABLES.md) —— 运行中任务/执行/变量
- [`ACT_HI_*`](FLOWABLE-TABLES.md) —— 历史（`IWfProcessService` 的已办列表主要查历史）

> 注意：`IWfModelService` 管理的是 Flowable 的 Model（`ACT_RE_MODEL`），**没有** `wf_model` 自定义表；`WfModelVo` 只是 VO。

## 六、关键机制

### 1. 审批人类型解析（节点自定义属性 `userType`）

流程设计时给 UserTask 设置自定义属性，运行时按 `userType` 解析处理人：

- `assignee` —— 单个审批人（`Assignee`）
- `candidateUsers` —— 多个候选人
- `candidateGroups` —— 候选组，组名带前缀：`ROLE` + 角色 ID、`DEPT` + 部门 ID（见 `TaskConstants`）

### 2. 会签（多实例）人员解析：`MultiInstanceHandler`

会签节点的 `dataType` 决定人员来源（`getUserIds` 方法）：

- `USERS` —— 直接取 `candidateUsers`
- `ROLES` —— 按角色 ID 集合查 `sys_user_role` 得到用户 ID 集合
- `DEPTS` —— 按部门 ID 集合查 `sys_user` 得到用户 ID 集合

### 3. 流程状态维护

流程状态存为流程变量 `processStatus`（`running`/`terminated`/`completed`/`canceled`），配合 `GlobalEventListener` 的 `PROCESS_COMPLETED` 回调，在流程结束时把 `running` 置为 `completed`。终止/取消由 `IWfInstanceService`/`IWfTaskService` 显式写入。

### 4. 退回 / 驳回 / 撤回的实现

三者都依赖 `FlowableUtils` 对 BpmnModel 的 DFS 遍历：

- **退回（taskReturn）**：`findReturnTaskList` 用 `iteratorFindParentUserTasks`/`iteratorFindChildUserTasks` 找出可回退的上游 UserTask，再走回连线
- **驳回（taskReject）**：直接结束到结束事件（或指定节点），写 `FlowComment.REJECT` 意见
- **撤回（revokeProcess）**：用 `dfsFindRejects`/`iteratorFindRejects`/`historicTaskInstanceClean` 等找出已流转节点做历史清理，回退到发起人

### 5. 流程图高亮

`diagram(processId)`（`IWfTaskService`）→ `CustomProcessDiagramGenerator` 按历史已走节点/连线生成 PNG，绿色高亮已流转路径。

## 七、快速定位索引

| 想改什么 | 看哪里 |
| --- | --- |
| 新增/调整 REST 接口、权限点 | `ruoyi-admin` 的 `controller/workflow/WfXxxController` |
| 任务流转业务逻辑（审批/退回/驳回/撤回/签收/委派/转办） | `ruoyi-system` 的 `workflow/service/impl/WfTaskServiceImpl` |
| 各列表（待办/已办/我发起/抄送）查询逻辑 | `ruoyi-system` 的 `workflow/service/impl/WfProcessServiceImpl` |
| 发起流程、表单内容获取 | `WfProcessServiceImpl` 的 `startProcessByDefId` / `selectFormContent` |
| 直接调 Flowable API 的封装 | `ruoyi-flowable` 的 `FlowServiceFactory` + `flow/*` + `utils/*` |
| 节点遍历 / 退回 / 撤回算法 | `ruoyi-flowable` 的 `FlowableUtils`、`FindNextNodeUtil`、`ModelUtils` |
| 会签人员解析 | `ruoyi-system` 的 `workflow/handler/MultiInstanceHandler` |
| 流程图高亮渲染 | `ruoyi-flowable` 的 `CustomProcessDiagramGenerator`/`Canvas` |
| 审批人类型/候选组前缀/流程状态常量 | `ruoyi-flowable` 的 `common/constant` 与 `common/enums` |

## 专题文档（惰性加载，需要时读取）
L@FLOWABLE-TABLES.md