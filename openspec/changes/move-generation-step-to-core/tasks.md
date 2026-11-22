# 开发计划：GenerationStep 上移到 core

## 概述

将 `GenerationStep` 从 `aimi-rag-query` 迁至 `aimi-rag-core` 的 `core.step` 包，
并调整相关模块依赖。

**模块**：aimi-rag-core、aimi-rag-query、aimi-rag-generation

---

## 1. core 迁移

- [x] 1.1 新增 `core/step/GenerationStep.java`
  - package 改为 `io.github.aimi.rag.workflow.core.step`
  - 去掉对同包 `AbstractStep` 的 import
- [x] 1.2 `aimi-rag-core/pom.xml` 增加 `spring-ai-model` 依赖

---

## 2. 清理旧位置与依赖

- [x] 2.1 删除 `query/step/generation/GenerationStep.java`
- [x] 2.2 `aimi-rag-query/pom.xml` 移除 `spring-ai-model` 依赖

---

## 3. 更新引用

- [x] 3.1 `ChatGenerationStep` 改 import 为 `core.step.GenerationStep`
- [x] 3.2 `aimi-rag-generation/pom.xml` 移除 `aimi-rag-query` 依赖

---

## 4. 验证

- [x] 4.1 全量编译通过
- [x] 4.2 单测通过（core / generation）
