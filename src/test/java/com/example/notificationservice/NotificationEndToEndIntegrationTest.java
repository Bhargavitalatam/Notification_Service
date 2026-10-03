package com.example.notificationservice;

import com.example.notificationservice.client.UserProfileServiceClient;
import com.example.notificationservice.dto.NotificationRecord;
import com.example.notificationservice.dto.NotificationRequest;
import com.example.notificationservice.mock.MockUserProfileServiceController;
import com.example.notificationservice.service.NotificationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"notification_events"})
@DirtiesContext
class NotificationEndToEndIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private NotificationStore notificationStore;

    @Autowired
    private UserProfileServiceClient client;

    @Autowired
    private MockUserProfileServiceController mockController;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(client, "userProfileServiceUrl", "http://localhost:" + port + "/mock-users");
        mockController.configureMock(false, 0L);
        notificationStore.clear();
    }

    @Test
    @DisplayName("End-to-End: POST /send produces event to Kafka and consumer processes message into NotificationStore")
    void testEndToEndNotificationFlow() throws InterruptedException {
        NotificationRequest request = new NotificationRequest("alice101", "Order #9923 Dispatched", "EMAIL");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/notifications/send",
                request,
                String.class
        );

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("Notification request received and queued.", response.getBody());

        // Wait for asynchronous Kafka message delivery and consumer processing
        NotificationRecord processedRecord = null;
        for (int i = 0; i < 20; i++) {
            List<NotificationRecord> records = notificationStore.getAll();
            if (!records.isEmpty()) {
                processedRecord = records.get(0);
                break;
            }
            Thread.sleep(500);
        }

        assertNotNull(processedRecord, "Notification should have been processed and stored by consumer");
        assertEquals("alice101", processedRecord.getUserId());
        assertEquals("alice101@example.com", processedRecord.getEmail());
        assertEquals("Order #9923 Dispatched", processedRecord.getMessage());
        assertEquals("EMAIL", processedRecord.getNotificationType());
        assertEquals("SENT", processedRecord.getStatus());
    }
}
