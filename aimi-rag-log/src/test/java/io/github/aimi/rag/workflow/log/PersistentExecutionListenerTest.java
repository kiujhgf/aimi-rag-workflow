package io.github.aimi.rag.workflow.log;

import io.github.aimi.rag.workflow.core.exception.FlowException;
import io.github.aimi.rag.workflow.core.flow.Flow;
import io.github.aimi.rag.workflow.core.flow.FlowBuilder;
import io.github.aimi.rag.workflow.core.model.FlowCategory;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.FlowContextKeys;
import io.github.aimi.rag.workflow.core.model.StepType;
import io.github.aimi.rag.workflow.core.step.AbstractStep;
import io.github.aimi.rag.workflow.log.repository.FlowExecutionJpaRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PersistentExecutionListenerTest {

    private JdbcDataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        new LogSchemaInitializer(dataSource).initialize();
    }

    @Test
    void shouldPersistSuccessfulFlowLifecycle() {
        Flow flow = flow(new TestStep("step1", false));
        flow.addListener(new PersistentExecutionListener(
                new FlowExecutionJpaRepository(dataSource), "test-flow"));
        FlowContext context = FlowContext.create();

        flow.execute(context);

        String executionId = context.get(FlowContextKeys.EXECUTION_ID, String.class);
        assertEquals("test-flow", jdbcTemplate.queryForObject(
                "SELECT flow_name FROM flow_execution WHERE id = ?", String.class, executionId));
        assertEquals("COMPLETED", jdbcTemplate.queryForObject(
                "SELECT status FROM flow_execution WHERE id = ?", String.class, executionId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM step_execution WHERE execution_id = ? AND step_name = 'step1'",
                Integer.class, executionId));
    }

    @Test
    void shouldPersistFailureLifecycleAndStackTrace() {
        Flow flow = flow(new TestStep("failing-step", true));
        flow.addListener(new PersistentExecutionListener(new FlowExecutionJpaRepository(dataSource)));
        FlowContext context = FlowContext.create();

        assertThrows(FlowException.class, () -> flow.execute(context));

        String executionId = context.get(FlowContextKeys.EXECUTION_ID, String.class);
        assertEquals("FAILED", jdbcTemplate.queryForObject(
                "SELECT status FROM flow_execution WHERE id = ?", String.class, executionId));
        String stack = jdbcTemplate.queryForObject(
                "SELECT error_stack FROM step_failure WHERE execution_id = ? AND step_name = 'failing-step'",
                String.class, executionId);
        assertTrue(stack.contains("boom"));
    }

    @Test
    void shouldSupportDataSourceConstructorWithoutImplicitDdl() {
        JdbcDataSource emptyDataSource = new JdbcDataSource();
        emptyDataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        new PersistentExecutionListener(emptyDataSource);
        JdbcTemplate emptyJdbcTemplate = new JdbcTemplate(emptyDataSource);
        assertEquals(0, emptyJdbcTemplate.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_NAME IN ('FLOW_EXECUTION', 'STEP_EXECUTION', 'STEP_FAILURE')", Integer.class));

        new LogSchemaInitializer(emptyDataSource).initialize();
        Flow flow = flow(new TestStep("step1", false));
        flow.addListener(new PersistentExecutionListener(emptyDataSource));
        FlowContext context = flow.execute(FlowContext.create());

        String executionId = context.get(FlowContextKeys.EXECUTION_ID, String.class);
        assertEquals("unnamed-flow", emptyJdbcTemplate.queryForObject(
                "SELECT flow_name FROM flow_execution WHERE id = ?", String.class, executionId));
        assertEquals("COMPLETED", emptyJdbcTemplate.queryForObject(
                "SELECT status FROM flow_execution WHERE id = ?", String.class, executionId));
    }

    private static Flow flow(TestStep step) {
        return FlowBuilder.create().start().step(step).end().build();
    }

    static class TestStep extends AbstractStep<Void, Void> {

        private final String name;
        private final boolean fail;

        TestStep(String name, boolean fail) {
            super(context -> null);
            this.name = name;
            this.fail = fail;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public StepType getType() {
            return StepType.INPUT;
        }

        @Override
        public FlowCategory getCategory() {
            return FlowCategory.INGEST;
        }

        @Override
        public Class<Void> getInputType() {
            return Void.class;
        }

        @Override
        public Void resolveInput(FlowContext context) {
            return null;
        }

        @Override
        protected Void doProcess(Void input, FlowContext context) {
            if (fail) {
                throw new IllegalStateException("boom");
            }
            return null;
        }

        @Override
        protected void writeOutput(Void output, FlowContext context) {
        }
    }
}
