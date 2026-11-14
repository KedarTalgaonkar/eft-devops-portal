package com.euronet.eft.portal.config;

import com.euronet.eft.portal.security.audit.ApiAuditInterceptor;
import com.euronet.eft.portal.security.audit.AuditService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC setup. No CORS: the SPA and the API share one origin (nginx in the containers, the dev-server proxy in
 * development), so cross-origin requests are refused by default. Security lives in the security package.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ObjectProvider<AuditService> audit;

    public WebConfig(ObjectProvider<AuditService> audit) {
        this.audit = audit;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        audit.ifAvailable(a -> registry.addInterceptor(new ApiAuditInterceptor(a)).addPathPatterns("/api/**"));
    }
}
