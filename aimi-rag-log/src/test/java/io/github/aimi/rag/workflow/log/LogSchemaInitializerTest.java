package io.github.aimi.rag.workflow.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.aimi.rag.workflow.log.dialect.H2LogRepositoryDialect;
import io.github.aimi.rag.workflow.log.repository.FlowExecutionJpaRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LogSchemaInitializerTest {

    private JdbcDataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void shouldNotExecuteDdlUntilInitializeIsCalled() {
        new LogSchemaInitializer(dataSource);
        new FlowExecutionJpaRepository(dataSource);
        new PersistentExecutionListener(dataSource);

        assertEquals(0, tableCount());
    }

    @Test
    void shouldCreateTablesAndIndexesIdempotently() throws Exception {
        LogSchemaInitializer initializer = new LogSchemaInitializer(dataSource);

        initializer.initialize();
        initializer.initialize();

        assertEquals(3, tableCount());
        assertTrue(indexExists("STEP_EXECUTION", "IDX_STEP_EXECUTION_EXECUTION_ID"));
        assertTrue(indexExists("STEP_FAILURE", "IDX_STEP_FAILURE_EXECUTION_ID"));
    }

    @Test
    void shouldWarnAndContinueAfterStatementFailure() {
        class FailingDialect extends H2LogRepositoryDialect {
            @Override
            public String getCreateFlowExecutionTableSql() {
                return "INVALID DDL";
            }
        }
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(LogSchemaInitializer.class);
        appender.start();
        logger.addAppender(appender);
        try {
            assertDoesNotThrow(() -> new LogSchemaInitializer(dataSource, new FailingDialect()).initialize());

            assertEquals(2, tableCount());
            assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN
                    && event.getFormattedMessage().contains("continuing with remaining statements")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private int tableCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_NAME IN ('FLOW_EXECUTION', 'STEP_EXECUTION', 'STEP_FAILURE')", Integer.class);
    }

    private boolean indexExists(String tableName, String indexName) throws Exception {
        try (Connection connection = dataSource.getConnection();
             ResultSet indexes = connection.getMetaData().getIndexInfo(null, "PUBLIC", tableName, false, false)) {
            while (indexes.next()) {
                if (indexName.equals(indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
