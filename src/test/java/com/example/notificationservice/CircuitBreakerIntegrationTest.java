package com.example.notificationservice;

import com.example.notificationservice.client.UserProfileServiceClient;
import com.example.notificationservice.dto.UserProfile;
import com.example.notificationservice.mock.MockUserProfileServiceController;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CircuitBreakerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserProfileServiceClient client;

    @Autowired
    private MockUserProfileServiceController mockController;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // Point the client dynamically to the local random port
        ReflectionTestUtils.setField(client, "userProfileServiceUrl", "http://localhost:" + port + "/mock-users");
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("userProfileService");
        circuitBreaker.reset();
        mockController.configureMock(false, 0L);
    }

    @Test
    @DisplayName("Verify full Circuit Breaker lifecycle: CLOSED -> OPEN -> HALF_OPEN -> CLOSED")
    void testCircuitBreakerLifecycle() throws InterruptedException {
        // 1. Initial State should be CLOSED
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());

        // Normal successful call
        UserProfile successProfile = client.getUserProfile("user-ok");
        assertNotNull(successProfile);
        assertEquals("user-ok@example.com", successProfile.getEmail());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());

        // 2. Simulate failures to trip Circuit Breaker to OPEN
        mockController.configureMock(true, 0L);

        // Make calls that will fail until failureRateThreshold is met
        for (int i = 0; i < 4; i++) {
            UserProfile fallbackProfile = client.getUserProfile("user-fail-" + i);
            assertNotNull(fallbackProfile);
            assertEquals("fallback@example.com", fallbackProfile.getEmail());
        }

        // Circuit breaker should now be OPEN
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        // 3. While OPEN, call fails fast without network call and triggers fallback
        UserProfile fastFailProfile = client.getUserProfile("user-fast-fail");
        assertNotNull(fastFailProfile);
        assertEquals("fallback@example.com", fastFailProfile.getEmail());
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        // 4. Wait for waitDurationInOpenState (1500ms in test config)
        Thread.sleep(1700);

        // Reset mock to success so recovery calls succeed
        mockController.configureMock(false, 0L);

        // 5. Next call should trigger transition to HALF_OPEN
        UserProfile recoveryProfile1 = client.getUserProfile("user-rec-1");
        assertNotNull(recoveryProfile1);
        assertEquals("user-rec-1@example.com", recoveryProfile1.getEmail());

        // Second permitted call in HALF_OPEN to close the breaker
        UserProfile recoveryProfile2 = client.getUserProfile("user-rec-2");
        assertNotNull(recoveryProfile2);
        assertEquals("user-rec-2@example.com", recoveryProfile2.getEmail());

        // Breaker should have recovered back to CLOSED
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }
}
