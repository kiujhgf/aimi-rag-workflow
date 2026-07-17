package io.github.aimi.rag.workflow.log.mybatis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.SQLException;

@Component
public class DatabaseInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        logger.info("Initializing flow execution tables if not exists...");

        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            String databaseType = resolveDatabaseType(connection);
            logger.info("Detected database type: {}", databaseType);

            createFlowExecutionTable(databaseType);
            createStepExecutionTable(databaseType);
            createStepFailureTable(databaseType);

            logger.info("Flow execution tables initialization completed");
        } catch (SQLException e) {
            logger.warn("Failed to initialize flow execution tables: {}", e.getMessage());
        }
    }

    private String resolveDatabaseType(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName().toLowerCase();
        if (productName.contains("mysql")) {
            return "mysql";
        } else if (productName.contains("postgresql")) {
            return "postgresql";
        } else if (productName.contains("h2")) {
            return "h2";
        } else if (productName.contains("sqlite")) {
            return "sqlite";
        } else if (productName.contains("microsoft sql server")) {
            return "sqlserver";
        } else if (productName.contains("oracle")) {
            return "oracle";
        } else {
            return "generic";
        }
    }

    private void createFlowExecutionTable(String databaseType) {
        String sql = """
            CREATE TABLE IF NOT EXISTS flow_execution (
                id VARCHAR(64) PRIMARY KEY,
                flow_name VARCHAR(128) NOT NULL,
                status VARCHAR(32) NOT NULL,
                start_time TIMESTAMP NOT NULL,
                end_time TIMESTAMP,
                input_context TEXT
            )
            """;
        executeSql(sql, databaseType);
    }

    private void createStepExecutionTable(String databaseType) {
        String sql = """
            CREATE TABLE IF NOT EXISTS step_execution (
                id VARCHAR(64) PRIMARY KEY,
                execution_id VARCHAR(64) NOT NULL,
                step_name VARCHAR(128) NOT NULL,
                status VARCHAR(32) NOT NULL,
                start_time TIMESTAMP NOT NULL,
                end_time TIMESTAMP,
                INDEX idx_step_execution_execution_id (execution_id)
            )
            """;
        executeSql(sql, databaseType);
    }

    private void createStepFailureTable(String databaseType) {
        String sql = """
            CREATE TABLE IF NOT EXISTS step_failure (
                id VARCHAR(64) PRIMARY KEY,
                execution_id VARCHAR(64) NOT NULL,
                step_name VARCHAR(128) NOT NULL,
                error_message TEXT,
                error_stack TEXT,
                timestamp TIMESTAMP NOT NULL,
                INDEX idx_step_failure_execution_id (execution_id)
            )
            """;
        executeSql(sql, databaseType);
    }

    private void executeSql(String sql, String databaseType) {
        try {
            String adjustedSql = adjustSqlForDatabase(sql, databaseType);
            jdbcTemplate.execute(adjustedSql);
            logger.debug("Executed SQL: {}", adjustedSql);
        } catch (Exception e) {
            logger.warn("Failed to execute SQL: {}", e.getMessage());
        }
    }

    private String adjustSqlForDatabase(String sql, String databaseType) {
        return switch (databaseType) {
            case "sqlite" -> sql.replace("INDEX ", "");
            case "sqlserver" -> sql.replace("INDEX ", "");
            case "oracle" -> sql.replace("INDEX ", "").replace("VARCHAR(", "VARCHAR2(").replace("TEXT", "CLOB");
            default -> sql;
        };
    }
}