## ADDED Requirements

### Requirement: Execution ID generated at execution time

`Flow.execute()` SHALL write an execution id into the context before any listener runs, without requiring a persistence repository.

#### Scenario: Generate when absent

- **WHEN** `execute(context)` is called with a context that has no `__execution_id`
- **THEN** a new UUID is written under `FlowContextKeys.EXECUTION_ID` before `onFlowStart` listeners run

#### Scenario: Preserve existing id

- **WHEN** `execute(context)` is called with a context that already contains `__execution_id`
- **THEN** the existing value is preserved (no overwrite)

#### Scenario: No repository required

- **WHEN** a flow has no `PersistentExecutionListener` / `FlowExecutionRepository` registered
- **THEN** the context still contains a non-null `__execution_id` after execution

### Requirement: Repository reads execution id from context

`FlowExecutionRepository` methods SHALL obtain the execution id from the `FlowContext` instead of a dedicated parameter or record field.

#### Scenario: createExecution reads from context

- **WHEN** `createExecution(flowName, context)` is invoked
- **THEN** the persistence record uses the id from `context.get(FlowContextKeys.EXECUTION_ID)` and the method returns no id

#### Scenario: saveStepExecution reads from context

- **WHEN** `saveStepExecution(context, record)` is invoked
- **THEN** the persistence record uses the id from `context.get(FlowContextKeys.EXECUTION_ID)` (the record carries no executionId)

#### Scenario: saveStepFailure reads from context

- **WHEN** `saveStepFailure(context, record)` is invoked
- **THEN** the persistence record uses the id from `context.get(FlowContextKeys.EXECUTION_ID)` (the record carries no executionId)

#### Scenario: updateExecutionStatus reads from context

- **WHEN** `updateExecutionStatus(context, status)` is invoked
- **THEN** the execution id is read from `context.get(FlowContextKeys.EXECUTION_ID)`

#### Scenario: getExecutionHistory keeps explicit id

- **WHEN** `getExecutionHistory(executionId)` is invoked
- **THEN** the explicit `executionId` parameter is used (query-time API without a context)

### Requirement: Records carry no execution id

`StepExecutionRecord` and `StepFailureRecord` SHALL NOT expose an `executionId` field.

#### Scenario: StepExecutionRecord has no execution id

- **WHEN** a `StepExecutionRecord` is constructed
- **THEN** there is no `executionId` constructor argument, field, or getter

#### Scenario: StepFailureRecord has no execution id

- **WHEN** a `StepFailureRecord` is constructed
- **THEN** there is no `executionId` constructor argument, field, or getter

### Requirement: Current step tracked in context

`AbstractStep.process()` SHALL write the current step name into the context under `FlowContextKeys.CURRENT_STEP`.

#### Scenario: Written before processing

- **WHEN** a step's `process(context)` begins
- **THEN** `context.get(FlowContextKeys.CURRENT_STEP)` equals the step's `getName()`

#### Scenario: Reflects step name

- **WHEN** step A with name "parse" executes followed by step B with name "embed"
- **THEN** after A's processing `CURRENT_STEP` is "parse", and after B's processing it is "embed"
