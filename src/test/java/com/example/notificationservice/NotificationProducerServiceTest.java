package com.example.notificationservice;

import com.example.notificationservice.dto.NotificationRequest;
import com.example.notificationservice.event.NotificationEvent;
import com.example.notificationservice.service.NotificationProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationProducerServiceTest {

    @Mock
    private KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    private NotificationProducerService producerService;

    @BeforeEach
    void setUp() {
        producerService = new NotificationProducerService(kafkaTemplate);
    }

    @Test
    @DisplayName("Should publish NotificationEvent to Kafka with userId as partitioning key")
    void testPublishNotificationEvent() {
        NotificationRequest request = new NotificationRequest("user123", "Hello World", "EMAIL");

        producerService.publishNotificationEvent(request);

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(kafkaTemplate, times(1)).send(eq("notification_events"), eq("user123"), eventCaptor.capture());

        NotificationEvent publishedEvent = eventCaptor.getValue();
        assertNotNull(publishedEvent);
        assertNotNull(publishedEvent.getEventId());
        assertEquals("user123", publishedEvent.getUserId());
        assertEquals("Hello World", publishedEvent.getMessage());
        assertEquals("EMAIL", publishedEvent.getNotificationType());
        assertNotNull(publishedEvent.getTimestamp());
    }
}
