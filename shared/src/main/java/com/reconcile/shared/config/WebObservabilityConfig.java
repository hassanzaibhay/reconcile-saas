package com.reconcile.shared.config;

import com.reconcile.shared.web.TraceIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class WebObservabilityConfig {

    /**
     * Registered at highest precedence so the {@code traceId} MDC key is set before Spring Security's
     * filter chain (default order -100) runs — the security entry-point/denied handlers must be able to
     * read it when emitting 401/403.
     */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(new TraceIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
