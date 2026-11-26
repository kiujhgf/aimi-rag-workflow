package io.github.aimi.rag.workflow.generation.step;

import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.generation.advisor.ChatHistoryRecordingAdvisor;
import io.github.aimi.rag.workflow.generation.history.ChatHistorySchemaInitializer;
import io.github.aimi.rag.workflow.generation.history.ChatMessageRepository;
import io.github.aimi.rag.workflow.generation.history.ChatTurnUsageRepository;
import io.github.aimi.rag.workflow.generation.memory.ChatMemoryMode;
import io.github.aimi.rag.workflow.generation.memory.CompressingChatMemory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatGenerationStepMemoryTest {

    private static ChatModel modelReturning(String answer) {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(new AssistantMessage(answer));
        when(response.getResult()).thenReturn(generation);
        when(response.getResults()).thenReturn(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
        return chatModel;
    }

    private static FlowContext turn(String conversationId, String question) {
        FlowContext context = FlowContext.create().set("question", question);
        if (conversationId != null) {
            context.set("conversation_id", conversationId);
        }
        return context;
    }

    @Test
    void shouldIncludeHistoryOnSecondTurnWithSameConversationId() throws Exception {
        ChatModel chatModel = modelReturning("答案");
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel))
                .userPrompt("{question}")
                .build();

        step.process(turn("conv-A", "问题1"));
        step.process(turn("conv-A", "问题2"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(captor.capture());
        List<Message> second = captor.getAllValues().get(1).getInstructions();
        assertEquals(3, second.size());
        assertEquals("问题1", second.get(0).getText());
        assertEquals("答案", second.get(1).getText());
        assertEquals("问题2", second.get(2).getText());
    }

    @Test
    void shouldIsolateDifferentConversationIds() throws Exception {
        ChatModel chatModel = modelReturning("答案");
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(chatModel))
                .userPrompt("{question}")
                .build();

        step.process(turn("conv-A", "问题A"));
        step.process(turn("conv-B", "问题B"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(captor.capture());
        assertEquals(1, captor.getAllValues().get(1).getInstructions().size());
    }

    @Test
    void shouldUseWindowMemoryByDefault() {
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(mock(ChatModel.class)))
                .userPrompt("{question}")
                .build();

        assertInstanceOf(MessageWindowChatMemory.class, step.getChatMemory());
    }

    @Test
    void shouldUseCompressingMemoryInCompressingMode() {
        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(mock(ChatModel.class)))
                .userPrompt("{question}")
                .memoryMode(ChatMemoryMode.COMPRESSING)
                .summaryChatModel(mock(ChatModel.class))
                .maxTokens(1000)
                .keepRecentMessages(5)
                .build();

        assertInstanceOf(CompressingChatMemory.class, step.getChatMemory());
    }

    @Test
    void shouldRequireSummaryModelForCompressing() {
        ChatGenerationStep.Builder builder = ChatGenerationStep.builder(ChatClient.create(mock(ChatModel.class)))
                .userPrompt("{question}")
                .memoryMode(ChatMemoryMode.COMPRESSING);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void shouldPersistFullHistoryInAllMode() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        new ChatHistorySchemaInitializer(dataSource).initialize();

        Advisor historyAdvisor = ChatHistoryRecordingAdvisor.builder(
                new ChatMessageRepository(dataSource), new ChatTurnUsageRepository(dataSource))
                .build();

        ChatGenerationStep step = ChatGenerationStep.builder(ChatClient.create(modelReturning("答案")))
                .userPrompt("{question}")
                .memoryMode(ChatMemoryMode.ALL)
                .historyAdvisor(historyAdvisor)
                .build();

        step.process(turn("conv-A", "问题"));

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_message WHERE conversation_id = 'conv-A'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_turn_usage WHERE conversation_id = 'conv-A'", Integer.class));
    }
}
