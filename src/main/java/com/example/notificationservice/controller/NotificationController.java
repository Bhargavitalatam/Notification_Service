package com.example.notificationservice.controller;

import com.example.notificationservice.dto.NotificationRecord;
import com.example.notificationservice.dto.NotificationRequest;
import com.example.notificationservice.service.NotificationProducerService;
import com.example.notificationservice.service.NotificationStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationProducerService producerService;
    private final NotificationStore notificationStore;

    public NotificationController(NotificationProducerService producerService,
                                  NotificationStore notificationStore) {
        this.producerService = producerService;
        this.notificationStore = notificationStore;
    }

    @PostMapping("/send")
    public ResponseEntity<String> sendNotification(@RequestBody NotificationRequest request) {
        producerService.publishNotificationEvent(request); // Delegate to producer service
        return ResponseEntity.accepted().body("Notification request received and queued.");
    }

    @GetMapping("/records")
    public ResponseEntity<List<NotificationRecord>> getNotificationRecords() {
        return ResponseEntity.ok(notificationStore.getAll());
    }

    @DeleteMapping("/records")
    public ResponseEntity<String> clearNotificationRecords() {
        notificationStore.clear();
        return ResponseEntity.ok("Notification records cleared.");
    }
}
