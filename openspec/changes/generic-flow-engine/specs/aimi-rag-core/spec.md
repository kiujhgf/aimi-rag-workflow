## Flow 重新开发规范

### 1. 核心模型变更

#### 1.1 StepStatus 枚举

`StepStatus` 由类改为枚举，仅包含三种控制流状态。自定义状态通过 `FlowDecider` 的 String 返回值实现。

```java
public enum StepStatus {
    FINISHED,  // 正常执行下一个节点
    FAILED,    // 失败，暂停并停止执行
    END        // 直接到结束节点
}
```

##### Scenario: Step 返回 FINISHED
- **WHEN** Step 执行成功
- **THEN** getStatus() 返回 FINISHED
- **AND** 流程继续执行下一个节点

##### Scenario: Step 返回 FAILED
- **WHEN** Step 执行失败
- **THEN** getStatus() 返回 FAILED
- **AND** 流程停止并抛出异常

##### Scenario: Step 返回 END
- **WHEN** Step 返回 END 状态
- **THEN** 流程直接跳转到结束节点

---

#### 1.2 Step 接口变更

移除 `getId()` 方法，添加重试相关方法，移除 `validate()` 相关方法。

```java
public interface Step<I, O> {
    String getName();
    StepType getType();
    FlowCategory getCategory();
    I resolveInput(FlowContext context) throws ResolveInputException;
    Class<I> getInputType();
    RepeatStatus process(FlowContext context) throws StepExecuteException;
    StepStatus getStatus();
    
    // 重试相关
    int getRetryTimes();
    void setRetryTimes(int retryTimes);
    Class<? extends Throwable>[] getRetryableExceptions();
}
```

##### Scenario: Step 移除 getId
- **WHEN** 调用 Step.getId()
- **THEN** 编译错误（方法已移除）

##### Scenario: Step 重试次数配置
- **WHEN** 创建 Step 并调用 setRetryTimes(3)
- **THEN** getRetryTimes() 返回 3
- **AND** process() 方法最多重试 3 次

##### Scenario: Step 默认重试次数
- **WHEN** 创建 Step 未设置重试次数
- **THEN** getRetryTimes() 返回 1（只执行一次）

---

#### 1.3 AbstractStep 变更

移除 id 字段和 UUID 生成，实现重试逻辑。

```java
public abstract class AbstractStep<I, O> implements Step<I, O> {
    protected InputResolver<I> inputResolver;
    protected OutputResolver<O> outputResolver;
    protected StepStatus status = StepStatus.FINISHED;
    protected RepeatStatus repeatStatus = RepeatStatus.FINISHED;
    
    // 重试配置
    protected int retryTimes = 1;
    protected Class<? extends Throwable>[] retryableExceptions = new Class[]{Exception.class};
    
    @Override
    public RepeatStatus process(FlowContext context) throws StepExecuteException {
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < retryTimes) {
            try {
                I input = resolveInput(context);
                O output = doProcess(input, context);
                writeOutput(output, context);
                this.status = StepStatus.FINISHED;
                return repeatStatus;
            } catch (Exception e) {
                lastException = e;
                attempts++;
                if (attempts >= retryTimes || !isRetryable(e)) {
                    this.status = StepStatus.FAILED;
                    throw new StepExecuteException(getName(), e);
                }
            }
        }
        
        this.status = StepStatus.FAILED;
        throw new StepExecuteException(getName(), lastException);
    }
    
    private boolean isRetryable(Exception e) {
        for (Class<? extends Throwable> clazz : retryableExceptions) {
            if (clazz.isInstance(e)) {
                return true;
            }
        }
        return false;
    }
}
```

##### Scenario: AbstractStep 重试成功
- **GIVEN** Step 配置 retryTimes = 3
- **WHEN** 前 2 次执行抛出异常，第 3 次成功
- **THEN** process() 返回成功
- **AND** status 为 FINISHED

##### Scenario: AbstractStep 重试耗尽
- **GIVEN** Step 配置 retryTimes = 3
- **WHEN** 3 次执行都抛出异常
- **THEN** process() 抛出 StepExecuteException
- **AND** status 为 FAILED

---

#### 1.4 StartStep 和 EndStep

新增内置的开始节点和结束节点，无实际业务逻辑。

```java
public class StartStep extends AbstractStep<Void, Void> {
    public static final StartStep INSTANCE = new StartStep();
    
    private StartStep() {}
    
    @Override public String getName() { return "START"; }
    @Override public StepType getType() { return StepType.START; }
    @Override public Class<Void> getInputType() { return Void.class; }
    @Override protected Void doProcess(Void input, FlowContext context) { return null; }
}

public class EndStep extends AbstractStep<Void, Void> {
    public static final EndStep INSTANCE = new EndStep();
    
    private EndStep() {}
    
    @Override public String getName() { return "END"; }
    @Override public StepType getType() { return StepType.END; }
    @Override public Class<Void> getInputType() { return Void.class; }
    @Override protected Void doProcess(Void input, FlowContext context) { return null; }
}
```

##### Scenario: StartStep 执行
- **WHEN** StartStep.process() 被调用
- **THEN** 立即返回 FINISHED
- **AND** 不修改 FlowContext

##### Scenario: EndStep 执行
- **WHEN** EndStep.process() 被调用
- **THEN** 立即返回 FINISHED
- **AND** 标记流程结束

---

### 2. 图结构设计（JGraphT）

#### 2.1 FlowNode

图节点，封装 Step 或 FlowDecider。equals/hashCode 基于 Step 的 equals 方法或 decider 的 name。

```java
public class FlowNode {
    private final Step<?, ?> step;
    private final FlowDecider decider;
    private final String name;
    
    public FlowNode(Step<?, ?> step) {
        this.step = step;
        this.decider = null;
        this.name = step.getName();
    }
    
    public FlowNode(FlowDecider decider, String name) {
        this.step = null;
        this.decider = decider;
        this.name = name;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlowNode flowNode = (FlowNode) o;
        if (step != null) return step.equals(flowNode.step);
        return Objects.equals(name, flowNode.name);
    }
    
    @Override
    public int hashCode() {
        return step != null ? step.hashCode() : Objects.hashCode(name);
    }
}
```

##### Scenario: FlowNode 基于 Step equals
- **GIVEN** 两个 FlowNode 包装同一个 Step
- **WHEN** 调用 equals()
- **THEN** 返回 true

##### Scenario: FlowNode 基于 decider name
- **GIVEN** 两个 FlowNode 包装不同 decider 但同名
- **WHEN** 调用 equals()
- **THEN** 返回 true

---

#### 2.2 FlowEdge

图的边，携带状态条件。

```java
public class FlowEdge {
    private final String status;
    
    public FlowEdge() {
        this.status = "*";
    }
    
    public FlowEdge(String status) {
        this.status = status;
    }
    
    public String getStatus() { return status; }
}
```

##### Scenario: FlowEdge 默认条件
- **WHEN** 创建 FlowEdge 不传参数
- **THEN** getStatus() 返回 "*"

##### Scenario: FlowEdge 带条件
- **WHEN** 创建 FlowEdge("VECTOR")
- **THEN** getStatus() 返回 "VECTOR"

---

#### 2.3 Flow 类

流程执行器，内部使用 JGraphT 的有向图。

```java
public class Flow {
    public static final Step<?, ?> START_STEP = StartStep.INSTANCE;
    public static final Step<?, ?> END_STEP = EndStep.INSTANCE;
    
    private final DirectedGraph<FlowNode, FlowEdge> graph;
    private final FlowNode startNode;
    private final FlowNode endNode;
    private final List<FlowExecutionListener> listeners = new ArrayList<>();
    
    public void addListener(FlowExecutionListener listener) {
        listeners.add(listener);
    }
    
    public FlowContext execute(FlowContext context) {
        // 执行逻辑
    }
}
```

##### Scenario: Flow 空流程
- **WHEN** FlowBuilder.create().build()
- **THEN** 返回只包含 START 和 END 节点的 Flow
- **AND** execute() 直接返回 context

---

### 3. FlowBuilder DSL

#### 3.1 基础语法

```java
// 空流程
Flow emptyFlow = FlowBuilder.create().build();

// 等价于
Flow emptyFlow2 = FlowBuilder.create().start().end().build();

// 顺序流程
Flow sequentialFlow = FlowBuilder.create()
    .step(inputStep)
    .step(chunkingStep)
    .step(storageStep)
    .build();
```

##### Scenario: 空流程构建
- **WHEN** FlowBuilder.create().build()
- **THEN** 返回的 Flow 只包含 START 和 END 节点

##### Scenario: 顺序流程构建
- **WHEN** FlowBuilder.create().step(s1).step(s2).step(s3).build()
- **THEN** 图中包含 START → s1 → s2 → s3 → END 的路径

---

#### 3.2 条件分支

使用 `decider()` + `on().to()` 语法。

```java
Flow conditionalFlow = FlowBuilder.create()
    .step(inputStep)
    .step(chunkingStep)
    .decider(contentTypeDecider)
    .on("VECTOR").to(embeddingStep)
    .on("TEXT").to(storageStep)
    .from(embeddingStep)
    .step(storageStep)
    .build();
```

##### Scenario: 条件分支 - VECTOR 路径
- **GIVEN** decider 返回 "VECTOR"
- **WHEN** 流程执行
- **THEN** 执行 embeddingStep
- **AND** 然后执行 storageStep

##### Scenario: 条件分支 - TEXT 路径
- **GIVEN** decider 返回 "TEXT"
- **WHEN** 流程执行
- **THEN** 直接执行 storageStep
- **AND** 跳过 embeddingStep

---

#### 3.3 循环

支持三种循环方式：

```java
// 方式1：简化 loop
Flow loopFlow = FlowBuilder.create()
    .loop(loopCondition)
        .step(processingStep)
        .step(validationStep)
    .endLoop()
    .step(finalStep)
    .build();

// 方式2：复杂 loop
Flow complexLoopFlow = FlowBuilder.create()
    .loop(loopDecider)
    .build();

// 方式3：使用 decider 实现循环
Flow manualLoopFlow = FlowBuilder.create()
    .step(step1)
    .step(step2)
    .decider(loopDecider)
    .on("LOOP").to(step1)
    .on("END").to(Flow.END_STEP)
    .build();
```

循环最大迭代次数默认 20，可配置。

##### Scenario: 简化 loop 执行
- **GIVEN** loop 中包含 step1, step2
- **WHEN** 条件为 true
- **THEN** 重复执行 step1 → step2
- **AND** 直到条件为 false 退出

##### Scenario: 循环超过最大迭代
- **GIVEN** 循环一直满足条件
- **WHEN** 迭代次数达到 maxIterations
- **THEN** 抛出 FlowValidationException
- **AND** 流程终止

##### Scenario: 循环默认最大迭代
- **WHEN** loop() 未指定 maxIterations
- **THEN** 默认使用 20

---

#### 3.4 并行

使用 `parallel()` + `on()` + `endParallel()` 语法，支持自定义 ExecutorService。

```java
// 基本并行
Flow parallelFlow = FlowBuilder.create()
    .step(step1)
    .parallel()
        .on().step(step2).step(step3)
        .on().step(step4).step(step5)
    .endParallel()
    .step(step6)
    .build();

// 自定义线程池
Flow parallelFlowWithExecutor = FlowBuilder.create()
    .step(step1)
    .parallel(customExecutor)
        .on().step(step2).step(step3)
        .on().step(step4).step(step5)
    .endParallel()
    .step(step6)
    .build();
```

并行失败策略默认 FAIL_FAST，可配置。

##### Scenario: 并行执行
- **WHEN** 遇到 parallel 节点
- **THEN** 所有 on() 分支并行执行
- **AND** 全部完成后继续执行后续步骤

##### Scenario: 并行失败 - FAIL_FAST
- **GIVEN** 失败策略为 FAIL_FAST
- **WHEN** 一个分支失败
- **THEN** 立即取消其他分支
- **AND** 流程抛出异常

##### Scenario: 自定义 ExecutorService
- **WHEN** parallel(executor) 使用自定义线程池
- **THEN** 并行任务使用该线程池执行
- **AND** 不关闭该线程池（用户管理生命周期）

---

### 4. 验证规则

#### 4.1 语法糖校验

| 规则 | 错误消息 |
|------|---------|
| loop 必须有 endLoop | "Loop not closed with endLoop()" |
| parallel 必须有 endParallel | "Parallel not closed with endParallel()" |

##### Scenario: loop 未闭合
- **WHEN** FlowBuilder 调用 .loop() 但未调用 .endLoop()
- **THEN** build() 抛出 FlowValidationException

##### Scenario: parallel 未闭合
- **WHEN** FlowBuilder 调用 .parallel() 但未调用 .endParallel()
- **THEN** build() 抛出 FlowValidationException

---

#### 4.2 节点关系校验

| 规则 | 错误消息 |
|------|---------|
| 无孤立节点 | "Unreachable nodes found: {nodes}" |
| 条件冲突 | "Duplicate condition '{status}' for decider '{name}'" |
| 并行任务互斥 | "Step '{name}' appears in multiple parallel branches" |
| 并行内部不能连接外部 | "Parallel branch cannot connect to external step" |
| 禁止嵌套并行 | "Nested parallel groups are not supported" |
| 非 loop 构造不能形成循环 | "Cycle detected without loop construct" |

##### Scenario: 检测孤立节点
- **GIVEN** 图中有一个节点无法从 START 到达
- **WHEN** build()
- **THEN** 抛出 FlowValidationException

##### Scenario: 检测条件冲突
- **GIVEN** 同一个 decider 有两个相同条件
- **WHEN** build()
- **THEN** 抛出 FlowValidationException

##### Scenario: 检测并行任务互斥
- **GIVEN** 同一个 Step 出现在两个并行分支中
- **WHEN** build()
- **THEN** 抛出 FlowValidationException

##### Scenario: 检测嵌套并行
- **GIVEN** parallel() 内部又调用 parallel()
- **WHEN** build()
- **THEN** 抛出 FlowValidationException

---

### 5. 执行逻辑

#### 5.1 基本执行流程

```
START → node1 → node2 → ... → END
```

##### Scenario: 顺序执行
- **WHEN** 流程按顺序执行
- **THEN** 每个节点按定义顺序依次执行

##### Scenario: 失败停止
- **WHEN** 某个节点返回 FAILED
- **THEN** 流程立即停止
- **AND** 抛出 FlowException

---

#### 5.2 并行执行

- 并行节点由 ParallelStartStep 和 ParallelEndStep 包裹
- 每个分支作为子流程执行
- 子流程使用独立的 FlowContext
- 执行完成后合并到主 FlowContext
- 默认合并策略：最后写入获胜

##### Scenario: 并行上下文合并
- **GIVEN** 两个并行分支都写入 context
- **WHEN** 并行执行完成
- **THEN** 两个分支的结果都合并到主 context
- **AND** 相同 key 以最后完成为准

---

#### 5.3 循环执行

- 循环入口是一个 decider 节点
- 每次循环开始前检查条件
- 达到最大迭代次数则报错终止

##### Scenario: 循环正常退出
- **WHEN** decider 返回退出状态
- **THEN** 跳出循环
- **AND** 继续执行后续节点

---

### 6. 执行监听器

#### 6.1 FlowExecutionListener 接口

```java
public interface FlowExecutionListener {
    default void onFlowStart(FlowContext context) {}
    default void onStepStart(String stepName, FlowContext context) {}
    default void onStepSuccess(String stepName, StepStatus status, FlowContext context) {}
    default void onStepFailure(String stepName, Throwable error, FlowContext context) {}
    default void onFlowComplete(FlowContext context) {}
    default void onFlowFailure(Throwable error, FlowContext context) {}
}
```

##### Scenario: 监听器通知
- **WHEN** 流程执行到各个阶段
- **THEN** 相应的监听器方法被调用

---

### 7. StepType 扩展

新增 START 和 END 类型。

```java
public enum StepType {
    INPUT,
    CHUNKING,
    EMBEDDING,
    STORAGE,
    START,  // 新增
    END     // 新增
}
```
