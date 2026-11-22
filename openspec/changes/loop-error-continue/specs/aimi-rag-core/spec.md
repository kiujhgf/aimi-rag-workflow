## ADDED Requirements

### Requirement: Continue loop on step failure

Inside a `loop()/endLoop()` body, a step SHALL be able to declare `onFailureToLoopStart()`, routing a failure (after retries, or FAILED status) directly to the loop decider node, which then decides whether the next iteration starts.

#### Scenario: Direct continue on failure

- **WHEN** a loop body step A declares `onFailureToLoopStart()`
- **AND** A fails after exhausting retries
- **THEN** the flow routes to the loop decider, the iteration counter is incremented, and remaining body steps of the current iteration are skipped

#### Scenario: Iteration guard still applies

- **WHEN** a loop body step declares `onFailureToLoopStart()`
- **AND** the step fails on every iteration
- **THEN** the flow aborts with `FlowException` once the loop exceeds `maxIterations` (no infinite loop)

#### Scenario: Called outside a loop

- **WHEN** `onFailureToLoopStart()` is called when the builder is not inside `loop()/endLoop()`
- **THEN** a `FlowValidationException` is thrown

### Requirement: Error handler before loop continue

A loop body step SHALL be able to declare `onFailureToLoopStart(handler)`, routing failure first to an error-handling step and then to the loop decider.

#### Scenario: Handler executes then continue

- **WHEN** a loop body step A declares `onFailureToLoopStart(handler)`
- **AND** A fails after exhausting retries
- **THEN** a `StepErrorInfo` is written into the context, `handler` executes once, and the flow routes to the loop decider for the next-iteration decision

#### Scenario: Build passes validation

- **WHEN** a flow uses `onFailureToLoopStart(handler)` inside a loop
- **THEN** `build()` succeeds (no illegal-cycle rejection, since the only back edge is the LOOP edge owned by `loop()/endLoop()`)

#### Scenario: Chaining position unchanged

- **WHEN** `.step(A).onFailureToLoopStart(handler).step(B)` is declared inside a loop
- **THEN** the normal path edge is A -> B (the DSL call does not move the chain cursor)
