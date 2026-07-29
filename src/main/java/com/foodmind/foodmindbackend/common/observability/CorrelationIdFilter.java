package com.foodmind.foodmindbackend.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 4:27 pm
 */

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    private static final int MAX_CORRELATION_ID_LENGTH = 128;
    private static final Pattern ACCEPTED_VALUE = Pattern.compile("[A-Za-z0-9._:-]+");

    public static String currentCorrelationId() {
        return MDC.get(MDC_KEY);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = acceptOrGenerate(request.getHeader(HEADER_NAME));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    public static String acceptOrGenerate(String candidate) {
        if (candidate == null) {
            return UUID.randomUUID().toString();
        }

        String trimmed = candidate.trim();
        if (trimmed.isEmpty()
                || trimmed.length() > MAX_CORRELATION_ID_LENGTH
                || !ACCEPTED_VALUE.matcher(trimmed).matches()) {
            return UUID.randomUUID().toString();
        }
        return trimmed;
    }
}
