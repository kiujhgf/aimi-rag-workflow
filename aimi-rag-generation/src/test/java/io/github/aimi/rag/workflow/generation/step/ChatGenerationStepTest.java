package io.github.aimi.rag.workflow.generation.step;

import io.github.aimi.rag.workflow.core.exception.StepExecuteException;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.resolver.InputResolver;
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
        when(response.getResults()).thenReturn(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
        return chatModel;
    }

    @Test
    void shouldBuildUserPromptAndWriteAnswer() throws Exception {
        ChatModel chatModel = mockModelReturning("模型答案");
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel))
                .userPrompt("{question}")
                .build();

        FlowContext context = FlowContext.create()
                .set("conversation_id", "conv-1")
                .set("question", "你好");

        step.process(context);

        assertEquals("模型答案", context.get("answer", String.class));
    }

    @Test
    void shouldBuildSystemAndUserPrompt() throws Exception {
        ChatModel chatModel = mockModelReturning("ok");
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel))
                .systemPrompt("你是助手")
                .userPrompt("你好")
                .build();

        step.process(FlowContext.create().set("conversation_id", "conv-1"));

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

        step.process(FlowContext.create().set("conversation_id", "conv-1"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        List<Message> instructions = captor.getValue().getInstructions();
        assertEquals(1, instructions.size());
        assertEquals("仅用户消息", instructions.get(0).getText());
    }

    @Test
    void shouldResolvePlaceholdersFromContext() throws Exception {
        ChatModel chatModel = mockModelReturning("ok");
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel))
                .systemPrompt("你是{role}")
                .userPrompt("问题：{question}")
                .build();

        step.process(FlowContext.create()
                .set("conversation_id", "conv-1")
                .set("role", "翻译官")
                .set("question", "什么是 RAG"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        List<Message> instructions = captor.getValue().getInstructions();
        assertEquals("你是翻译官", instructions.get(0).getText());
        assertEquals("问题：什么是 RAG", instructions.get(1).getText());
    }

    @Test
    void shouldUseCustomOutputResolver() throws Exception {
        ChatModel chatModel = mockModelReturning("模型答案");
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel))
                .userPrompt("你好")
                .outputResolver((answer, ctx) -> ctx.set("custom_answer", answer))
                .build();

        FlowContext context = FlowContext.create().set("conversation_id", "conv-1");
        step.process(context);

        assertEquals("模型答案", context.get("custom_answer", String.class));
        assertNull(context.get("answer"));
    }

    @Test
    void shouldUseCustomInputResolver() {
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(mock(ChatModel.class)))
                .userPrompt("你好")
                .inputResolver(ctx -> "resolved-" + ctx.get("user_id", String.class))
                .build();

        assertEquals("resolved-u1", step.resolveInput(FlowContext.create().set("user_id", "u1")));
    }

    @Test
    void shouldNotFailWithoutConversationId() throws Exception {
        ChatModel chatModel = mockModelReturning("ok");
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel))
                .userPrompt("你好")
                .build();

        step.process(FlowContext.create());

        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void shouldFailWhenModelThrows() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));

        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel))
                .userPrompt("你好")
                .build();
        FlowContext context = FlowContext.create().set("conversation_id", "conv-1");

        assertThrows(StepExecuteException.class, () -> step.process(context));
    }

    @Test
    void shouldFailWhenNoPromptConfigured() {
        ChatModel chatModel = mockModelReturning("ok");
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel)).build();

        assertThrows(StepExecuteException.class,
                () -> step.process(FlowContext.create().set("conversation_id", "conv-1")));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void shouldPutColdStreamIntoContext() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse first = streamChunk("你");
        ChatResponse second = streamChunk("好");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(first, second));

        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel))
                .userPrompt("{question}")
                .streaming(true)
                .build();

        FlowContext context = FlowContext.create()
                .set("conversation_id", "conv-1")
                .set("question", "你好");

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
