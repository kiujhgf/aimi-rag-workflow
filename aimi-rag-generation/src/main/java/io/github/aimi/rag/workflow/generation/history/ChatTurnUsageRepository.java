package io.github.aimi.rag.workflow.generation.history;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persists per-turn usage (token counts, status, timing) into
 * {@code chat_turn_usage}, one row per LLM call, using plain {@link JdbcTemplate}.
 *
 * <p>{@code turn_sequence} is allocated with {@link #nextTurnSequence(String)}
 * ({@code MAX(turn_sequence)+1}); saving is idempotent, so a re-save of the same
 * {@code (conversation_id, turn_sequence)} updates the existing row.</p>
 */
public class ChatTurnUsageRepository {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_ERROR = "ERROR";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private static final String INSERT_SQL = """
            INSERT INTO chat_turn_usage (id, conversation_id, turn_sequence, model, execution_id, status,
                error_message, prompt_tokens, completion_tokens, total_tokens, start_time, end_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.strip();

    private static final String UPDATE_SQL = "UPDATE chat_turn_usage SET model = ?, execution_id = ?, "
            + "status = ?, error_message = ?, prompt_tokens = ?, completion_tokens = ?, total_tokens = ?, "
            + "start_time = ?, end_time = ? WHERE conversation_id = ? AND turn_sequence = ?";

    private static final String UPDATE_STATUS_SQL = "UPDATE chat_turn_usage SET status = ?, error_message = ?, "
            + "end_time = ? WHERE conversation_id = ? AND turn_sequence = ?";

    private static final String MAX_SEQUENCE_SQL = "SELECT COALESCE(MAX(turn_sequence), 0) "
            + "FROM chat_turn_usage WHERE conversation_id = ?";

    private final JdbcTemplate jdbcTemplate;

    public ChatTurnUsageRepository(DataSource dataSource) {
        this(new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null")));
    }

    public ChatTurnUsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * Computes the next turn sequence for a conversation ({@code MAX(turn_sequence)+1}).
     */
    public int nextTurnSequence(String conversationId) {
        Integer max = jdbcTemplate.queryForObject(MAX_SEQUENCE_SQL, Integer.class, conversationId);
        return (max == null ? 0 : max) + 1;
    }

    public void saveUsage(ChatTurnUsageRecord record) {
        try {
            jdbcTemplate.update(INSERT_SQL,
                    UUID.randomUUID().toString(), record.conversationId(), record.turnSequence(),
                    record.model(), record.executionId(), record.status(), record.errorMessage(),
                    record.promptTokens(), record.completionTokens(), record.totalTokens(),
                    record.startTime(), record.endTime());
        } catch (DuplicateKeyException e) {
            jdbcTemplate.update(UPDATE_SQL,
                    record.model(), record.executionId(), record.status(), record.errorMessage(),
                    record.promptTokens(), record.completionTokens(), record.totalTokens(),
                    record.startTime(), record.endTime(),
                    record.conversationId(), record.turnSequence());
        }
    }

    public void markError(String conversationId, int turnSequence, String message) {
        jdbcTemplate.update(UPDATE_STATUS_SQL,
                STATUS_ERROR, message, Instant.now(), conversationId, turnSequence);
    }

    public void markCancelled(String conversationId, int turnSequence) {
        jdbcTemplate.update(UPDATE_STATUS_SQL,
                STATUS_CANCELLED, null, Instant.now(), conversationId, turnSequence);
    }
}
