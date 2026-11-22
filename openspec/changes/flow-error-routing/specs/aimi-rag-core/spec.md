## ADDED Requirements

### Requirement: Error routing edge

The aimi-rag-core module SHALL provide a reserved `ERROR` status on `FlowEdge` for error-routing edges. Error edges SHALL NOT be selected by normal routing (decider result matching or wildcard).

#### Scenario: Error edge identification

- **WHEN** a `FlowEdge` is created with status `"ERROR"`
- **THEN** `isError()` returns `true` and `isWildcard()` returns `false`

#### Scenario: Normal routing ignores error edges

- **WHEN** a node has both a wildcard edge and an ERROR edge
- **AND** the node completes successfully
- **THEN** the flow routes through the wildcard edge, never the ERROR edge

### Requirement: StepErrorInfo model

The aimi-rag-core module SHALL provide `StepErrorInfo` carrying failure details of a step, written into `FlowContext` under the fixed key `flow.step.error` before routing to an error-handling node.

#### Scenario: Error info content

- **WHEN** step `parseStep` fails with exception `e` and an error route exists
- **THEN** `context.get("flow.step.error")` returns a `StepErrorInfo` whose `stepName` is `parseStep`, `error` is `e`, `errorMessage` is `e.getMessage()`, and `timestamp` is set

### Requirement: onFailure DSL

`FlowBuilder` SHALL provide `onFailure(Step)` and `onFailureToEnd()` to bind an error exit to the current step node.

#### Scenario: Bind error step

- **WHEN** `.step(A).onFailure(B).step(C)` is built
- **THEN** the graph contains an ERROR edge from A to B
- **AND** the normal path contains a wildcard edge from A to C (chaining continues on the normal path)

#### Scenario: Route to END on failure

- **WHEN** `.step(A).onFailureToEnd()` is built
- **THEN** the graph contains an ERROR edge from A to the END node

#### Scenario: onFailure after non-step node

- **WHEN** `onFailure(...)` is called right after `decider(...)` or before any step
- **THEN** a `FlowValidationException` is thrown

### Requirement: Error routing at runtime

When a step fails after retries (throws `StepExecuteException`) or returns `FAILED` status, the flow SHALL route to the error-handling node if an ERROR edge exists; otherwise the flow SHALL abort with `FlowException` as before.

#### Scenario: Route to error handler

- **WHEN** step A fails after exhausting retries
- **AND** A has an ERROR edge to step B
- **THEN** `onStepFailure` listeners are notified, `StepErrorInfo` is written into the context, B executes, and the flow continues from B's normal outgoing edges

#### Scenario: No error route preserves current behavior

- **WHEN** step A fails and has no ERROR edge
- **THEN** the flow aborts by throwing `FlowException`

#### Scenario: Error handler fails without its own route

- **WHEN** step A fails and routes to error handler B
- **AND** B fails and has no ERROR edge
- **THEN** the flow aborts by throwing `FlowException`

#### Scenario: Failure inside parallel branch

- **WHEN** a step inside a parallel branch fails and has an ERROR edge to another node within the same parallel region
- **THEN** the branch routes to the error node and the branch continues instead of failing the whole parallel group
