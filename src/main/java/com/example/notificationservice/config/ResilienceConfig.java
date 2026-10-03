package com.example.notificationservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public ResilienceConfig(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @PostConstruct
    public void registerEventLogging() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("userProfileService");

        cb.getEventPublisher()
                .onStateTransition(event -> {
                    System.out.println(">>> [CIRCUIT BREAKER STATE TRANSITION] Instance: "
                            + event.getCircuitBreakerName()
                            + " transitioned from " + event.getStateTransition().getFromState()
                            + " to " + event.getStateTransition().getToState());
                    log.info(">>> [CIRCUIT BREAKER STATE TRANSITION] {} transitioned from {} to {}",
                            event.getCircuitBreakerName(),
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());
                })
                .onError(event -> {
                    log.warn(">>> [CIRCUIT BREAKER RECORDED ERROR] Instance: {}, duration: {} ms, error: {}",
                            event.getCircuitBreakerName(),
                            event.getElapsedDuration().toMillis(),
                            event.getThrowable().getMessage());
                })
                .onCallNotPermitted(event -> {
                    System.out.println(">>> [CIRCUIT BREAKER CALL NOT PERMITTED] Instance: "
                            + event.getCircuitBreakerName() + " is in OPEN state. Request failed fast without network call.");
                    log.warn(">>> [CIRCUIT BREAKER CALL NOT PERMITTED] {} is OPEN. Request rejected fast.",
                            event.getCircuitBreakerName());
                })
                .onSuccess(event -> {
                    log.debug(">>> [CIRCUIT BREAKER SUCCESS] Instance: {}", event.getCircuitBreakerName());
                });
    }
}
