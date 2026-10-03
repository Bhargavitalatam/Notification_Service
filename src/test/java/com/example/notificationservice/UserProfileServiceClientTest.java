package com.example.notificationservice;

import com.example.notificationservice.client.UserProfileServiceClient;
import com.example.notificationservice.dto.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceClientTest {

    @Mock
    private RestTemplate restTemplate;

    private UserProfileServiceClient client;
    private final String serviceUrl = "http://mock-service:8080/mock-users";

    @BeforeEach
    void setUp() {
        client = new UserProfileServiceClient(restTemplate, serviceUrl);
    }

    @Test
    @DisplayName("Should successfully retrieve UserProfile when external service responds normally")
    void testGetUserProfileSuccess() {
        UserProfile mockProfile = new UserProfile("user456", "user456@example.com", "+1-456-555-0100");
        when(restTemplate.getForObject(serviceUrl + "/{userId}", UserProfile.class, "user456"))
                .thenReturn(mockProfile);

        UserProfile result = client.getUserProfile("user456");

        assertNotNull(result);
        assertEquals("user456", result.getUserId());
        assertEquals("user456@example.com", result.getEmail());
        assertEquals("+1-456-555-0100", result.getPhoneNumber());
        verify(restTemplate, times(1)).getForObject(serviceUrl + "/{userId}", UserProfile.class, "user456");
    }

    @Test
    @DisplayName("Should invoke fallback and return default fallback profile on exception")
    void testGetUserProfileFallback() {
        Throwable exception = new RuntimeException("Simulated connection timeout");
        UserProfile fallback = client.getUserProfileFallback("user789", exception);

        assertNotNull(fallback);
        assertEquals("user789", fallback.getUserId());
        assertEquals("fallback@example.com", fallback.getEmail());
        assertEquals("+1-FALLBACK", fallback.getPhoneNumber());
    }
}
