package io.github.aimi.rag.workflow.log.autoconfigure;

import io.github.aimi.rag.workflow.core.flow.FlowExecutionRepository;
import io.github.aimi.rag.workflow.log.LogSchemaInitializer;
import io.github.aimi.rag.workflow.log.PersistentExecutionListener;
import io.github.aimi.rag.workflow.log.repository.FlowExecutionJpaRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * Auto-configuration that wires {@code aimi-rag-log} persistence into a Spring
 * Boot application.
 *
 * <p>When a usable {@link javax.sql.DataSource} is available, it provides a
 * {@link FlowExecutionRepository} ({@link FlowExecutionJpaRepository}), a
 * {@link PersistentExecutionListener} and a {@link LogSchemaInitializer}. All
 * beans back off when the application declares its own.
 *
 * <p>{@link FlowExecutionListenerRegistrar} mounts every
 * {@code FlowExecutionListener} bean onto every {@code Flow} bean managed by
 * the Spring container, so business code does not need to call
 * {@code flow.addListener(...)}. Flows created outside the container (for
 * example with {@code new} or a factory) are not affected.
 *
 * <p>Properties:
 * <ul>
 *   <li>{@code aimi.rag.log.initialize-schema} (default {@code true}): run
 *       {@link LogSchemaInitializer#initialize()} on startup. Set to
 *       {@code false} to manage the DDL yourself; the initializer bean remains
 *       available for manual invocation.</li>
 *   <li>{@code aimi.rag.log.data-source-bean-name}: name of the
 *       {@link javax.sql.DataSource} bean to use when several are present.
 *       Without it, the unique (or {@code @Primary}) DataSource is used; if
 *       several exist and none is primary, the log beans are not created.</li>
 *   <li>{@code aimi.rag.log.enabled} (default {@code true}): set to
 *       {@code false} to disable this auto-configuration entirely.</li>
 * </ul>
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "aimi.rag.log", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LogPersistenceProperties.class)
public class LogPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @Conditional(LogDataSourceCondition.class)
    public FlowExecutionRepository flowExecutionRepository(ApplicationContext applicationContext,
                                                           LogPersistenceProperties properties) {
        return new FlowExecutionJpaRepository(LogDataSourceResolver.resolve(applicationContext, properties));
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(LogDataSourceCondition.class)
    public PersistentExecutionListener persistentExecutionListener(FlowExecutionRepository repository) {
        return new PersistentExecutionListener(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(LogDataSourceCondition.class)
    public LogSchemaInitializer logSchemaInitializer(ApplicationContext applicationContext,
                                                     LogPersistenceProperties properties) {
        LogSchemaInitializer initializer = new LogSchemaInitializer(
                LogDataSourceResolver.resolve(applicationContext, properties));
        if (properties.isInitializeSchema()) {
            initializer.initialize();
        }
        return initializer;
    }

    @Bean
    @ConditionalOnMissingBean
    public static FlowExecutionListenerRegistrar flowExecutionListenerRegistrar() {
        return new FlowExecutionListenerRegistrar();
    }
}
