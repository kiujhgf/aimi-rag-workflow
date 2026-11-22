package io.github.aimi.rag.workflow.log.repository;

import io.github.aimi.rag.workflow.core.flow.StepFailureRecord;
import io.github.aimi.rag.workflow.log.dialect.JdbcLogRepositoryDialect;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;
import java.util.UUID;

public class StepFailureJpaRepository {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcLogRepositoryDialect dialect;

    public StepFailureJpaRepository(JdbcTemplate jdbcTemplate, JdbcLogRepositoryDialect dialect) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect must not be null");
    }

    public void insert(String executionId, StepFailureRecord record) {
        jdbcTemplate.update(dialect.getInsertStepFailureSql(),
                UUID.randomUUID().toString(), executionId, record.getStepName(),
                record.getErrorMessage(), record.getErrorStack(), record.getTimestamp());
    }
}
