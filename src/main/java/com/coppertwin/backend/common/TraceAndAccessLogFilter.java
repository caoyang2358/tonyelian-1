package com.coppertwin.backend.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceAndAccessLogFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(TraceAndAccessLogFilter.class);
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started=System.nanoTime();
        String supplied=request.getHeader("X-Trace-Id");
        String traceId=supplied!=null && supplied.matches("[A-Za-z0-9._-]{8,64}") ? supplied : UUID.randomUUID().toString().replace("-", "");
        MDC.put("traceId", traceId); response.setHeader("X-Trace-Id", traceId);
        try { chain.doFilter(request,response); }
        finally {
            long millis=(System.nanoTime()-started)/1_000_000;
            log.info("request method={} path={} status={} durationMs={}", request.getMethod(), request.getRequestURI(), response.getStatus(), millis);
            MDC.remove("traceId");
        }
    }
}

