## ADDED Requirements

### Requirement: Step interface

The aimi-rag-core module SHALL provide `Step<I, O>` interface for defining individual processing steps in a flow. Steps transform raw data `I` into `O` within `FlowContext`.

#### Scenario: Define a Step

- **WHEN** developer implements `Step<String, List<Chunk>>`
- **THEN** the step exposes `getName()` returning step name, `getType()` returning `StepType`, `getCategory()` returning `FlowCategory` (defaults to `INGEST`), and `process(FlowContext)` returning `StepStatus`

#### Scenario: Optional canProcess

- **WHEN** developer does not override `canProcess(I)`
- **THEN** default implementation returns `true`

#### Scenario: Step retry configuration

- **WHEN** `setRetryTimes(3)` is called
- **THEN** `getRetryTimes()` returns 3
- **AND** `process()` will retry up to 3 times on retryable exceptions

### Requirement: StepType enum

The aimi-rag-core module SHALL provide `StepType` enum classifying steps by their concrete role.

#### Scenario: StepType values

- **WHEN** `StepType` is referenced
- **THEN** values include `INPUT`, `CHUNKING`, `EMBEDDING`, `STORAGE`, `START`, `END`

### Requirement: FlowCategory enum

The aimi-rag-core module SHALL provide `FlowCategory` enum for top-level pipeline classification.

#### Scenario: FlowCategory values

- **WHEN** `FlowCategory` is referenced
- **THEN** values include `INGEST`, `QUERY`, `DELTA`

### Requirement: FlowException

The aimi-rag-core module SHALL provide `FlowException` for flow-level error handling.

#### Scenario: Step throws exception

- **WHEN** any Step throws an exception during flow execution
- **THEN** it is wrapped in FlowException with step name and original exception

### Requirement: FlowExecutionRepository

The aimi-rag-core module SHALL provide `FlowExecutionRepository` interface for persisting flow execution records.

#### Scenario: Create execution

- **WHEN** `createExecution(flowName, inputContext)` is called
- **THEN** returns execution ID string
- **AND** creates a flow_execution record in database

#### Scenario: Save step execution

- **WHEN** `saveStepExecution(executionId, record)` is called
- **THEN** creates a step_execution record in database

#### Scenario: Save step failure

- **WHEN** `saveStepFailure(executionId, record)` is called
- **THEN** creates a step_failure record in database

#### Scenario: Update execution status

- **WHEN** `updateExecutionStatus(executionId, status)` is called
- **THEN** updates flow_execution record with end time

#### Scenario: Get execution history

- **WHEN** `getExecutionHistory(executionId)` is called
- **THEN** returns list of StepExecutionRecord

### Requirement: PersistentExecutionListener

The aimi-rag-core module SHALL provide `PersistentExecutionListener` implementing `FlowExecutionListener` for persisting execution events.

#### Scenario: Listener registration

- **WHEN** `PersistentExecutionListener` is added to Flow
- **THEN** all execution events (flow start, step start, step success, step failure, flow complete) are persisted to database

### Requirement: FlowContext

The aimi-rag-core module SHALL provide `FlowContext` for thread-safe shared data storage during flow execution.

#### Scenario: Context write/read

- **WHEN** `context.set(key, value)` is called
- **THEN** `context.get(key, type)` returns the value
- **AND** value is available to all subsequent steps

#### Scenario: Context type conversion

- **WHEN** `context.get("count", Integer.class)` is called
- **THEN** returns Integer value, or throws TypeMismatchException if type mismatch

### Requirement: InputResolver / OutputResolver

The aimi-rag-core module SHALL provide `InputResolver<I>` and `OutputResolver<O>` for resolving input from context and writing output to context.

#### Scenario: DefaultInputResolver

- **WHEN** `DefaultInputResolver.ByKeyResolver("texts", List.class, name)` is used
- **THEN** `resolve(context)` returns value from context under key "texts"

#### Scenario: DefaultOutputResolver

- **WHEN** `DefaultOutputResolver("chunks")` is used
- **THEN** `resolve(output, context)` sets value in context under key "chunks"