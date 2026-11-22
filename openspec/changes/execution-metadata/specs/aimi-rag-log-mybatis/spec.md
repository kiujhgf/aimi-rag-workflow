## MODIFIED Requirements

### Requirement: MyBatis repository reads execution id from context

`MyBatisFlowExecutionRepository` SHALL obtain the execution id from the `FlowContext` rather than generating or receiving it as a parameter.

#### Scenario: createExecution uses context id

- **WHEN** `createExecution(flowName, context)` is invoked
- **THEN** the `FlowExecution` entity id equals `context.get(FlowContextKeys.EXECUTION_ID)` and no `UUID.randomUUID()` is called for the execution id

#### Scenario: saveStepExecution uses context id

- **WHEN** `saveStepExecution(context, record)` is invoked
- **THEN** the `StepExecution` entity `executionId` equals `context.get(FlowContextKeys.EXECUTION_ID)`

#### Scenario: saveStepFailure uses context id

- **WHEN** `saveStepFailure(context, record)` is invoked
- **THEN** the `StepFailure` entity `executionId` equals `context.get(FlowContextKeys.EXECUTION_ID)`

#### Scenario: updateExecutionStatus uses context id

- **WHEN** `updateExecutionStatus(context, status)` is invoked
- **THEN** the status update targets the `FlowExecution` whose id equals `context.get(FlowContextKeys.EXECUTION_ID)`

#### Scenario: history record omits execution id

- **WHEN** `getExecutionHistory(executionId)` maps an entity to a `StepExecutionRecord`
- **THEN** the mapped record does not set an `executionId` field
