package com.hrplatform.backend;

import com.hrplatform.backend.service.EmailService;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Base class for integration tests: real PostgreSQL via Testcontainers (Flyway runs all
 * migrations), dummy secrets from application-test.yaml, and a mocked EmailService so no
 * request ever reaches Resend.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestBase {

    @MockitoBean
    protected EmailService emailService;
}
