package io.github.aimi.rag.workflow.log.repository;

import io.github.aimi.rag.workflow.core.flow.FlowExecutionRepository;
import io.github.aimi.rag.workflow.core.flow.StepExecutionRecord;
import io.github.aimi.rag.workflow.core.flow.StepFailureRecord;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.FlowContextKeys;
import io.github.aimi.rag.workflow.log.InputContextSerializer;
import io.github.aimi.rag.workflow.log.dialect.JdbcLogRepositoryDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class FlowExecutionJpaRepository implements FlowExecutionRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowExecutionJpaRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final JdbcLogRepositoryDialect dialect;
    private final InputContextSerializer inputContextSerializer;
    private final StepExecutionJpaRepository stepExecutionRepository;
    private final StepFailureJpaRepository stepFailureRepository;

    public FlowExecutionJpaRepository(DataSource dataSource) {
        this(new JdbcTemplate(dataSource), JdbcLogRepositoryDialect.from(dataSource),
                InputContextSerializer.none());
    }

    public FlowExecutionJpaRepository(DataSource dataSource, JdbcLogRepositoryDialect dialect) {
        this(new JdbcTemplate(dataSource), JdbcLogRepositoryDialect.resolve(dataSource, dialect),
                InputContextSerializer.none());
    }

    public FlowExecutionJpaRepository(DataSource dataSource, InputContextSerializer serializer) {
        this(new JdbcTemplate(dataSource), JdbcLogRepositoryDialect.from(dataSource), serializer);
    }

    public FlowExecutionJpaRepository(JdbcTemplate jdbcTemplate, JdbcLogRepositoryDialect dialect,
                                      InputContextSerializer inputContextSerializer) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect must not be null");
        this.inputContextSerializer = Objects.requireNonNull(
                inputContextSerializer, "inputContextSerializer must not be null");
        this.stepExecutionRepository = new StepExecutionJpaRepository(jdbcTemplate, dialect);
        this.stepFailureRepository = new StepFailureJpaRepository(jdbcTemplate, dialect);
    }

    @Override
    public void createExecution(String flowName, FlowContext context) {
        String executionId = context.get(FlowContextKeys.EXECUTION_ID, String.class);
        jdbcTemplate.update(dialect.getInsertFlowExecutionSql(), executionId, flowName,
                "RUNNING", Instant.now(), serializeInputContext(context));
    }

    @Override
    public void saveStepExecution(FlowContext context, StepExecutionRecord record) {
        stepExecutionRepository.insert(executionId(context), record);
    }

    @Override
    public void saveStepFailure(FlowContext context, StepFailureRecord record) {
        stepFailureRepository.insert(executionId(context), record);
    }

    @Override
    public void updateExecutionStatus(FlowContext context, String status) {
        jdbcTemplate.update(dialect.getUpdateFlowExecutionStatusSql(),
                status, Instant.now(), executionId(context));
    }

    @Override
    public List<StepExecutionRecord> getExecutionHistory(String executionId) {
        return stepExecutionRepository.findByExecutionIdOrderByStartTime(executionId);
    }

    private String executionId(FlowContext context) {
        return context.get(FlowContextKeys.EXECUTION_ID, String.class);
    }

    private String serializeInputContext(FlowContext context) {
        try {
            return inputContextSerializer.serialize(context);
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to serialize input context; storing null instead", e);
            return null;
        }
    }
}
