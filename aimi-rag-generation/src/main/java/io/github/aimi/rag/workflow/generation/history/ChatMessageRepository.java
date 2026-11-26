package io.github.aimi.rag.workflow.generation.history;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Persists full chat history into {@code chat_message}, one row per message,
 * using plain {@link JdbcTemplate} (no JPA/MyBatis).
 *
 * <p>Saving is idempotent: a re-save of the same
 * {@code (conversation_id, turn_sequence, message_index)} updates the existing
 * row instead of failing.</p>
 */
public class ChatMessageRepository {

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private static final String INSERT_SQL = """
            INSERT INTO chat_message (id, conversation_id, turn_sequence, message_index, message_role, content, created_time)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.strip();

    private static final String UPDATE_SQL = "UPDATE chat_message SET content = ?, created_time = ? "
            + "WHERE conversation_id = ? AND turn_sequence = ? AND message_index = ?";

    private static final String SELECT_SQL = """
            SELECT conversation_id, turn_sequence, message_index, message_role, content, created_time
            FROM chat_message
            WHERE conversation_id = ?
            ORDER BY turn_sequence ASC, message_index ASC
            """.strip();

    private final JdbcTemplate jdbcTemplate;

    public ChatMessageRepository(DataSource dataSource) {
        this(new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null")));
    }

    public ChatMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * Saves the user (index 0) and, when present, the assistant (index 1) row for a turn.
     *
     * @param assistantContent may be {@code null} when only the user message is known yet
     */
    public void saveMessages(String conversationId, int turnSequence,
                             String userContent, String assistantContent) {
        saveMessage(conversationId, turnSequence, 0, ROLE_USER, userContent);
        if (assistantContent != null) {
            saveMessage(conversationId, turnSequence, 1, ROLE_ASSISTANT, assistantContent);
        }
    }

    public List<ChatMessageRecord> findByConversationId(String conversationId) {
        return jdbcTemplate.query(SELECT_SQL, this::mapRow, conversationId);
    }

    private void saveMessage(String conversationId, int turnSequence, int messageIndex,
                             String role, String content) {
        if (content == null) {
            return;
        }
        Instant now = Instant.now();
        try {
            jdbcTemplate.update(INSERT_SQL, UUID.randomUUID().toString(), conversationId, turnSequence,
                    messageIndex, role, content, now);
        } catch (DuplicateKeyException e) {
            jdbcTemplate.update(UPDATE_SQL, content, now, conversationId, turnSequence, messageIndex);
        }
    }

    private ChatMessageRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new ChatMessageRecord(
                resultSet.getString("conversation_id"),
                resultSet.getInt("turn_sequence"),
                resultSet.getInt("message_index"),
                resultSet.getString("message_role"),
                resultSet.getString("content"),
                resultSet.getObject("created_time", Instant.class));
    }
}
