package com.example.messaging.stream.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.messaging.stream.model.Event;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@Configuration
public class EventStreamProcessor {

    @Bean
    public Consumer<Event> eventConsumer() {
        return event -> {
            log.info("Received event: {} with type: {}", event.getEventId(), event.getEventType());
            log.info("Event payload: {}", event.getPayload());
            processEvent(event);
        };
    }

    @Bean
    public Consumer<Event> processedEventConsumer() {
        return event -> {
            log.info("✅ CONSUMED PROCESSED EVENT: {} with type: {}", event.getEventId(), event.getEventType());
            log.info("✅ Processed payload: {}", event.getPayload());
        };
    }

    @Bean
    public Function<Event, Event> eventProcessor() {
        return event -> {
            log.info("Processing event: {}", event.getEventId());
            
            // Transform/enrich the event
            event.setTimestamp(LocalDateTime.now());
            event.getPayload().put("processed", true);
            event.getPayload().put("processedAt", LocalDateTime.now().toString());
            
            log.info("Event processed successfully: {}", event.getEventId());
            return event;
        };
    }

    private void processEvent(Event event) {
        // Business logic here
        log.info("Processing business logic for event type: {}", event.getEventType());
    }
}
