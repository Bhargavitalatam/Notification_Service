package com.example.notificationservice.client;

import com.example.notificationservice.dto.UserProfile;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class UserProfileServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserProfileServiceClient.class);

    private final RestTemplate restTemplate;
    private final String userProfileServiceUrl;

    public UserProfileServiceClient(RestTemplate restTemplate,
                                    @Value("${user.profile.service.url}") String userProfileServiceUrl) {
        this.restTemplate = restTemplate;
        this.userProfileServiceUrl = userProfileServiceUrl;
    }

    @CircuitBreaker(name = "userProfileService", fallbackMethod = "getUserProfileFallback")
    public UserProfile getUserProfile(String userId) {
        System.out.println("Attempting to call User Profile Service for user: " + userId);
        log.info("Attempting to call User Profile Service for user: {} at {}", userId, userProfileServiceUrl);
        return restTemplate.getForObject(userProfileServiceUrl + "/{userId}", UserProfile.class, userId);
    }

    public UserProfile getUserProfileFallback(String userId, Throwable t) {
        System.err.println("Circuit Breaker open or call failed for user: " + userId + ". Error: " + t.getMessage() + " - Circuit Breaker open, notification deferred");
        log.warn("Circuit Breaker open or call failed for user: {}. Error: {}. Circuit Breaker open, notification deferred.", userId, t.getMessage());
        return new UserProfile(userId, "fallback@example.com", "+1-FALLBACK");
    }
}
