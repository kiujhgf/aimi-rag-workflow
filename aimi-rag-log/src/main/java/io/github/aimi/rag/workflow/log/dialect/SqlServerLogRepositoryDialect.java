package io.github.aimi.rag.workflow.log.dialect;

public class SqlServerLogRepositoryDialect extends AbstractLogRepositoryDialect {

    public SqlServerLogRepositoryDialect() {
        super("VARCHAR", "DATETIMEOFFSET(6)", "NVARCHAR(MAX)", "NVARCHAR(MAX)", false, false);
    }
}
