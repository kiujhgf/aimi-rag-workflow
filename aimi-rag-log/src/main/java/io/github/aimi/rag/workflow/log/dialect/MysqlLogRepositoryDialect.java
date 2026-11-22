package io.github.aimi.rag.workflow.log.dialect;

public class MysqlLogRepositoryDialect extends AbstractLogRepositoryDialect {

    public MysqlLogRepositoryDialect() {
        super("VARCHAR", "TIMESTAMP(6)", "LONGTEXT", "TEXT", true, false);
    }

    @Override
    public String getInsertFlowExecutionSql() {
        return """
                INSERT IGNORE INTO flow_execution (id, flow_name, status, start_time, input_context)
                VALUES (?, ?, ?, ?, ?)
                """.strip();
    }
}
