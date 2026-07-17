## ADDED Requirements

### Requirement: MyBatisFlowExecutionRepository

The aimi-rag-log-mybatis module SHALL provide `MyBatisFlowExecutionRepository` implementing `FlowExecutionRepository` using MyBatis.

#### Scenario: Create execution
- **WHEN** `createExecution(flowName, inputContext)` is called
- **THEN** inserts `FlowExecution` entity using `FlowExecutionMapper.insert()`
- **AND** returns UUID execution ID

#### Scenario: Save step execution
- **WHEN** `saveStepExecution(executionId, record)` is called
- **THEN** inserts `StepExecution` entity using `StepExecutionMapper.insert()`

#### Scenario: Save step failure
- **WHEN** `saveStepFailure(executionId, record)` is called
- **THEN** inserts `StepFailure` entity using `StepFailureMapper.insert()`

#### Scenario: Update execution status
- **WHEN** `updateExecutionStatus(executionId, status)` is called
- **THEN** updates `flow_execution` table using `FlowExecutionMapper.updateStatus()`
- **AND** sets end time to current time

#### Scenario: Get execution history
- **WHEN** `getExecutionHistory(executionId)` is called
- **THEN** queries `step_execution` table using `StepExecutionMapper.selectByExecutionId()`
- **AND** returns list of `StepExecutionRecord`

### Requirement: DatabaseInitializer

The aimi-rag-log-mybatis module SHALL provide `DatabaseInitializer` for automatically creating database tables if they don't exist.

#### Scenario: Auto-create tables
- **WHEN** Spring context initializes with `aimi.rag.auto-init-tables=true`
- **THEN** creates `flow_execution`, `step_execution`, `step_failure` tables if not exists
- **AND** uses `CREATE TABLE IF NOT EXISTS` for idempotency

#### Scenario: Database dialect support
- **WHEN** connected to different databases (MySQL, PostgreSQL, H2, SQLite, SQL Server, Oracle)
- **THEN** DDL is adjusted for database-specific syntax
- **AND** INDEX statements are skipped for SQLite/SQL Server
- **AND** VARCHAR/TEXT are converted to VARCHAR2/CLOB for Oracle

### Requirement: Auto-configuration

The aimi-rag-log-mybatis module SHALL provide `MyBatisFlowExecutionAutoConfiguration` for automatic Spring Boot configuration.

#### Scenario: Auto-configuration enabled
- **WHEN** `aimi.rag.step-log-jdbc=true` (default)
- **THEN** Spring Boot auto-configures:
  - `DatabaseInitializer` bean
  - `FlowExecutionRepository` bean (MyBatisFlowExecutionRepository)
  - `PersistentExecutionListener` bean
  - Mapper scanning via `@MapperScan`

#### Scenario: Auto-configuration disabled
- **WHEN** `aimi.rag.step-log-jdbc=false`
- **THEN** all beans from this module are not registered

#### Scenario: Table initialization disabled
- **WHEN** `aimi.rag.auto-init-tables=false`
- **THEN** `DatabaseInitializer` is not registered
- **AND** user must manually create database tables

### Requirement: Mapper interfaces

The aimi-rag-log-mybatis module SHALL provide MyBatis Mapper interfaces.

#### Scenario: FlowExecutionMapper
- **WHEN** `FlowExecutionMapper` is used
- **THEN** provides `insert(FlowExecution)` and `updateStatus(executionId, status, endTime)` methods

#### Scenario: StepExecutionMapper
- **WHEN** `StepExecutionMapper` is used
- **THEN** provides `insert(StepExecution)` and `selectByExecutionId(executionId)` methods

#### Scenario: StepFailureMapper
- **WHEN** `StepFailureMapper` is used
- **THEN** provides `insert(StepFailure)` method

### Requirement: Entity classes

The aimi-rag-log-mybatis module SHALL provide entity classes for MyBatis mapping.

#### Scenario: FlowExecution entity
- **WHEN** `FlowExecution` entity is created
- **THEN** maps to `flow_execution` table with fields:
  - `id` (String)
  - `flowName` (String)
  - `status` (String)
  - `startTime` (Instant)
  - `endTime` (Instant)
  - `inputContext` (String)

#### Scenario: StepExecution entity
- **WHEN** `StepExecution` entity is created
- **THEN** maps to `step_execution` table with fields:
  - `id` (String)
  - `executionId` (String)
  - `stepName` (String)
  - `status` (String)
  - `startTime` (Instant)
  - `endTime` (Instant)

#### Scenario: StepFailure entity
- **WHEN** `StepFailure` entity is created
- **THEN** maps to `step_failure` table with fields:
  - `id` (String)
  - `executionId` (String)
  - `stepName` (String)
  - `errorMessage` (String)
  - `errorStack` (String)
  - `timestamp` (Instant)