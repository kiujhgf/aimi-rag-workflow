package io.github.aimi.rag.workflow.generation.memory;

import io.github.aimi.rag.workflow.generation.history.ChatCompressionUsageRecord;
import io.github.aimi.rag.workflow.generation.history.ChatCompressionUsageRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompressingChatMemoryTest {

    private static Message msg(String text) {
        return new UserMessage(text);
    }

    private static ChatModel modelReturning(String summary) {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(new AssistantMessage(summary));
        when(response.getResult()).thenReturn(generation);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response));
        return chatModel;
    }

    private static CompressingChatMemory memory(ChatModel chatModel, int maxTokens, int keepRecent) {
        return CompressingChatMemory.builder()
                .repository(new InMemoryChatMemoryRepository())
                .chatModel(chatModel)
                .tokenCountEstimator(TokenCountEstimator.charsPerToken(1))
                .maxTokens(maxTokens)
                .keepRecentMessages(keepRecent)
                .build();
    }

    @Test
    void shouldCompressWhenOverThreshold() {
        ChatModel chatModel = modelReturning("摘要");
        CompressingChatMemory memory = memory(chatModel, 3, 1);
        memory.add("conv", List.of(msg("一二三"), msg("四五六"), msg("七八九"), msg("最近")));

        List<Message> result = memory.get("conv");

        assertEquals(2, result.size());
        assertInstanceOf(SystemMessage.class, result.get(0));
        assertEquals("摘要", result.get(0).getText());
        assertEquals("最近", result.get(1).getText());
    }

    @Test
    void shouldReplaceOldMessagesWithSummaryInRepository() {
        ChatModel chatModel = modelReturning("摘要");
        InMemoryChatMemoryRepository repository = new InMemoryChatMemoryRepository();
        CompressingChatMemory memory = CompressingChatMemory.builder()
                .repository(repository)
                .chatModel(chatModel)
                .tokenCountEstimator(TokenCountEstimator.charsPerToken(1))
                .maxTokens(5)
                .keepRecentMessages(1)
                .build();
        memory.add("conv", List.of(msg("一二三"), msg("四五六"), msg("七八九"), msg("最近")));

        memory.get("conv");

        List<Message> stored = repository.findByConversationId("conv");
        assertEquals(2, stored.size());
        assertInstanceOf(SystemMessage.class, stored.get(0));
        assertEquals("摘要", stored.get(0).getText());
        assertEquals("最近", stored.get(1).getText());
    }

    @Test
    void shouldRecordCompressionUsage() {
        ChatModel chatModel = modelReturning("摘要");
        ChatCompressionUsageRepository usageRepository = mock(ChatCompressionUsageRepository.class);
        CompressingChatMemory memory = CompressingChatMemory.builder()
                .repository(new InMemoryChatMemoryRepository())
                .chatModel(chatModel)
                .tokenCountEstimator(TokenCountEstimator.charsPerToken(1))
                .maxTokens(5)
                .keepRecentMessages(1)
                .usageRepository(usageRepository)
                .build();
        memory.add("conv", List.of(msg("一二三"), msg("四五六"), msg("七八九"), msg("最近")));

        memory.get("conv");

        ArgumentCaptor<ChatCompressionUsageRecord> captor = ArgumentCaptor.forClass(ChatCompressionUsageRecord.class);
        verify(usageRepository).saveUsage(captor.capture());
        assertEquals("SUCCESS", captor.getValue().status());
        assertEquals(Integer.valueOf(3), captor.getValue().compressedMessageCount());
    }

    @Test
    void shouldReturnFullMessagesWhenUnderThreshold() {
        ChatModel chatModel = modelReturning("不应调用");
        CompressingChatMemory memory = memory(chatModel, 100, 1);
        memory.add("conv", List.of(msg("a"), msg("b")));

        List<Message> result = memory.get("conv");

        assertEquals(2, result.size());
        verify(chatModel, never()).stream(any(Prompt.class));
    }

    @Test
    void shouldNotRecompressWhenCompressedContentFitsUnderThreshold() {
        ChatModel chatModel = modelReturning("摘要");
        CompressingChatMemory memory = memory(chatModel, 5, 1);
        memory.add("conv", List.of(msg("一二三"), msg("四五六"), msg("七八九"), msg("最近")));

        memory.get("conv");
        memory.get("conv");

        verify(chatModel, times(1)).stream(any(Prompt.class));
    }

    @Test
    void shouldInvalidateCacheOnAdd() {
        ChatModel chatModel = modelReturning("摘要");
        CompressingChatMemory memory = memory(chatModel, 3, 1);
        memory.add("conv", List.of(msg("一二三"), msg("四五六"), msg("七八九"), msg("最近")));

        memory.get("conv");
        memory.add("conv", List.of(msg("新消息")));
        memory.get("conv");

        verify(chatModel, times(2)).stream(any(Prompt.class));
    }

    @Test
    void shouldDegradeToWindowWhenCompressionFails() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("boom")));
        CompressingChatMemory memory = memory(chatModel, 3, 1);
        memory.add("conv", List.of(msg("一二三"), msg("四五六"), msg("七八九"), msg("最近")));

        List<Message> result = memory.get("conv");

        assertEquals(1, result.size());
        assertEquals("最近", result.get(0).getText());
    }

    @Test
    void shouldClearRepositoryAndCache() {
        ChatModel chatModel = modelReturning("摘要");
        CompressingChatMemory memory = memory(chatModel, 3, 1);
        memory.add("conv", List.of(msg("一二三"), msg("四五六"), msg("七八九"), msg("最近")));
        memory.get("conv");

        memory.clear("conv");

        assertEquals(List.of(), memory.get("conv"));
    }

    @Test
    void shouldUseCustomSummaryPrompt() {
        ChatModel chatModel = modelReturning("摘要");
        String customPrompt = "请用中文总结以下对话：\n";
        CompressingChatMemory memory = CompressingChatMemory.builder()
                .repository(new InMemoryChatMemoryRepository())
                .chatModel(chatModel)
                .tokenCountEstimator(TokenCountEstimator.charsPerToken(1))
                .maxTokens(3)
                .keepRecentMessages(1)
                .summaryPrompt(customPrompt)
                .build();
        memory.add("conv", List.of(msg("一二三"), msg("四五六"), msg("七八九"), msg("最近")));

        memory.get("conv");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(captor.capture());
        assertTrue(captor.getValue().getInstructions().get(0).getText().startsWith(customPrompt));
    }
}
