package io.github.aimi.rag.workflow.log.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the aimi-rag-log starter.
 */
@ConfigurationProperties(prefix = "aimi.rag.log")
public class LogPersistenceProperties {

    /**
     * Whether to create the log tables and indexes on startup.
     */
    private boolean initializeSchema = true;

    /**
     * Name of the DataSource bean to use. When empty, the unique DataSource
     * (or the {@code @Primary} one) is used.
     */
    private String dataSourceBeanName;

    public boolean isInitializeSchema() {
        return initializeSchema;
    }

    public void setInitializeSchema(boolean initializeSchema) {
        this.initializeSchema = initializeSchema;
    }

    public String getDataSourceBeanName() {
        return dataSourceBeanName;
    }

    public void setDataSourceBeanName(String dataSourceBeanName) {
        this.dataSourceBeanName = dataSourceBeanName;
    }
}
