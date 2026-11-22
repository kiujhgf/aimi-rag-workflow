## MODIFIED Requirements

### Requirement: StepType 枚举

The `StepType` enum SHALL include a `PARSING` value for document-parsing steps.

#### Scenario: 枚举包含 PARSING

- **WHEN** `StepType.PARSING` is referenced
- **THEN** it is a valid enum constant
