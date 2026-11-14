package com.bankledger.account.config;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.GenericFilterBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OpenTelemetryLoggingTest {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryLoggingTest.class);

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("account_db")
            .withUsername("bankledger")
            .withPassword("bankledger");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("bankledger.outbox.poller.auto-startup", () -> "false");
    }

    private final WebApplicationContext context;
    private final Tracer tracer;
    private MockMvc mockMvc;
    private final AtomicReference<String> capturedMdcTraceId = new AtomicReference<>();
    private final AtomicReference<String> capturedContextTraceId = new AtomicReference<>();

    public OpenTelemetryLoggingTest(WebApplicationContext context, Tracer tracer) {
        this.context = context;
        this.tracer = tracer;
    }

    @BeforeEach
    void setUp() {
        capturedMdcTraceId.set(null);
        capturedContextTraceId.set(null);

        var captureFilter = new GenericFilterBean() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                try {
                    String mdcTrace = MDC.get("traceId");
                    capturedMdcTraceId.set(mdcTrace);

                    if (tracer.currentSpan() != null && tracer.currentSpan().context() != null) {
                        capturedContextTraceId.set(tracer.currentSpan().context().traceId());
                    }
                    log.info("Inspecting trace context inside filter: mdcTraceId={}, spanContextTraceId={}",
                            mdcTrace, capturedContextTraceId.get());
                    chain.doFilter(request, response);
                } finally {
                    // Do not clear here so we only test what was present during execution
                }
            }
        };

        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .addFilters(captureFilter)
                .build();
    }

    @Test
    @DisplayName("Incoming request to /actuator/health should generate non-blank traceId in context and MDC")
    void shouldGenerateTraceIdOnIncomingRequest() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        log.info("Captured results: mdcTraceId='{}', contextTraceId='{}'",
                capturedMdcTraceId.get(), capturedContextTraceId.get());

        // Verify that a non-blank traceId is present in MDC
        assertThat(capturedMdcTraceId.get())
                .as("traceId in MDC must not be null or blank")
                .isNotBlank()
                .hasSize(32);

        // Verify that a non-blank traceId is present in tracing context
        assertThat(capturedContextTraceId.get())
                .as("traceId in tracing context must not be null or blank")
                .isNotBlank()
                .hasSize(32);

        // Verify MDC and Tracer context match
        assertThat(capturedMdcTraceId.get())
                .as("MDC traceId must match Tracer context traceId")
                .isEqualTo(capturedContextTraceId.get());
    }
}
