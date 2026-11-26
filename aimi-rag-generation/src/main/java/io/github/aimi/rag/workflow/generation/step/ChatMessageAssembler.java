package io.github.aimi.rag.workflow.generation.step;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.resolver.DefaultInputResolver;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 包内共享的消息组装逻辑，供 {@link ChatGenerationStep} 与 {@link StructuredChatGenerationStep} 复用。
 *
 * <p>仅负责本轮 prompt 组装：{@code messages} 非空时原样透传；否则先拼 systemPrompt 再拼 userPrompt；
 * 两者都为空时抛 {@link StepExecuteException}。prompt 支持 {@code {key}} 占位符，
 * 从 {@link FlowContext} 取值，未命中保留原样。</p>
 *
 * <p>历史消息不在此处拼接，统一由挂载在 {@code ChatClient} 上的
 * {@code MessageChatMemoryAdvisor} / {@code ChatHistoryRecordingAdvisor} 通过
 * {@code ChatMemory} 处理。</p>
 */
final class ChatMessageAssembler {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private ChatMessageAssembler() {}

    static List<Message> resolve(List<Message> input,
                                 String systemPrompt,
                                 String userPrompt,
                                 FlowContext context,
                                 String stepName) {
        if (input != null && !input.isEmpty()) {
            return input;
        }

        List<Message> messages = new ArrayList<>();
        String system = applyPlaceholders(systemPrompt, context);
        if (system != null && !system.isBlank()) {
            messages.add(new SystemMessage(system));
        }
        String user = applyPlaceholders(userPrompt, context);
        if (user != null && !user.isBlank()) {
            messages.add(new UserMessage(user));
        }

        if (messages.isEmpty()) {
            throw new StepExecuteException(stepName,
                    "No messages: context key 'messages' is empty and neither systemPrompt nor userPrompt is configured");
        }
        return messages;
    }

    static String applyPlaceholders(String template, FlowContext context) {
        if (template == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Object value = context.get(matcher.group(1));
            String replacement = value != null ? value.toString() : matcher.group();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static InputResolver<List<Message>> defaultInputResolver() {
        return new DefaultInputResolver.ByKeyOrDefaultResolver("messages", List.class, List.of());
    }
}
