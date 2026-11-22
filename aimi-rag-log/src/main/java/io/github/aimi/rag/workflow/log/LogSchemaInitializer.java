package io.github.aimi.rag.workflow.log;

import io.github.aimi.rag.workflow.log.dialect.JdbcLogRepositoryDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;

public class LogSchemaInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final JdbcLogRepositoryDialect dialect;

    public LogSchemaInitializer(DataSource dataSource) {
        this(dataSource, null);
    }

    public LogSchemaInitializer(DataSource dataSource, JdbcLogRepositoryDialect dialect) {
        this.jdbcTemplate = new JdbcTemplate(
                Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.dialect = JdbcLogRepositoryDialect.resolve(dataSource, dialect);
    }

    public void initialize() {
        List<String> statements = List.of(
                dialect.getCreateFlowExecutionTableSql(),
                dialect.getCreateStepExecutionTableSql(),
                dialect.getCreateStepFailureTableSql(),
                dialect.getCreateStepExecutionIndexSql(),
                dialect.getCreateStepFailureIndexSql());

        for (String statement : statements) {
            try {
                jdbcTemplate.execute(statement);
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to execute log schema statement; continuing with remaining statements: {}",
                        statement, e);
            }
        }
    }
}
