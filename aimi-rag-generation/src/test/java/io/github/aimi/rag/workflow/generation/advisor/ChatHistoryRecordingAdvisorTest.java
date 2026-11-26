package io.github.aimi.rag.workflow.generation.advisor;

import io.github.aimi.rag.workflow.generation.history.ChatMessageRepository;
import io.github.aimi.rag.workflow.generation.history.ChatTurnUsageRecord;
import io.github.aimi.rag.workflow.generation.history.ChatTurnUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatHistoryRecordingAdvisorTest {

    private ChatMessageRepository messageRepository;
    private ChatTurnUsageRepository usageRepository;
    private ChatHistoryRecordingAdvisor advisor;

    @BeforeEach
    void setUp() {
        messageRepository = mock(ChatMessageRepository.class);
        usageRepository = mock(ChatTurnUsageRepository.class);
        when(usageRepository.nextTurnSequence(anyString())).thenReturn(1);
        advisor = ChatHistoryRecordingAdvisor.builder(messageRepository, usageRepository).build();
    }

    private static ChatClientRequest request(String userContent) {
        Prompt prompt = new Prompt(new UserMessage(userContent));
        return new ChatClientRequest(prompt, Map.of(ChatMemory.CONVERSATION_ID, "conv-1"));
    }

    private static ChatClientResponse response(String text, Usage usage) {
        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(new AssistantMessage(text));
        when(chatResponse.getResult()).thenReturn(generation);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        doReturn(usage).when(metadata).getUsage();
        when(chatResponse.getMetadata()).thenReturn(metadata);
        return new ChatClientResponse(chatResponse, Map.of());
    }

    private static Usage usage(int prompt, int completion, int total) {
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(prompt);
        when(usage.getCompletionTokens()).thenReturn(completion);
        when(usage.getTotalTokens()).thenReturn(total);
        return usage;
    }

    @Test
    void shouldRecordCallPathWithTokens() {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        ChatClientResponse expected = response("回答", usage(10, 20, 30));
        when(chain.nextCall(any())).thenReturn(expected);

        ChatClientResponse result = advisor.adviseCall(request("问题"), chain);

        assertSame(expected, result);
        verify(messageRepository).saveMessages("conv-1", 1, "问题", "回答");

        ArgumentCaptor<ChatTurnUsageRecord> captor = ArgumentCaptor.forClass(ChatTurnUsageRecord.class);
        verify(usageRepository).saveUsage(captor.capture());
        assertEquals(ChatTurnUsageRepository.STATUS_SUCCESS, captor.getValue().status());
        assertEquals(10, captor.getValue().promptTokens());
        assertEquals(20, captor.getValue().completionTokens());
        assertEquals(30, captor.getValue().totalTokens());
    }

    @Test
    void shouldRecordCallPathWithoutUsageMetadata() {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        ChatClientResponse expected = response("回答", null);
        when(chain.nextCall(any())).thenReturn(expected);

        advisor.adviseCall(request("问题"), chain);

        ArgumentCaptor<ChatTurnUsageRecord> captor = ArgumentCaptor.forClass(ChatTurnUsageRecord.class);
        verify(usageRepository).saveUsage(captor.capture());
        assertEquals(ChatTurnUsageRepository.STATUS_SUCCESS, captor.getValue().status());
        assertNull(captor.getValue().totalTokens());
    }

    @Test
    void shouldRecordCallPathError() {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenThrow(new RuntimeException("boom"));

        try {
            advisor.adviseCall(request("问题"), chain);
        } catch (RuntimeException ignored) {
            // expected
        }

        ArgumentCaptor<ChatTurnUsageRecord> captor = ArgumentCaptor.forClass(ChatTurnUsageRecord.class);
        verify(usageRepository).saveUsage(captor.capture());
        assertEquals(ChatTurnUsageRepository.STATUS_ERROR, captor.getValue().status());
        assertEquals("boom", captor.getValue().errorMessage());
    }

    @Test
    void shouldRecordStreamCompletionAndPassThroughChunks() {
        ChatClientResponse first = response("你", null);
        ChatClientResponse second = response("好", usage(10, 20, 30));
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(any())).thenReturn(Flux.just(first, second));

        List<ChatClientResponse> collected = advisor.adviseStream(request("问题"), chain)
                .collectList()
                .block();

        assertEquals(2, collected.size());
        assertSame(first, collected.get(0));
        assertSame(second, collected.get(1));

        verify(messageRepository).saveMessages("conv-1", 1, "问题", "你好");
        ArgumentCaptor<ChatTurnUsageRecord> captor = ArgumentCaptor.forClass(ChatTurnUsageRecord.class);
        verify(usageRepository).saveUsage(captor.capture());
        assertEquals(ChatTurnUsageRepository.STATUS_SUCCESS, captor.getValue().status());
        assertEquals(30, captor.getValue().totalTokens());
    }

    @Test
    void shouldRecordStreamError() {
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(any())).thenReturn(Flux.error(new RuntimeException("boom")));

        advisor.adviseStream(request("问题"), chain)
                .onErrorResume(e -> Flux.empty())
                .blockLast();

        ArgumentCaptor<ChatTurnUsageRecord> captor = ArgumentCaptor.forClass(ChatTurnUsageRecord.class);
        verify(usageRepository).saveUsage(captor.capture());
        assertEquals(ChatTurnUsageRepository.STATUS_ERROR, captor.getValue().status());
        assertEquals("boom", captor.getValue().errorMessage());
    }

    @Test
    void shouldRecordStreamCancellation() {
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(any())).thenReturn(Flux.never());

        Disposable subscription = advisor.adviseStream(request("问题"), chain).subscribe();
        subscription.dispose();

        ArgumentCaptor<ChatTurnUsageRecord> captor = ArgumentCaptor.forClass(ChatTurnUsageRecord.class);
        verify(usageRepository).saveUsage(captor.capture());
        assertEquals(ChatTurnUsageRepository.STATUS_CANCELLED, captor.getValue().status());
    }
}
