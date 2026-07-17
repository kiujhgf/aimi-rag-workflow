package io.github.aimi.rag.workflow.log.mybatis;

import io.github.aimi.rag.workflow.core.flow.FlowExecutionRepository;
import io.github.aimi.rag.workflow.core.flow.PersistentExecutionListener;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@AutoConfiguration
@ConditionalOnProperty(name = "aimi.rag.step-log-jdbc", havingValue = "true", matchIfMissing = true)
@MapperScan(basePackages = "io.github.aimi.rag.workflow.log.mybatis.mapper")
public class MyBatisFlowExecutionAutoConfiguration {

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
    public FlowExecutionRepository flowExecutionRepository(MyBatisFlowExecutionRepository repository) {
        return repository;
    }

    @Bean
    @ConditionalOnBean(FlowExecutionRepository.class)
    public PersistentExecutionListener persistentExecutionListener(FlowExecutionRepository repository) {
        return new PersistentExecutionListener(repository);
    }
}