package io.github.aimi.rag.workflow.log.dialect;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class JdbcLogRepositoryDialectTest {

    static Stream<Arguments> dialects() {
        String standardInsert = "INSERT INTO flow_execution (id, flow_name, status, start_time, input_context) "
                + "VALUES (?, ?, ?, ?, ?)";
        return Stream.of(
                arguments(new MysqlLogRepositoryDialect(),
                        "INSERT IGNORE INTO flow_execution (id, flow_name, status, start_time, input_context) "
                                + "VALUES (?, ?, ?, ?, ?)",
                        "VARCHAR", "TIMESTAMP(6)", "LONGTEXT", "TEXT", true, false),
                arguments(new PostgresLogRepositoryDialect(), standardInsert + " ON CONFLICT (id) DO NOTHING",
                        "VARCHAR", "TIMESTAMP(6) WITH TIME ZONE", "TEXT", "TEXT", true, true),
                arguments(new H2LogRepositoryDialect(), standardInsert,
                        "VARCHAR", "TIMESTAMP(6) WITH TIME ZONE", "TEXT", "TEXT", true, true),
                arguments(new OracleLogRepositoryDialect(), standardInsert,
                        "VARCHAR2", "TIMESTAMP(6) WITH TIME ZONE", "CLOB", "CLOB", false, false),
                arguments(new SqlServerLogRepositoryDialect(), standardInsert,
                        "VARCHAR", "DATETIMEOFFSET(6)", "NVARCHAR(MAX)", "NVARCHAR(MAX)", false, false),
                arguments(new SqliteLogRepositoryDialect(), standardInsert + " ON CONFLICT (id) DO NOTHING",
                        "VARCHAR", "TEXT", "TEXT", "TEXT", true, true),
                arguments(new GenericLogRepositoryDialect(), standardInsert,
                        "VARCHAR", "TIMESTAMP", "CLOB", "CLOB", true, true));
    }

    @ParameterizedTest
    @MethodSource("dialects")
    void shouldProvideCompleteDmlAndDdl(JdbcLogRepositoryDialect dialect, String insertFlow,
                                        String varchar, String timestamp, String inputText, String text,
                                        boolean tableIfNotExists, boolean indexIfNotExists) {
        assertNormalizedEquals(insertFlow, dialect.getInsertFlowExecutionSql());
        assertNormalizedEquals("UPDATE flow_execution SET status = ?, end_time = ? WHERE id = ?",
                dialect.getUpdateFlowExecutionStatusSql());
        assertNormalizedEquals("INSERT INTO step_execution "
                        + "(id, execution_id, step_name, status, start_time, end_time) VALUES (?, ?, ?, ?, ?, ?)",
                dialect.getInsertStepExecutionSql());
        assertNormalizedEquals("SELECT step_name, status, start_time, end_time FROM step_execution "
                        + "WHERE execution_id = ? ORDER BY start_time ASC",
                dialect.getSelectStepExecutionsSql());
        assertNormalizedEquals("INSERT INTO step_failure "
                        + "(id, execution_id, step_name, error_message, error_stack, timestamp) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                dialect.getInsertStepFailureSql());

        String tablePrefix = tableIfNotExists ? "IF NOT EXISTS " : "";
        String indexPrefix = indexIfNotExists ? "IF NOT EXISTS " : "";
        assertNormalizedEquals("CREATE TABLE " + tablePrefix + "flow_execution ( "
                        + "id " + varchar + "(64) PRIMARY KEY, "
                        + "flow_name " + varchar + "(128) NOT NULL, "
                        + "status " + varchar + "(32) NOT NULL, "
                        + "start_time " + timestamp + " NOT NULL, "
                        + "end_time " + timestamp + ", "
                        + "input_context " + inputText + " )",
                dialect.getCreateFlowExecutionTableSql());
        assertNormalizedEquals("CREATE TABLE " + tablePrefix + "step_execution ( "
                        + "id " + varchar + "(64) PRIMARY KEY, "
                        + "execution_id " + varchar + "(64) NOT NULL, "
                        + "step_name " + varchar + "(128) NOT NULL, "
                        + "status " + varchar + "(32) NOT NULL, "
                        + "start_time " + timestamp + " NOT NULL, "
                        + "end_time " + timestamp + " )",
                dialect.getCreateStepExecutionTableSql());
        assertNormalizedEquals("CREATE TABLE " + tablePrefix + "step_failure ( "
                        + "id " + varchar + "(64) PRIMARY KEY, "
                        + "execution_id " + varchar + "(64) NOT NULL, "
                        + "step_name " + varchar + "(128) NOT NULL, "
                        + "error_message " + text + ", "
                        + "error_stack " + text + ", "
                        + "timestamp " + timestamp + " NOT NULL )",
                dialect.getCreateStepFailureTableSql());
        assertNormalizedEquals("CREATE INDEX " + indexPrefix
                        + "idx_step_execution_execution_id ON step_execution (execution_id)",
                dialect.getCreateStepExecutionIndexSql());
        assertNormalizedEquals("CREATE INDEX " + indexPrefix
                        + "idx_step_failure_execution_id ON step_failure (execution_id)",
                dialect.getCreateStepFailureIndexSql());
    }

    private static void assertNormalizedEquals(String expected, String actual) {
        assertEquals(normalize(expected), normalize(actual));
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
