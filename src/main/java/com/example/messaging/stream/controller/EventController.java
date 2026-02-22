package com.example.messaging.stream.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.messaging.stream.model.Event;
import com.example.messaging.stream.service.EventPublisher;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventPublisher eventPublisher;

    
    /*
     * 
     * 

  curl -X POST http://localhost:7070/api/events/publish \
  -H "Content-Type: application/json" \
  -d '{"eventType": "USER_CREATED", "source": "test", "payload": {"name": "John"}}'

     */
    
    
    @PostMapping("/publish")
    public ResponseEntity<Map<String, Object>> publishEvent(@RequestBody Event event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(LocalDateTime.now());
        }
        
        eventPublisher.publishEvent(event);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("eventId", event.getEventId());
        response.put("message", "Event published successfully");
        
        return ResponseEntity.ok(response);
    }
    
    /*
 
curl -X POST http://localhost:7070/api/events/input \
  -H "Content-Type: application/json" \
  -d '{"eventType": "ORDER_CREATED", "source": "test", "payload": {"orderId": "789", "amount": 99.99}}'


     */
    
    
    /**
     * 
     * @param event
     * @return
     */

    @PostMapping("/input")
    public ResponseEntity<Map<String, Object>> publishToInput(@RequestBody Event event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(LocalDateTime.now());
        }
        
        eventPublisher.publishToInputEvents(event);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("eventId", event.getEventId());
        response.put("message", "Event sent to input-events");
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "event-processor");
        return ResponseEntity.ok(response);
    }
}
