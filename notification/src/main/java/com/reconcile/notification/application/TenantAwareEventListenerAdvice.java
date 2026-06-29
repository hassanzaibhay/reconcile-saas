package com.reconcile.notification.application;

import com.reconcile.shared.domain.TenantContext;
import com.reconcile.shared.domain.TenantScopedEvent;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * BeanPostProcessor that weaves TenantContext propagation into every
 * {@code @ApplicationModuleListener} method. Reads {@link TenantScopedEvent#tenantId()} from the
 * event payload and sets/clears TenantContext around the listener call.
 *
 * <p>When the bean is already a Spring AOP proxy ({@link Advised}), the interceptor is prepended
 * to the existing advice chain to avoid creating a proxy-of-a-proxy (which breaks
 * {@code MethodIntrospector} and {@code EventListenerMethodProcessor}).
 */
@Component
public class TenantAwareEventListenerAdvice implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!hasApplicationModuleListenerMethod(bean.getClass())) {
            return bean;
        }
        MethodInterceptor interceptor = invocation -> {
            Object[] args = invocation.getArguments();
            if (args.length == 1
                    && args[0] instanceof TenantScopedEvent event
                    && isModuleListenerMethod(invocation.getMethod())) {
                try {
                    TenantContext.set(event.tenantId());
                    return invocation.proceed();
                } finally {
                    TenantContext.clear();
                }
            }
            return invocation.proceed();
        };
        // Prefer adding to an existing AOP proxy rather than wrapping with a second proxy.
        // A proxy-of-a-proxy breaks EventListenerMethodProcessor (MethodIntrospector fails on
        // the CGLIB-generated class of the outer proxy at context startup).
        if (bean instanceof Advised advised && !advised.isFrozen()) {
            advised.addAdvice(0, interceptor);
            return bean;
        }
        ProxyFactory factory = new ProxyFactory(bean);
        factory.addAdvice(interceptor);
        return factory.getProxy();
    }

    private boolean hasApplicationModuleListenerMethod(Class<?> clazz) {
        for (Method m : clazz.getMethods()) {
            // AnnotationUtils.findAnnotation traverses the class hierarchy so CGLIB-generated
            // override methods (which do not carry the source annotation) are handled correctly.
            if (AnnotationUtils.findAnnotation(m, ApplicationModuleListener.class) != null) return true;
        }
        return false;
    }

    private boolean isModuleListenerMethod(Method method) {
        return AnnotationUtils.findAnnotation(method, ApplicationModuleListener.class) != null;
    }
}
