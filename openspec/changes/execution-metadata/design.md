# 设计文档：执行元数据（executionId 生成上移 + 当前 Step 追踪）

## 1. 设计目标

- `executionId` 的生成从持久化层上移到 `Flow.execute()`，与数据库解耦
- Repository 接口去 `executionId` 参数，统一从 context 读取
- record 不再承载 `executionId` 字段
- `__current_step` 在 `AbstractStep` 通用入口写入（仅 Step）

## 2. 元数据 key 常量

新建 `io.github.aimi.rag.workflow.core.model.FlowContextKeys`：

```java
public final class FlowContextKeys {
    public static final String EXECUTION_ID = "__execution_id";
    public static final String CURRENT_STEP = "__current_step";

    private FlowContextKeys() {
    }
}
```

`PersistentExecutionListener.EXECUTION_ID_KEY` 由 `FlowContextKeys.EXECUTION_ID` 取代。

## 3. executionId 生成上移

### 3.1 Flow.execute()

在 context 判空之后、`notifyFlowStart` 之前：

```java
public FlowContext execute(FlowContext context) {
    if (context == null) {
        context = FlowContext.create();
    }
    context.setIfAbsent(FlowContextKeys.EXECUTION_ID, UUID.randomUUID().toString());
    notifyFlowStart(context);
    ...
}
```

- 用 `setIfAbsent`：调用方若已在 context 传入 `__execution_id`（如上游 traceId），则保留；
  否则生成新 UUID。
- 无条件执行：不依赖是否挂 listener / Repository。

### 3.2 生命周期顺序

```
execute(context)
  ├─ setIfAbsent(EXECUTION_ID, uuid)     ← 生成/写入（无 DB 依赖）
  ├─ notifyFlowStart → listener.onFlowStart
  │     └─ repository.createExecution(flowName, context)   ← 读 context 的 ID 落库
  ├─ ... step 执行（AbstractStep 写 CURRENT_STEP）
  ├─ notifyFlowComplete → listener.onFlowComplete
  │     └─ repository.updateExecutionStatus(context, "COMPLETED")
  └─ return context（含 executionId）
```

## 4. Repository 接口改造

```java
public interface FlowExecutionRepository {
    void createExecution(String flowName, FlowContext context);
    void saveStepExecution(FlowContext context, StepExecutionRecord record);
    void saveStepFailure(FlowContext context, StepFailureRecord record);
    void updateExecutionStatus(FlowContext context, String status);
    List<StepExecutionRecord> getExecutionHistory(String executionId);
}
```

| 方法 | 原签名 | 新签名 | executionId 来源 |
|------|--------|--------|---------|
| createExecution | `String createExecution(flowName, context)` | `void createExecution(flowName, context)` | `context.get(EXECUTION_ID)` |
| saveStepExecution | `void saveStepExecution(executionId, record)` | `void saveStepExecution(context, record)` | `context.get(EXECUTION_ID)` |
| saveStepFailure | `void saveStepFailure(executionId, record)` | `void saveStepFailure(context, record)` | `context.get(EXECUTION_ID)` |
| updateExecutionStatus | `void updateExecutionStatus(executionId, status)` | `void updateExecutionStatus(context, status)` | `context.get(EXECUTION_ID)` |
| getExecutionHistory | 不变 | 不变 | 调用方显式传入 |

### 4.1 record 类改动

`StepExecutionRecord` / `StepFailureRecord` 移除 `executionId` 字段：

- 删除字段、构造器参数、`getExecutionId()`
- 删除 builder 的 `executionId(...)` 方法与对应字段

> 理由：写入路径的 executionId 从 context 读取；查询路径 `getExecutionHistory(executionId)`
> 的调用方本就持有 executionId，返回记录无需重复携带。

### 4.2 JPA / MyBatis 实现改动

以 MyBatis 为例：

```java
@Override
@Transactional
public void createExecution(String flowName, FlowContext context) {
    String executionId = context.get(FlowContextKeys.EXECUTION_ID, String.class);
    FlowExecution fe = FlowExecution.builder()
            .id(executionId)               // 不再 UUID.randomUUID()
            .flowName(flowName)
            .status("RUNNING")
            .startTime(Instant.now())
            .inputContext(serializeContext(context))
            .build();
    flowExecutionMapper.insert(fe);
}

@Override
@Transactional
public void saveStepExecution(FlowContext context, StepExecutionRecord record) {
    String executionId = context.get(FlowContextKeys.EXECUTION_ID, String.class);
    StepExecution se = StepExecution.builder()
            .id(UUID.randomUUID().toString())
            .executionId(executionId)
            .stepName(record.getStepName())
            ...
            .build();
    stepExecutionMapper.insert(se);
}

@Override
@Transactional
public void updateExecutionStatus(FlowContext context, String status) {
    String executionId = context.get(FlowContextKeys.EXECUTION_ID, String.class);
    flowExecutionMapper.updateStatus(executionId, status, Instant.now());
}
```

`toStepExecutionRecord(entity)` 中删除 `.executionId(entity.getExecutionId())`。

### 4.3 PersistentExecutionListener 改动

```java
@Override
public void onFlowStart(FlowContext context) {
    repository.createExecution(flowName, context);   // 不再生成 ID、不再写 context
    // 原 updateExecutionStatus("RUNNING") 冗余（createExecution 已落 RUNNING 记录），删除
}

@Override
public void onStepSuccess(String stepName, StepStatus status, FlowContext context) {
    StepExecutionRecord record = StepExecutionRecord.builder()
            .stepName(stepName)               // 不再填 executionId
            .status(status)
            .startTime(...)
            .endTime(...)
            .build();
    repository.saveStepExecution(context, record);
}

@Override
public void onStepFailure(String stepName, Throwable error, FlowContext context) {
    StepFailureRecord record = StepFailureRecord.builder()
            .stepName(stepName)               // 不再填 executionId
            .errorMessage(error.getMessage())
            .errorStack(getStackTrace(error))
            .timestamp(Instant.now())
            .build();
    repository.saveStepFailure(context, record);
}

@Override
public void onFlowComplete(FlowContext context) {
    repository.updateExecutionStatus(context, "COMPLETED");
}

@Override
public void onFlowFailure(Throwable error, FlowContext context) {
    repository.updateExecutionStatus(context, "FAILED");
}
```

## 5. `__current_step` 写入（仅 Step）

在 `AbstractStep.process()` 方法最前写入：

```java
@Override
public StepStatus process(FlowContext context) throws StepExecuteException {
    context.set(FlowContextKeys.CURRENT_STEP, getName());
    int attempts = 0;
    ...
}
```

- 覆盖所有继承 `AbstractStep` 的 Step（含自定义 Step、Start/End/ParallelStart/ParallelEnd）
- **局限**：decider 节点不经过 `AbstractStep`，执行期间 `__current_step` 停留在上一个 Step
  （本次按用户决策不覆盖 decider）

## 6. 改动清单

| 文件 | 改动 |
|------|------|
| `core/model/FlowContextKeys.java` | 新增，`EXECUTION_ID` / `CURRENT_STEP` |
| `core/flow/Flow.java` | `execute()` 中 `setIfAbsent` 生成 executionId |
| `core/flow/FlowExecutionRepository.java` | 接口方法签名调整（去 executionId 参数） |
| `core/flow/StepExecutionRecord.java` | 移除 `executionId` 字段 |
| `core/flow/StepFailureRecord.java` | 移除 `executionId` 字段 |
| `core/flow/PersistentExecutionListener.java` | 适配新接口，删除生成逻辑 |
| `core/step/AbstractStep.java` | `process()` 写入 `CURRENT_STEP` |
| `log-jpa/.../JpaFlowExecutionRepository.java` | 实现适配 |
| `log-mybatis/.../MyBatisFlowExecutionRepository.java` | 实现适配 |
| 测试 | 新增 executionId 生成 / repository 读取 / current_step 用例 |
