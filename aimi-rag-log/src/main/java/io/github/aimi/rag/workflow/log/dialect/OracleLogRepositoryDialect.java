package io.github.aimi.rag.workflow.log.dialect;

public class OracleLogRepositoryDialect extends AbstractLogRepositoryDialect {

    public OracleLogRepositoryDialect() {
        super("VARCHAR2", "TIMESTAMP(6) WITH TIME ZONE", "CLOB", "CLOB", false, false);
    }
}
