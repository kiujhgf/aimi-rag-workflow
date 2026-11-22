package io.github.aimi.rag.workflow.log.autoconfigure;

import io.github.aimi.rag.workflow.core.flow.Flow;
import io.github.aimi.rag.workflow.core.flow.FlowExecutionListener;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Mounts every {@link FlowExecutionListener} bean onto every {@link Flow} bean.
 *
 * <p>Listeners are looked up lazily through the {@link BeanFactory} so that
 * this post-processor does not force listener beans to be created while it is
 * being registered.
 */
public class FlowExecutionListenerRegistrar implements BeanPostProcessor, BeanFactoryAware {

    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof Flow flow) {
            beanFactory.getBeanProvider(FlowExecutionListener.class)
                    .orderedStream()
                    .forEach(flow::addListener);
        }
        return bean;
    }
}
