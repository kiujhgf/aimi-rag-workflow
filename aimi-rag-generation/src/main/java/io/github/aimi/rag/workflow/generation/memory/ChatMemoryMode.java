package io.github.aimi.rag.workflow.generation.memory;

/**
 * 对话记忆/历史策略，供 {@code ChatGenerationStep} 以高层语义选择，
 * 屏蔽 {@code MessageChatMemoryAdvisor} / {@code ChatHistoryRecordingAdvisor} 等实现细节。
 */
public enum ChatMemoryMode {

    /** 仅窗口工作记忆（默认）。 */
    WINDOW,

    /** LLM 上下文压缩记忆。 */
    COMPRESSING,

    /** 窗口工作记忆 + 全量历史留痕（含每轮 token）。 */
    ALL
}
