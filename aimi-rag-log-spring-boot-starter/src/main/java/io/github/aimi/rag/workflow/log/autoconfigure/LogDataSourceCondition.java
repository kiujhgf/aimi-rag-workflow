package io.github.aimi.rag.workflow.log.autoconfigure;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * Matches when a usable DataSource can be resolved:
 * <ul>
 *   <li>{@code aimi.rag.log.data-source-bean-name} points to a DataSource bean, or</li>
 *   <li>there is exactly one DataSource, or</li>
 *   <li>there are multiple DataSources and one of them is {@code @Primary}.</li>
 * </ul>
 */
class LogDataSourceCondition implements Condition {

    static final String DATA_SOURCE_BEAN_NAME_PROPERTY = "aimi.rag.log.data-source-bean-name";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
        if (beanFactory == null) {
            return false;
        }

        String beanName = context.getEnvironment().getProperty(DATA_SOURCE_BEAN_NAME_PROPERTY);
        if (StringUtils.hasText(beanName)) {
            return beanFactory.containsBean(beanName)
                    && beanFactory.isTypeMatch(beanName, DataSource.class);
        }

        String[] names = beanFactory.getBeanNamesForType(DataSource.class, true, false);
        if (names.length == 1) {
            return true;
        }
        if (names.length > 1) {
            for (String name : names) {
                if (beanFactory.containsBeanDefinition(name)
                        && beanFactory.getBeanDefinition(name).isPrimary()) {
                    return true;
                }
            }
        }
        return false;
    }
}
