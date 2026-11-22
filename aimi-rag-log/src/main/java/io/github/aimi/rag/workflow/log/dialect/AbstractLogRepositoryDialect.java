package io.github.aimi.rag.workflow.log.dialect;

abstract class AbstractLogRepositoryDialect implements JdbcLogRepositoryDialect {

    private final String varcharType;
    private final String timestampType;
    private final String inputContextType;
    private final String textType;
    private final String createTablePrefix;
    private final String createIndexPrefix;

    AbstractLogRepositoryDialect(String varcharType, String timestampType,
                                 String inputContextType, String textType,
                                 boolean createTableIfNotExists,
                                 boolean createIndexIfNotExists) {
        this.varcharType = varcharType;
        this.timestampType = timestampType;
        this.inputContextType = inputContextType;
        this.textType = textType;
        this.createTablePrefix = createTableIfNotExists ? "IF NOT EXISTS " : "";
        this.createIndexPrefix = createIndexIfNotExists ? "IF NOT EXISTS " : "";
    }

    @Override
    public String getInsertFlowExecutionSql() {
        return """
                INSERT INTO flow_execution (id, flow_name, status, start_time, input_context)
                VALUES (?, ?, ?, ?, ?)
                """.strip();
    }

    @Override
    public String getUpdateFlowExecutionStatusSql() {
        return "UPDATE flow_execution SET status = ?, end_time = ? WHERE id = ?";
    }

    @Override
    public String getInsertStepExecutionSql() {
        return """
                INSERT INTO step_execution (id, execution_id, step_name, status, start_time, end_time)
                VALUES (?, ?, ?, ?, ?, ?)
                """.strip();
    }

    @Override
    public String getSelectStepExecutionsSql() {
        return """
                SELECT step_name, status, start_time, end_time
                FROM step_execution
                WHERE execution_id = ?
                ORDER BY start_time ASC
                """.strip();
    }

    @Override
    public String getInsertStepFailureSql() {
        return """
                INSERT INTO step_failure (id, execution_id, step_name, error_message, error_stack, timestamp)
                VALUES (?, ?, ?, ?, ?, ?)
                """.strip();
    }

    @Override
    public String getCreateFlowExecutionTableSql() {
        return ("""
                CREATE TABLE %sflow_execution (
                    id %s(64) PRIMARY KEY,
                    flow_name %s(128) NOT NULL,
                    status %s(32) NOT NULL,
                    start_time %s NOT NULL,
                    end_time %s,
                    input_context %s
                )
                """.formatted(createTablePrefix, varcharType, varcharType, varcharType,
                timestampType, timestampType, inputContextType)).strip();
    }

    @Override
    public String getCreateStepExecutionTableSql() {
        return ("""
                CREATE TABLE %sstep_execution (
                    id %s(64) PRIMARY KEY,
                    execution_id %s(64) NOT NULL,
                    step_name %s(128) NOT NULL,
                    status %s(32) NOT NULL,
                    start_time %s NOT NULL,
                    end_time %s
                )
                """.formatted(createTablePrefix, varcharType, varcharType, varcharType,
                varcharType, timestampType, timestampType)).strip();
    }

    @Override
    public String getCreateStepFailureTableSql() {
        return ("""
                CREATE TABLE %sstep_failure (
                    id %s(64) PRIMARY KEY,
                    execution_id %s(64) NOT NULL,
                    step_name %s(128) NOT NULL,
                    error_message %s,
                    error_stack %s,
                    timestamp %s NOT NULL
                )
                """.formatted(createTablePrefix, varcharType, varcharType, varcharType,
                textType, textType, timestampType)).strip();
    }

    @Override
    public String getCreateStepExecutionIndexSql() {
        return "CREATE INDEX " + createIndexPrefix
                + "idx_step_execution_execution_id ON step_execution (execution_id)";
    }

    @Override
    public String getCreateStepFailureIndexSql() {
        return "CREATE INDEX " + createIndexPrefix
                + "idx_step_failure_execution_id ON step_failure (execution_id)";
    }
}
