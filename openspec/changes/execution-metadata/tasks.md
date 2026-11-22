# 任务清单：执行元数据

- [x] Task 1: 新增 `FlowContextKeys` 常量类（`EXECUTION_ID` / `CURRENT_STEP`）
- [x] Task 2: `Flow.execute()` 中 `setIfAbsent` 生成并写入 executionId
- [x] Task 3: 调整 `FlowExecutionRepository` 接口方法签名（去 executionId 参数，改从 context 读）
- [x] Task 4: `StepExecutionRecord` / `StepFailureRecord` 移除 `executionId` 字段
- [x] Task 5: 适配 `PersistentExecutionListener`（删除生成逻辑，用新接口）
- [x] Task 6: `AbstractStep.process()` 写入 `CURRENT_STEP`
- [x] Task 7: 适配 `JpaFlowExecutionRepository` 实现
- [x] Task 8: 适配 `MyBatisFlowExecutionRepository` 实现
- [x] Task 9: 单元测试（executionId 生成 / repository 从 context 读 / current_step）
