package com.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRecord {
    private String eventId;
    private String userId;
    private String email;
    private String phoneNumber;
    private String message;
    private String notificationType;
    private String status; // e.g. "SENT", "DEFERRED_FALLBACK"
    private LocalDateTime processedAt;
}
