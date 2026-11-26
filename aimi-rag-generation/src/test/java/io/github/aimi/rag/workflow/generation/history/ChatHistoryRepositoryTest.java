package io.github.aimi.rag.workflow.generation.history;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatHistoryRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private ChatMessageRepository messageRepository;
    private ChatTurnUsageRepository usageRepository;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        new ChatHistorySchemaInitializer(dataSource).initialize();
        messageRepository = new ChatMessageRepository(jdbcTemplate);
        usageRepository = new ChatTurnUsageRepository(jdbcTemplate);
    }

    @Test
    void shouldSaveTwoMessagesAndOneUsageRowPerTurn() {
        int turn = usageRepository.nextTurnSequence("conv-1");
        assertEquals(1, turn);

        messageRepository.saveMessages("conv-1", turn, "你好", "你好，有什么可以帮你？");
        usageRepository.saveUsage(usage("conv-1", turn, "SUCCESS", 10, 20, 30));

        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_message WHERE conversation_id = 'conv-1'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_turn_usage WHERE conversation_id = 'conv-1'", Integer.class));
        assertEquals(30, jdbcTemplate.queryForObject(
                "SELECT total_tokens FROM chat_turn_usage WHERE conversation_id = 'conv-1'", Integer.class));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT error_message FROM chat_turn_usage WHERE conversation_id = 'conv-1'", String.class));
    }

    @Test
    void shouldBeIdempotentOnRepeatedSave() {
        int turn = usageRepository.nextTurnSequence("conv-1");

        messageRepository.saveMessages("conv-1", turn, "第一版", "回答一");
        messageRepository.saveMessages("conv-1", turn, "第二版", "回答二");

        List<ChatMessageRecord> messages = messageRepository.findByConversationId("conv-1");
        assertEquals(2, messages.size());
        assertEquals("第二版", messages.get(0).content());
        assertEquals("回答二", messages.get(1).content());

        usageRepository.saveUsage(usage("conv-1", turn, "SUCCESS", 1, 2, 3));
        usageRepository.saveUsage(usage("conv-1", turn, "SUCCESS", 4, 5, 9));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_turn_usage WHERE conversation_id = 'conv-1'", Integer.class));
        assertEquals(9, jdbcTemplate.queryForObject(
                "SELECT total_tokens FROM chat_turn_usage WHERE conversation_id = 'conv-1'", Integer.class));
    }

    @Test
    void shouldQueryFullHistoryAscendingByTurnAndIndex() {
        messageRepository.saveMessages("conv-1", 1, "问题1", "回答1");
        messageRepository.saveMessages("conv-1", 2, "问题2", "回答2");

        List<ChatMessageRecord> messages = messageRepository.findByConversationId("conv-1");
        assertEquals(4, messages.size());
        assertEquals(0, messages.get(0).messageIndex());
        assertEquals("问题1", messages.get(0).content());
        assertEquals(1, messages.get(1).messageIndex());
        assertEquals("回答1", messages.get(1).content());
        assertEquals("问题2", messages.get(2).content());
        assertEquals("回答2", messages.get(3).content());
    }

    @Test
    void shouldMarkErrorAndCancelled() {
        int turn = usageRepository.nextTurnSequence("conv-1");
        usageRepository.saveUsage(usage("conv-1", turn, "SUCCESS", 1, 2, 3));

        usageRepository.markError("conv-1", turn, "boom");
        assertEquals("ERROR", status("conv-1"));
        assertEquals("boom", jdbcTemplate.queryForObject(
                "SELECT error_message FROM chat_turn_usage WHERE conversation_id = 'conv-1'", String.class));
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT end_time FROM chat_turn_usage WHERE conversation_id = 'conv-1'", Instant.class));

        usageRepository.markCancelled("conv-1", turn);
        assertEquals("CANCELLED", status("conv-1"));
    }

    @Test
    void shouldAllocateMonotonicTurnSequence() {
        assertEquals(1, usageRepository.nextTurnSequence("conv-1"));
        usageRepository.saveUsage(usage("conv-1", 1, "SUCCESS", 0, 0, 0));
        assertEquals(2, usageRepository.nextTurnSequence("conv-1"));

        // sequence is scoped per conversation
        assertEquals(1, usageRepository.nextTurnSequence("conv-2"));
    }

    private String status(String conversationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM chat_turn_usage WHERE conversation_id = ?", String.class, conversationId);
    }

    private static ChatTurnUsageRecord usage(String conversationId, int turn, String status,
                                             int prompt, int completion, int total) {
        Instant start = Instant.parse("2026-11-22T08:00:00Z");
        return new ChatTurnUsageRecord(conversationId, turn, "gpt-4o-mini", "exec-1", status, null,
                prompt, completion, total, start, start.plusSeconds(1));
    }
}
