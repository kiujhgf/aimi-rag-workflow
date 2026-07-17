package io.github.aimi.rag.workflow.log.jpa;

import io.github.aimi.rag.workflow.core.flow.FlowExecutionRepository;
import io.github.aimi.rag.workflow.core.flow.PersistentExecutionListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@AutoConfiguration
@ConditionalOnProperty(name = "aimi.rag.step-log-jdbc", havingValue = "true", matchIfMissing = true)
@EnableJpaRepositories(basePackages = "io.github.aimi.rag.workflow.log.jpa.repository")
@EntityScan(basePackages = "io.github.aimi.rag.workflow.log.jpa.entity")
public class JpaFlowExecutionAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "aimi.rag.auto-init-tables", havingValue = "true", matchIfMissing = true)
    public DatabaseInitializer databaseInitializer(JdbcTemplate jdbcTemplate) {
        return new DatabaseInitializer(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(FlowExecutionRepository.class)
    @DependsOn("databaseInitializer")
    public FlowExecutionRepository flowExecutionRepository(JpaFlowExecutionRepository repository) {
        return repository;
    }

    @Bean
    @ConditionalOnBean(FlowExecutionRepository.class)
    public PersistentExecutionListener persistentExecutionListener(FlowExecutionRepository repository) {
        return new PersistentExecutionListener(repository);
    }
}