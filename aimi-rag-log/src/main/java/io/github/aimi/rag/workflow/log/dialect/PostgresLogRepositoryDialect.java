package io.github.aimi.rag.workflow.log.dialect;

public class PostgresLogRepositoryDialect extends AbstractLogRepositoryDialect {

    public PostgresLogRepositoryDialect() {
        super("VARCHAR", "TIMESTAMP(6) WITH TIME ZONE", "TEXT", "TEXT", true, true);
    }

    @Override
    public String getInsertFlowExecutionSql() {
        return """
                INSERT INTO flow_execution (id, flow_name, status, start_time, input_context)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """.strip();
    }
}
