package com.example.notificationservice.service;

import com.example.notificationservice.dto.NotificationRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class NotificationStore {

    private final List<NotificationRecord> notifications = new CopyOnWriteArrayList<>();

    public void add(NotificationRecord record) {
        notifications.add(record);
    }

    public List<NotificationRecord> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(notifications));
    }

    public void clear() {
        notifications.clear();
    }

    public int size() {
        return notifications.size();
    }
}
