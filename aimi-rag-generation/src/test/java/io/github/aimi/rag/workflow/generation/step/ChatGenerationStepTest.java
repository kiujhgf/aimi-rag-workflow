package io.github.aimi.rag.workflow.generation.step;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatGenerationStepTest {

    private static ChatModel mockModelReturning(String answer) {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(new AssistantMessage(answer));
        when(response.getResult()).thenReturn(generation);
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
        return chatModel;
    }

    @Test
    void shouldUseMessagesFromContextAndWriteAnswer() throws Exception {
        ChatModel chatModel = mockModelReturning("模型答案");
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel)).build();

        FlowContext context = FlowContext.create().set("messages", List.of(new UserMessage("你好")));

        step.process(context);

        assertEquals("模型答案", context.get("answer", String.class));
    }

    @Test
    void shouldPassContextMessagesToModel() throws Exception {
        ChatModel chatModel = mockModelReturning("ok");
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel)).build();

        List<Message> messages = List.of(new UserMessage("第一条"), new UserMessage("第二条"));
        FlowContext context = FlowContext.create().set("messages", messages);

        step.process(context);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        assertEquals(messages, captor.getValue().getInstructions());
    }

    @Test
    void shouldUseCustomInputResolver() throws Exception {
        ChatModel chatModel = mockModelReturning("ok");
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel))
                .inputResolver(ctx -> List.of(new UserMessage(ctx.get("custom_prompt", String.class))))
                .build();

        FlowContext context = FlowContext.create().set("custom_prompt", "自定义输入");

        step.process(context);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        assertEquals("自定义输入", captor.getValue().getInstructions().get(0).getText());
    }

    @Test
    void shouldUseCustomOutputResolver() throws Exception {
        ChatModel chatModel = mockModelReturning("模型答案");
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel))
                .outputResolver((answer, ctx) -> ctx.set("custom_answer", answer))
                .build();

        FlowContext context = FlowContext.create().set("messages", List.of(new UserMessage("你好")));

        step.process(context);

        assertEquals("模型答案", context.get("custom_answer", String.class));
        assertNull(context.get("answer"));
    }

    @Test
    void shouldFailWhenModelThrows() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));

        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel)).build();
        FlowContext context = FlowContext.create().set("messages", List.of(new UserMessage("你好")));

        assertThrows(StepExecuteException.class, () -> step.process(context));
    }

    @Test
    void shouldBuildSystemAndUserPromptWhenMessagesEmpty() throws Exception {
        ChatModel chatModel = mockModelReturning("ok");
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel))
                .systemPrompt("你是助手")
                .userPrompt("你好")
                .build();

        step.process(FlowContext.create());

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        List<Message> instructions = captor.getValue().getInstructions();
        assertEquals(2, instructions.size());
        assertInstanceOf(SystemMessage.class, instructions.get(0));
        assertEquals("你是助手", instructions.get(0).getText());
        assertInstanceOf(UserMessage.class, instructions.get(1));
        assertEquals("你好", instructions.get(1).getText());
    }

    @Test
    void shouldBuildOnlyUserPromptWhenSystemPromptMissing() throws Exception {
        ChatModel chatModel = mockModelReturning("ok");
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel))
                .userPrompt("仅用户消息")
                .build();

        step.process(FlowContext.create());

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        List<Message> instructions = captor.getValue().getInstructions();
        assertEquals(1, instructions.size());
        assertEquals("仅用户消息", instructions.get(0).getText());
    }

    @Test
    void shouldIgnorePromptsWhenMessagesPresent() throws Exception {
        ChatModel chatModel = mockModelReturning("ok");
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel))
                .systemPrompt("不应生效")
                .userPrompt("也不应生效")
                .build();

        List<Message> messages = List.of(new UserMessage("来自上下文"));
        step.process(FlowContext.create().set("messages", messages));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        assertEquals(messages, captor.getValue().getInstructions());
    }

    @Test
    void shouldResolvePlaceholdersFromContext() throws Exception {
        ChatModel chatModel = mockModelReturning("ok");
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel))
                .systemPrompt("你是{role}")
                .userPrompt("问题：{question}")
                .build();

        step.process(FlowContext.create()
                .set("role", "翻译官")
                .set("question", "什么是 RAG"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        List<Message> instructions = captor.getValue().getInstructions();
        assertEquals("你是翻译官", instructions.get(0).getText());
        assertEquals("问题：什么是 RAG", instructions.get(1).getText());
    }

    @Test
    void shouldKeepUnresolvedPlaceholdersAndJsonBraces() throws Exception {
        ChatModel chatModel = mockModelReturning("ok");
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel))
                .userPrompt("输出 JSON 示例 {\"a\": 1}，保留 {missing} 占位符")
                .build();

        step.process(FlowContext.create());

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        assertEquals("输出 JSON 示例 {\"a\": 1}，保留 {missing} 占位符",
                captor.getValue().getInstructions().get(0).getText());
    }

    @Test
    void shouldFailWhenNoMessagesAndNoPrompts() {
        ChatModel chatModel = mockModelReturning("ok");
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel)).build();

        assertThrows(StepExecuteException.class, () -> step.process(FlowContext.create()));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void shouldPutColdStreamIntoContext() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse first = streamChunk("你");
        ChatResponse second = streamChunk("好");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(first, second));

        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel))
                .streaming(true)
                .build();

        FlowContext context = FlowContext.create().set("messages", List.of(new UserMessage("你好")));

        step.process(context);

        assertNull(context.get("answer"));
        verify(chatModel, never()).call(any(Prompt.class));
        verify(chatModel, never()).stream(any(Prompt.class));

        @SuppressWarnings("unchecked")
        Flux<String> stream = (Flux<String>) context.get(ChatGenerationStep.STREAM_KEY);
        assertEquals(List.of("你", "好"), stream.collectList().block());
        verify(chatModel).stream(any(Prompt.class));
    }

    private static ChatResponse streamChunk(String text) {
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(new AssistantMessage(text));
        when(response.getResult()).thenReturn(generation);
        return response;
    }
}
