package com.ibm.codeanalyzer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Security and CORS configuration.
 *
 * Note: This is a basic configuration for development/demo purposes.
 * For production, implement proper authentication and authorization.
 */
@Configuration
public class SecurityConfig implements WebMvcConfigurer {

    /**
     * Configure CORS to allow frontend applications to access the API.
     * This applies globally to all endpoints.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // Allow requests from any origin (for demo purposes)
                // In production, specify exact origins like:
                // .allowedOrigins("http://localhost:5173", "http://localhost:3000")
                .allowedOriginPatterns("*")
                
                // Allow common HTTP methods
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH")
                
                // Allow common headers
                .allowedHeaders("*")
                
                // Allow credentials (cookies, authorization headers)
                .allowCredentials(true)
                
                // Cache preflight response for 1 hour
                .maxAge(3600)
                
                // Expose headers that the frontend can read
                .exposedHeaders("Content-Length", "Content-Type", "Authorization");
    }
}


