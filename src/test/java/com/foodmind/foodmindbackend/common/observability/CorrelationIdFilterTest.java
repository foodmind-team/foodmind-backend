package com.foodmind.foodmindbackend.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 4:27 pm
 */

class CorrelationIdFilterTest {

    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CorrelationTestController())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void acceptsValidCorrelationIdAndReturnsItInHeaderAndRequestContext() throws Exception {
        mockMvc.perform(get("/api/v1/correlation")
                        .header(CorrelationIdFilter.HEADER_NAME, "postman-correlation-test"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "postman-correlation-test"))
                .andExpect(jsonPath("$.traceId").value("postman-correlation-test"));

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesCorrelationIdWhenHeaderIsAbsent() throws Exception {
        mockMvc.perform(get("/api/v1/correlation"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.traceId", matchesPattern(UUID_PATTERN)));

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void replacesInvalidCorrelationIdWithGeneratedValue() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/correlation")
                        .header(CorrelationIdFilter.HEADER_NAME, "bad correlation id"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.traceId", matchesPattern(UUID_PATTERN)))
                .andReturn();

        assertThat(result.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME))
                .isNotEqualTo("bad correlation id");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @RestController
    static class CorrelationTestController {

        @GetMapping("/api/v1/correlation")
        Map<String, String> currentTrace() {
            return Map.of("traceId", CorrelationIdFilter.currentCorrelationId());
        }
    }
}
