## REMOVED Requirements

### Requirement: GenerationStep 基于消息的契约

**Reason**: `GenerationStep` 已上移到 `aimi-rag-core` 模块，职责更贴合「通用生成步骤」定位。

**Migration**: 引用 `io.github.aimi.rag.workflow.query.step.generation.GenerationStep` 的代码改为
`io.github.aimi.rag.workflow.core.step.GenerationStep`；`aimi-rag-query` 的 `spring-ai-model` 依赖一并移除。
