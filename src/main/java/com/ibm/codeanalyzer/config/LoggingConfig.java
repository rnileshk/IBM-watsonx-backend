package com.ibm.codeanalyzer.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoggingConfig implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        long start = System.currentTimeMillis();

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        try {
            chain.doFilter(request, response);
        } finally {
            long time = System.currentTimeMillis() - start;
            System.out.println(req.getMethod() + " " + req.getRequestURI()
                    + " -> " + res.getStatus() + " in " + time + "ms");
        }
    }
}
