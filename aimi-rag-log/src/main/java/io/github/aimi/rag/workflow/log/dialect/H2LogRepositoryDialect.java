package io.github.aimi.rag.workflow.log.dialect;

public class H2LogRepositoryDialect extends AbstractLogRepositoryDialect {

    public H2LogRepositoryDialect() {
        super("VARCHAR", "TIMESTAMP(6) WITH TIME ZONE", "TEXT", "TEXT", true, true);
    }
}
