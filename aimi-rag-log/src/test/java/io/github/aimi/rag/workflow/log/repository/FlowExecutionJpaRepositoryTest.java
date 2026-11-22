package io.github.aimi.rag.workflow.log.repository;

import io.github.aimi.rag.workflow.core.flow.StepExecutionRecord;
import io.github.aimi.rag.workflow.core.flow.StepFailureRecord;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.FlowContextKeys;
import io.github.aimi.rag.workflow.core.model.StepStatus;
import io.github.aimi.rag.workflow.log.InputContextSerializer;
import io.github.aimi.rag.workflow.log.LogSchemaInitializer;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FlowExecutionJpaRepositoryTest {

    private JdbcDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private FlowExecutionJpaRepository repository;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        new LogSchemaInitializer(dataSource).initialize();
        repository = new FlowExecutionJpaRepository(dataSource);
    }

    @Test
    void shouldPersistAndReadAllExecutionData() {
        FlowContext context = context("exec-1");
        context.set("question", "not serialized by default");
        context.set("unserializable", new Object());

        repository.createExecution("orders", context);

        assertEquals("RUNNING", queryString("SELECT status FROM flow_execution WHERE id = 'exec-1'"));
        assertEquals("orders", queryString("SELECT flow_name FROM flow_execution WHERE id = 'exec-1'"));
        assertNull(queryString("SELECT input_context FROM flow_execution WHERE id = 'exec-1'"));
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT start_time FROM flow_execution WHERE id = 'exec-1'", Instant.class));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT end_time FROM flow_execution WHERE id = 'exec-1'", Instant.class));

        Instant later = Instant.parse("2026-11-22T08:00:02.123456Z");
        Instant earlier = Instant.parse("2026-11-22T08:00:01.123456Z");
        repository.saveStepExecution(context, step("second", later));
        repository.saveStepExecution(context, step("first", earlier));

        List<StepExecutionRecord> history = repository.getExecutionHistory("exec-1");
        assertEquals(2, history.size());
        assertEquals("first", history.get(0).getStepName());
        assertEquals("second", history.get(1).getStepName());
        assertEquals(earlier, history.get(0).getStartTime());
        assertEquals(earlier.plusSeconds(1), history.get(0).getEndTime());
        assertEquals(StepStatus.FINISHED, history.get(0).getStatus());

        Instant failureTime = Instant.parse("2026-11-22T08:00:03.123456Z");
        repository.saveStepFailure(context, StepFailureRecord.builder()
                .stepName("first")
                .errorMessage("boom")
                .errorStack("stack trace")
                .timestamp(failureTime)
                .build());
        assertEquals("boom", queryString(
                "SELECT error_message FROM step_failure WHERE execution_id = 'exec-1'"));
        assertEquals(failureTime, jdbcTemplate.queryForObject(
                "SELECT timestamp FROM step_failure WHERE execution_id = 'exec-1'", Instant.class));

        repository.updateExecutionStatus(context, "COMPLETED");
        assertEquals("COMPLETED", queryString("SELECT status FROM flow_execution WHERE id = 'exec-1'"));
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT end_time FROM flow_execution WHERE id = 'exec-1'", Instant.class));
    }

    @Test
    void shouldStoreNullWhenCustomSerializerFails() {
        FlowExecutionJpaRepository failingSerializerRepository = new FlowExecutionJpaRepository(
                dataSource, (InputContextSerializer) context -> {
                    throw new IllegalStateException("serialization failed");
                });

        assertDoesNotThrow(() -> failingSerializerRepository.createExecution("orders", context("exec-2")));

        assertEquals("RUNNING", queryString("SELECT status FROM flow_execution WHERE id = 'exec-2'"));
        assertNull(queryString("SELECT input_context FROM flow_execution WHERE id = 'exec-2'"));
    }

    private static FlowContext context(String executionId) {
        FlowContext context = FlowContext.create();
        context.set(FlowContextKeys.EXECUTION_ID, executionId);
        return context;
    }

    private static StepExecutionRecord step(String name, Instant startTime) {
        return StepExecutionRecord.builder()
                .stepName(name)
                .status(StepStatus.FINISHED)
                .startTime(startTime)
                .endTime(startTime.plusSeconds(1))
                .build();
    }

    private String queryString(String sql) {
        return jdbcTemplate.queryForObject(sql, String.class);
    }
}
