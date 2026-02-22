package com.example.messaging.stream.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

import com.example.messaging.stream.model.Event;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {

    private final StreamBridge streamBridge;

    public void publishEvent(Event event) {
        log.info("Publishing event: {} to binding: eventProducer-out-0", event.getEventId());
        boolean sent = streamBridge.send("eventProducer-out-0", event);
        
        if (sent) {
            log.info("Event published successfully: {}", event.getEventId());
        } else {
            log.error("Failed to publish event: {}", event.getEventId());
        }
    }

    public void publishToInputEvents(Event event) {
        log.info("Publishing event: {} to input-events", event.getEventId());
        boolean sent = streamBridge.send("input-events", event);
        
        if (sent) {
            log.info("Event sent to input-events: {}", event.getEventId());
        } else {
            log.error("Failed to send event to input-events: {}", event.getEventId());
        }
    }
}
