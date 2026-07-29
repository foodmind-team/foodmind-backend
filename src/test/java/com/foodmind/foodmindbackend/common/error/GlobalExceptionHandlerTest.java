package com.foodmind.foodmindbackend.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmind.foodmindbackend.common.observability.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.NoHandlerFoundException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 4:27 pm
 */

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JsonMapper objectMapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new ApiConventionTestController())
                .setControllerAdvice(new GlobalExceptionHandler(objectMapper))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(objectMapper))
                .setValidator(validator)
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void validationErrorsUseStableEnvelopeAndSortedPublicFields() throws Exception {
        mockMvc.perform(post("/api/v1/test-validation")
                        .header(CorrelationIdFilter.HEADER_NAME, "test-correlation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "price": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "test-correlation"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("The request contains invalid fields."))
                .andExpect(jsonPath("$.path").value("/api/v1/test-validation"))
                .andExpect(jsonPath("$.traceId").value("test-correlation"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("NOT_BLANK"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("price"))
                .andExpect(jsonPath("$.fieldErrors[1].code").value("POSITIVE_OR_ZERO"));
    }

    @Test
    void unknownJsonFieldsAreRejectedAsValidationErrors() throws Exception {
        mockMvc.perform(post("/api/v1/test-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Soy Bowl",
                                  "price": 5.50,
                                  "debugSql": "select * from secret"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("debugSql"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("UNKNOWN_FIELD"))
                .andExpect(jsonPath("$.message", not(containsString("select *"))));
    }

    @Test
    void malformedJsonUsesSafeMalformedJsonCode() throws Exception {
        mockMvc.perform(post("/api/v1/test-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
                .andExpect(jsonPath("$.message").value("The request body is malformed or contains unsupported values."))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void requestBindingFailuresUsePublicParameterName() throws Exception {
        mockMvc.perform(get("/api/v1/test-type").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("TYPE_MISMATCH"));
    }

    @Test
    void mvcNotFoundExceptionsUseSafeNotFoundEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/test-mvc-not-found")
                        .header(CorrelationIdFilter.HEADER_NAME, "postman-correlation-test"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "postman-correlation-test"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/test-mvc-not-found"))
                .andExpect(jsonPath("$.traceId").value("postman-correlation-test"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void apiExceptionsUseSafeDomainMapping() throws Exception {
        mockMvc.perform(get("/api/v1/test-domain/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested resource was not found."));

        mockMvc.perform(get("/api/v1/test-domain/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Resource is already in use."));
    }

    @Test
    void unexpectedExceptionsDoNotExposeRawDetails() throws Exception {
        mockMvc.perform(get("/api/v1/test-boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.message", not(containsString("password"))));
    }

    @RestController
    static class ApiConventionTestController {

        @PostMapping("/api/v1/test-validation")
        TestResponse validate(@Valid @RequestBody TestRequest request) {
            return new TestResponse("ok");
        }

        @GetMapping("/api/v1/test-type")
        TestResponse type(@RequestParam Integer page) {
            return new TestResponse(String.valueOf(page));
        }

        @GetMapping("/api/v1/test-domain/{kind}")
        TestResponse domain(@PathVariable String kind) {
            if ("not-found".equals(kind)) {
                throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            if ("conflict".equals(kind)) {
                throw new ApiException(ErrorCode.CONFLICT, "Resource is already in use.");
            }
            return new TestResponse("ok");
        }

        @GetMapping("/api/v1/test-boom")
        TestResponse boom() {
            throw new IllegalStateException("password leaked from SQL detail");
        }

        @GetMapping("/api/v1/test-mvc-not-found")
        TestResponse mvcNotFound() throws NoHandlerFoundException {
            throw new NoHandlerFoundException("GET", "/api/v1/does-not-exist", new HttpHeaders());
        }
    }

    record TestRequest(
            @NotBlank String name,
            @PositiveOrZero BigDecimal price) {
    }

    record TestResponse(String status) {
    }
}
