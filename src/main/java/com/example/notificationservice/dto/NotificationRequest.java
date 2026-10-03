package com.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {
    private String userId; // Unique identifier for the user
    private String message;
    private String notificationType; // e.g., "EMAIL", "SMS", "PUSH"
}
