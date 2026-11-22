package io.github.aimi.rag.workflow.log.dialect;

public class GenericLogRepositoryDialect extends AbstractLogRepositoryDialect {

    public GenericLogRepositoryDialect() {
        super("VARCHAR", "TIMESTAMP", "CLOB", "CLOB", true, true);
    }
}
