package io.github.aimi.rag.workflow.log.autoconfigure;

import io.github.aimi.rag.workflow.core.flow.Flow;
import io.github.aimi.rag.workflow.core.flow.FlowBuilder;
import io.github.aimi.rag.workflow.core.flow.FlowExecutionListener;
import io.github.aimi.rag.workflow.core.flow.FlowExecutionRepository;
import io.github.aimi.rag.workflow.core.flow.StepExecutionRecord;
import io.github.aimi.rag.workflow.core.flow.StepFailureRecord;
import io.github.aimi.rag.workflow.core.model.FlowContext;
import io.github.aimi.rag.workflow.core.model.FlowContextKeys;
import io.github.aimi.rag.workflow.log.LogSchemaInitializer;
import io.github.aimi.rag.workflow.log.PersistentExecutionListener;
import io.github.aimi.rag.workflow.log.repository.FlowExecutionJpaRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LogPersistenceAutoConfigurationTest {

    private static ApplicationContextRunner runner(DataSource dataSource) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LogPersistenceAutoConfiguration.class))
                .withBean("dataSource", DataSource.class, () -> dataSource);
    }

    private static JdbcDataSource newDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }

    private static Flow minimalFlow() {
        return FlowBuilder.create().build();
    }

    private static int tableCount(DataSource dataSource) {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_NAME IN ('FLOW_EXECUTION', 'STEP_EXECUTION', 'STEP_FAILURE')",
                Integer.class);
    }

    @Test
    void shouldProvideLogBeansAndMountListenerForSingleDataSource() {
        JdbcDataSource dataSource = newDataSource();
        Flow flow = minimalFlow();

        runner(dataSource)
                .withBean(Flow.class, () -> flow)
                .run(context -> {
                    assertThat(context).hasSingleBean(FlowExecutionRepository.class);
                    assertThat(context.getBean(FlowExecutionRepository.class))
                            .isInstanceOf(FlowExecutionJpaRepository.class);
                    assertThat(context).hasSingleBean(PersistentExecutionListener.class);
                    assertThat(context).hasSingleBean(LogSchemaInitializer.class);
                    assertThat(flow.getListeners())
                            .contains(context.getBean(PersistentExecutionListener.class));
                });
    }

    @Test
    void shouldMountCustomListenerBeans() {
        JdbcDataSource dataSource = newDataSource();
        Flow flow = minimalFlow();
        FlowExecutionListener customListener = new FlowExecutionListener() {
        };

        runner(dataSource)
                .withBean("customListener", FlowExecutionListener.class, () -> customListener)
                .withBean(Flow.class, () -> flow)
                .run(context -> assertThat(flow.getListeners())
                        .contains(customListener, context.getBean(PersistentExecutionListener.class)));
    }

    @Test
    void shouldUseNamedDataSourceWhenMultipleDataSourcesExist() {
        JdbcDataSource logDataSource = newDataSource();
        JdbcDataSource otherDataSource = newDataSource();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LogPersistenceAutoConfiguration.class))
                .withBean("logDataSource", DataSource.class, () -> logDataSource)
                .withBean("otherDataSource", DataSource.class, () -> otherDataSource)
                .withPropertyValues("aimi.rag.log.data-source-bean-name=logDataSource")
                .run(context -> {
                    assertThat(context).hasSingleBean(PersistentExecutionListener.class);
                    assertThat(tableCount(logDataSource)).isEqualTo(3);
                    assertThat(tableCount(otherDataSource)).isZero();
                });
    }

    @Test
    void shouldSkipLogBeansWhenMultipleDataSourcesHaveNoPrimary() {
        Flow flow = minimalFlow();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LogPersistenceAutoConfiguration.class))
                .withBean("firstDataSource", DataSource.class, LogPersistenceAutoConfigurationTest::newDataSource)
                .withBean("secondDataSource", DataSource.class, LogPersistenceAutoConfigurationTest::newDataSource)
                .withBean(Flow.class, () -> flow)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FlowExecutionRepository.class);
                    assertThat(context).doesNotHaveBean(PersistentExecutionListener.class);
                    assertThat(context).doesNotHaveBean(LogSchemaInitializer.class);
                    assertThat(flow.getListeners()).isEmpty();
                });
    }

    @Test
    void shouldSkipLogBeansWhenNoDataSourceExists() {
        Flow flow = minimalFlow();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LogPersistenceAutoConfiguration.class))
                .withBean(Flow.class, () -> flow)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FlowExecutionRepository.class);
                    assertThat(context).doesNotHaveBean(PersistentExecutionListener.class);
                    assertThat(flow.getListeners()).isEmpty();
                });
    }

    @Test
    void shouldBackOffAndUseCustomRepository() {
        JdbcDataSource dataSource = newDataSource();
        Flow flow = minimalFlow();
        RecordingRepository repository = new RecordingRepository();

        runner(dataSource)
                .withBean(FlowExecutionRepository.class, () -> repository)
                .withBean(Flow.class, () -> flow)
                .run(context -> {
                    assertThat(context.getBean(FlowExecutionRepository.class)).isSameAs(repository);
                    assertThat(context).hasSingleBean(PersistentExecutionListener.class);

                    flow.execute(FlowContext.create());

                    assertThat(repository.calls)
                            .contains("create:unnamed-flow", "status:COMPLETED");
                });
    }

    @Test
    void shouldDisableAutoConfigurationWhenPropertyIsFalse() {
        JdbcDataSource dataSource = newDataSource();
        Flow flow = minimalFlow();

        runner(dataSource)
                .withPropertyValues("aimi.rag.log.enabled=false")
                .withBean(Flow.class, () -> flow)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FlowExecutionRepository.class);
                    assertThat(context).doesNotHaveBean(PersistentExecutionListener.class);
                    assertThat(context).doesNotHaveBean(LogSchemaInitializer.class);
                    assertThat(flow.getListeners()).isEmpty();
                });
    }

    @Test
    void shouldCreateSchemaAndPersistCompletedStatusByDefault() {
        JdbcDataSource dataSource = newDataSource();
        Flow flow = minimalFlow();

        runner(dataSource)
                .withBean(Flow.class, () -> flow)
                .run(context -> {
                    assertThat(tableCount(dataSource)).isEqualTo(3);

                    FlowContext flowContext = flow.execute(FlowContext.create());
                    String executionId = flowContext.get(FlowContextKeys.EXECUTION_ID, String.class);

                    assertThat(new JdbcTemplate(dataSource).queryForObject(
                            "SELECT status FROM flow_execution WHERE id = ?", String.class, executionId))
                            .isEqualTo("COMPLETED");
                });
    }

    @Test
    void shouldSkipSchemaCreationWhenInitializeSchemaIsFalse() {
        JdbcDataSource dataSource = newDataSource();

        runner(dataSource)
                .withPropertyValues("aimi.rag.log.initialize-schema=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(LogSchemaInitializer.class);
                    assertThat(tableCount(dataSource)).isZero();
                });
    }

    private static final class RecordingRepository implements FlowExecutionRepository {

        private final List<String> calls = new ArrayList<>();

        @Override
        public void createExecution(String flowName, FlowContext inputContext) {
            calls.add("create:" + flowName);
        }

        @Override
        public void saveStepExecution(FlowContext context, StepExecutionRecord record) {
            calls.add("step:" + record.getStepName());
        }

        @Override
        public void saveStepFailure(FlowContext context, StepFailureRecord record) {
            calls.add("failure:" + record.getStepName());
        }

        @Override
        public void updateExecutionStatus(FlowContext context, String status) {
            calls.add("status:" + status);
        }

        @Override
        public List<StepExecutionRecord> getExecutionHistory(String executionId) {
            return List.of();
        }
    }
}
