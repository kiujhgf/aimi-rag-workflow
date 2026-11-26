package io.github.aimi.rag.workflow.generation.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;

/**
 * Creates the {@code chat_message} and {@code chat_turn_usage} tables (and their
 * indexes) on startup, using {@link JdbcTemplate}. Statements are idempotent and
 * failures are logged rather than propagated, so schema creation never blocks the
 * main flow.
 */
public class ChatHistorySchemaInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatHistorySchemaInitializer.class);

    private static final List<String> STATEMENTS = List.of(
            """
            CREATE TABLE IF NOT EXISTS chat_message (
                id VARCHAR(64) PRIMARY KEY,
                conversation_id VARCHAR(128) NOT NULL,
                turn_sequence INT NOT NULL,
                message_index INT NOT NULL,
                message_role VARCHAR(16) NOT NULL,
                content TEXT,
                created_time TIMESTAMP NOT NULL
            )
            """,
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_message_conv_turn_idx "
                    + "ON chat_message (conversation_id, turn_sequence, message_index)",
            "CREATE INDEX IF NOT EXISTS idx_chat_message_conversation "
                    + "ON chat_message (conversation_id)",
            """
            CREATE TABLE IF NOT EXISTS chat_turn_usage (
                id VARCHAR(64) PRIMARY KEY,
                conversation_id VARCHAR(128) NOT NULL,
                turn_sequence INT NOT NULL,
                model VARCHAR(128),
                execution_id VARCHAR(64),
                status VARCHAR(32) NOT NULL,
                error_message TEXT,
                prompt_tokens INT,
                completion_tokens INT,
                total_tokens INT,
                start_time TIMESTAMP NOT NULL,
                end_time TIMESTAMP
            )
            """,
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_turn_usage_conv_turn "
                    + "ON chat_turn_usage (conversation_id, turn_sequence)",
            "CREATE INDEX IF NOT EXISTS idx_chat_turn_usage_execution "
                    + "ON chat_turn_usage (execution_id)",
            """
            CREATE TABLE IF NOT EXISTS chat_compression_usage (
                id VARCHAR(64) PRIMARY KEY,
                conversation_id VARCHAR(128) NOT NULL,
                status VARCHAR(32) NOT NULL,
                error_message TEXT,
                compressed_message_count INT,
                prompt_tokens INT,
                completion_tokens INT,
                total_tokens INT,
                start_time TIMESTAMP NOT NULL,
                end_time TIMESTAMP
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_chat_compression_usage_conversation "
                    + "ON chat_compression_usage (conversation_id)");

    private final JdbcTemplate jdbcTemplate;

    public ChatHistorySchemaInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(
                Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    public void initialize() {
        for (String statement : STATEMENTS) {
            try {
                jdbcTemplate.execute(statement.strip());
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to execute chat history schema statement; continuing: {}", statement, e);
            }
        }
    }
}
