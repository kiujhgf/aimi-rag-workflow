package io.github.aimi.rag.workflow.log.repository;

import io.github.aimi.rag.workflow.core.flow.StepExecutionRecord;
import io.github.aimi.rag.workflow.core.model.StepStatus;
import io.github.aimi.rag.workflow.log.dialect.JdbcLogRepositoryDialect;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class StepExecutionJpaRepository {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcLogRepositoryDialect dialect;

    public StepExecutionJpaRepository(JdbcTemplate jdbcTemplate, JdbcLogRepositoryDialect dialect) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect must not be null");
    }

    public void insert(String executionId, StepExecutionRecord record) {
        jdbcTemplate.update(dialect.getInsertStepExecutionSql(),
                UUID.randomUUID().toString(), executionId, record.getStepName(),
                record.getStatus().name(), record.getStartTime(), record.getEndTime());
    }

    public List<StepExecutionRecord> findByExecutionIdOrderByStartTime(String executionId) {
        return jdbcTemplate.query(dialect.getSelectStepExecutionsSql(), this::mapRecord, executionId);
    }

    private StepExecutionRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return StepExecutionRecord.builder()
                .stepName(resultSet.getString("step_name"))
                .status(StepStatus.valueOf(resultSet.getString("status")))
                .startTime(resultSet.getObject("start_time", Instant.class))
                .endTime(resultSet.getObject("end_time", Instant.class))
                .build();
    }
}
