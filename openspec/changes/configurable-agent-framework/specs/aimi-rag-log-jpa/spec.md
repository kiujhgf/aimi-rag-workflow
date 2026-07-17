## ADDED Requirements

### Requirement: JpaFlowExecutionRepository

The aimi-rag-log-jpa module SHALL provide `JpaFlowExecutionRepository` implementing `FlowExecutionRepository` using Spring Data JPA.

#### Scenario: Create execution
- **WHEN** `createExecution(flowName, inputContext)` is called
- **THEN** creates `FlowExecution` entity and saves to database
- **AND** returns UUID execution ID

#### Scenario: Save step execution
- **WHEN** `saveStepExecution(executionId, record)` is called
- **THEN** creates `StepExecution` entity with step name, status, and timestamps

#### Scenario: Save step failure
- **WHEN** `saveStepFailure(executionId, record)` is called
- **THEN** creates `StepFailure` entity with error message and stack trace

#### Scenario: Update execution status
- **WHEN** `updateExecutionStatus(executionId, status)` is called
- **THEN** updates `FlowExecution` entity status and sets end time

#### Scenario: Get execution history
- **WHEN** `getExecutionHistory(executionId)` is called
- **THEN** returns list of `StepExecutionRecord` ordered by start time

### Requirement: DatabaseInitializer

The aimi-rag-log-jpa module SHALL provide `DatabaseInitializer` for automatically creating database tables if they don't exist.

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

The aimi-rag-log-jpa module SHALL provide `JpaFlowExecutionAutoConfiguration` for automatic Spring Boot configuration.

#### Scenario: Auto-configuration enabled
- **WHEN** `aimi.rag.step-log-jdbc=true` (default)
- **THEN** Spring Boot auto-configures:
  - `DatabaseInitializer` bean
  - `FlowExecutionRepository` bean (JpaFlowExecutionRepository)
  - `PersistentExecutionListener` bean
  - JPA repositories scanning via `@EnableJpaRepositories`
  - Entity scanning via `@EntityScan`

#### Scenario: Auto-configuration disabled
- **WHEN** `aimi.rag.step-log-jdbc=false`
- **THEN** all beans from this module are not registered

#### Scenario: Table initialization disabled
- **WHEN** `aimi.rag.auto-init-tables=false`
- **THEN** `DatabaseInitializer` is not registered
- **AND** user must manually create database tables

### Requirement: Entity classes

The aimi-rag-log-jpa module SHALL provide entity classes for JPA mapping.

#### Scenario: FlowExecution entity
- **WHEN** `FlowExecution` entity is created
- **THEN** maps to `flow_execution` table with columns:
  - `id` (VARCHAR(64)) - primary key
  - `flow_name` (VARCHAR(128)) - flow name
  - `status` (VARCHAR(32)) - execution status
  - `start_time` (TIMESTAMP) - start time
  - `end_time` (TIMESTAMP) - end time
  - `input_context` (TEXT) - serialized input context

#### Scenario: StepExecution entity
- **WHEN** `StepExecution` entity is created
- **THEN** maps to `step_execution` table with columns:
  - `id` (VARCHAR(64)) - primary key
  - `execution_id` (VARCHAR(64)) - foreign key to flow_execution
  - `step_name` (VARCHAR(128)) - step name
  - `status` (VARCHAR(32)) - step status
  - `start_time` (TIMESTAMP) - start time
  - `end_time` (TIMESTAMP) - end time

#### Scenario: StepFailure entity
- **WHEN** `StepFailure` entity is created
- **THEN** maps to `step_failure` table with columns:
  - `id` (VARCHAR(64)) - primary key
  - `execution_id` (VARCHAR(64)) - foreign key to flow_execution
  - `step_name` (VARCHAR(128)) - step name
  - `error_message` (TEXT) - error message
  - `error_stack` (TEXT) - stack trace
  - `timestamp` (TIMESTAMP) - failure time