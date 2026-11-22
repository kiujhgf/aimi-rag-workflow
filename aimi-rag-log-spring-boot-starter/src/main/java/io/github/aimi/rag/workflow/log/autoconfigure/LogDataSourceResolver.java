package io.github.aimi.rag.workflow.log.autoconfigure;

import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * Resolves the DataSource used by the log auto-configuration, following the
 * same rules as {@link LogDataSourceCondition}.
 */
final class LogDataSourceResolver {

    private LogDataSourceResolver() {
    }

    static DataSource resolve(ApplicationContext context, LogPersistenceProperties properties) {
        String beanName = properties.getDataSourceBeanName();
        if (StringUtils.hasText(beanName)) {
            return context.getBean(beanName, DataSource.class);
        }

        DataSource dataSource = context.getBeanProvider(DataSource.class).getIfUnique();
        if (dataSource == null) {
            throw new IllegalStateException("No unique DataSource found; "
                    + "configure aimi.rag.log.data-source-bean-name or mark one DataSource as @Primary");
        }
        return dataSource;
    }
}
