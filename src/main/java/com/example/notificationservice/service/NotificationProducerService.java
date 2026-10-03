package com.example.notificationservice.service;

import com.example.notificationservice.dto.NotificationRequest;
import com.example.notificationservice.event.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class NotificationProducerService {

    private static final Logger log = LoggerFactory.getLogger(NotificationProducerService.class);
    public static final String TOPIC = "notification_events";

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    public NotificationProducerService(KafkaTemplate<String, NotificationEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishNotificationEvent(NotificationRequest request) {
        NotificationEvent event = new NotificationEvent(
                UUID.randomUUID().toString(),
                request.getUserId(),
                request.getMessage(),
                request.getNotificationType(),
                LocalDateTime.now()
        );
        kafkaTemplate.send(TOPIC, event.getUserId(), event); // Use userId as key for partitioning
        System.out.println("Published event: " + event);
        log.info("Published event: id={}, userId={}, type={}",
                event.getEventId(), event.getUserId(), event.getNotificationType());
    }
}
