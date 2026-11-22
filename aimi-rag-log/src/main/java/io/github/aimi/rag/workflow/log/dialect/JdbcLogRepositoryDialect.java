package io.github.aimi.rag.workflow.log.dialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;

/**
 * Provides the complete SQL statements used by the JDBC log repositories.
 */
public interface JdbcLogRepositoryDialect {

    Logger LOGGER = LoggerFactory.getLogger(JdbcLogRepositoryDialect.class);

    static JdbcLogRepositoryDialect from(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            String normalizedUrl = url == null ? "" : url.toLowerCase(Locale.ROOT);
            if (normalizedUrl.contains("postgresql")) {
                return new PostgresLogRepositoryDialect();
            }
            if (normalizedUrl.contains("mysql") || normalizedUrl.contains("mariadb")) {
                return new MysqlLogRepositoryDialect();
            }
            if (normalizedUrl.contains("sqlserver")) {
                return new SqlServerLogRepositoryDialect();
            }
            if (normalizedUrl.contains("h2")) {
                return new H2LogRepositoryDialect();
            }
            if (normalizedUrl.contains("oracle")) {
                return new OracleLogRepositoryDialect();
            }
            if (normalizedUrl.contains("sqlite")) {
                return new SqliteLogRepositoryDialect();
            }
            LOGGER.warn("Unrecognized JDBC URL '{}'; using generic log repository dialect", url);
            return new GenericLogRepositoryDialect();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not detect dialect from datasource", e);
        }
    }

    static JdbcLogRepositoryDialect resolve(DataSource dataSource,
                                            JdbcLogRepositoryDialect explicitDialect) {
        if (explicitDialect == null) {
            return from(dataSource);
        }
        try {
            JdbcLogRepositoryDialect detectedDialect = from(dataSource);
            if (!detectedDialect.getClass().equals(explicitDialect.getClass())) {
                LOGGER.warn("Explicit log repository dialect {} differs from detected dialect {}",
                        explicitDialect.getClass().getSimpleName(),
                        detectedDialect.getClass().getSimpleName());
            }
        } catch (IllegalStateException e) {
            LOGGER.debug("Could not verify explicit log repository dialect against datasource", e);
        }
        return explicitDialect;
    }

    String getInsertFlowExecutionSql();

    String getUpdateFlowExecutionStatusSql();

    String getInsertStepExecutionSql();

    String getSelectStepExecutionsSql();

    String getInsertStepFailureSql();

    String getCreateFlowExecutionTableSql();

    String getCreateStepExecutionTableSql();

    String getCreateStepFailureTableSql();

    String getCreateStepExecutionIndexSql();

    String getCreateStepFailureIndexSql();
}
