package io.github.aimi.rag.workflow.generation.history;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.UUID;

/**
 * Persists compression usage (token counts, status, timing) into
 * {@code chat_compression_usage}, one row per compression LLM call, using plain
 * {@link JdbcTemplate}.
 */
public class ChatCompressionUsageRepository {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_ERROR = "ERROR";

    private static final String INSERT_SQL = """
            INSERT INTO chat_compression_usage (id, conversation_id, status, error_message,
                compressed_message_count, prompt_tokens, completion_tokens, total_tokens, start_time, end_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.strip();

    private final JdbcTemplate jdbcTemplate;

    public ChatCompressionUsageRepository(DataSource dataSource) {
        this(new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null")));
    }

    public ChatCompressionUsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public void saveUsage(ChatCompressionUsageRecord record) {
        jdbcTemplate.update(INSERT_SQL,
                UUID.randomUUID().toString(),
                record.conversationId(),
                record.status(),
                record.errorMessage(),
                record.compressedMessageCount(),
                record.promptTokens(),
                record.completionTokens(),
                record.totalTokens(),
                record.startTime(),
                record.endTime());
    }
}
