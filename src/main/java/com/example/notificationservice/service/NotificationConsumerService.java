package com.example.notificationservice.service;

import com.example.notificationservice.client.UserProfileServiceClient;
import com.example.notificationservice.dto.NotificationRecord;
import com.example.notificationservice.dto.UserProfile;
import com.example.notificationservice.event.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class NotificationConsumerService {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumerService.class);

    private final UserProfileServiceClient userProfileServiceClient;
    private final NotificationStore notificationStore;

    public NotificationConsumerService(UserProfileServiceClient userProfileServiceClient,
                                       NotificationStore notificationStore) {
        this.userProfileServiceClient = userProfileServiceClient;
        this.notificationStore = notificationStore;
    }

    @KafkaListener(
            topics = "notification_events",
            groupId = "notification_group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeNotificationEvent(NotificationEvent event) {
        System.out.println("Consumed event for user: " + event.getUserId() + ", type: " + event.getNotificationType());
        log.info("Processing notification event: id={}, userId={}, type={}",
                event.getEventId(), event.getUserId(), event.getNotificationType());

        // Fetch user profile through circuit-breaker protected client
        UserProfile profile = userProfileServiceClient.getUserProfile(event.getUserId());

        boolean isFallback = profile != null && "fallback@example.com".equals(profile.getEmail());
        String status = isFallback ? "DEFERRED_FALLBACK" : "SENT";

        if (isFallback) {
            System.err.println("[NOTIFICATION DEFERRED] Fallback user profile used for user: "
                    + event.getUserId() + ". Notification deferred due to external service unavailability.");
            log.warn("[NOTIFICATION DEFERRED] Notification for user {} deferred: fallback contact info applied.",
                    event.getUserId());
        } else {
            System.out.println("[NOTIFICATION SENT] Notification successfully dispatched to user: "
                    + event.getUserId()
                    + " [Email: " + (profile != null ? profile.getEmail() : "N/A")
                    + ", Phone: " + (profile != null ? profile.getPhoneNumber() : "N/A")
                    + "] Message: " + event.getMessage());
            log.info("[NOTIFICATION SENT] Successfully processed notification for user: {}", event.getUserId());
        }

        NotificationRecord record = NotificationRecord.builder()
                .eventId(event.getEventId())
                .userId(event.getUserId())
                .email(profile != null ? profile.getEmail() : null)
                .phoneNumber(profile != null ? profile.getPhoneNumber() : null)
                .message(event.getMessage())
                .notificationType(event.getNotificationType())
                .status(status)
                .processedAt(LocalDateTime.now())
                .build();

        notificationStore.add(record);
    }
}
