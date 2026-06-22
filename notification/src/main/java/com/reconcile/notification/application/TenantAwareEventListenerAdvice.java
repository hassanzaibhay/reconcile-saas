package com.reconcile.notification.application;

import com.reconcile.shared.domain.TenantContext;
import com.reconcile.shared.domain.TenantScopedEvent;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * BeanPostProcessor that wraps every {@code @ApplicationModuleListener} method invocation with
 * TenantContext propagation. Reads {@link TenantScopedEvent#tenantId()} from the event payload and
 * sets/clears TenantContext around the listener call.
 *
 * <p>Guarantees: listeners never need to set TenantContext themselves; missing the context is a
 * bug in the event producer, not the consumer.
 */
@Component
public class TenantAwareEventListenerAdvice implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
            throws BeansException {
        boolean hasModuleListener =
                hasApplicationModuleListenerMethod(bean.getClass());
        if (!hasModuleListener) {
            return bean;
        }
        ProxyFactory factory = new ProxyFactory(bean);
        factory.addAdvice(
                (MethodInterceptor)
                        invocation -> {
                            Object[] args = invocation.getArguments();
                            if (args.length == 1 && args[0] instanceof TenantScopedEvent event
                                    && isModuleListenerMethod(invocation.getMethod())) {
                                try {
                                    TenantContext.set(event.tenantId());
                                    return invocation.proceed();
                                } finally {
                                    TenantContext.clear();
                                }
                            }
                            return invocation.proceed();
                        });
        return factory.getProxy();
    }

    private boolean hasApplicationModuleListenerMethod(Class<?> clazz) {
        for (Method m : clazz.getMethods()) {
            if (m.isAnnotationPresent(ApplicationModuleListener.class)) return true;
        }
        return false;
    }

    private boolean isModuleListenerMethod(Method method) {
        return method.isAnnotationPresent(ApplicationModuleListener.class);
    }
}
