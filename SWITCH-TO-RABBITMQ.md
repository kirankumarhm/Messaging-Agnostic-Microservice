# Switching from Kafka to RabbitMQ - Complete Guide

This guide demonstrates the **messaging-agnostic architecture** of Spring Cloud Stream, allowing you to switch between Kafka and RabbitMQ without changing any business logic code.

---

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Step-by-Step Setup](#step-by-step-setup)
3. [Configuration Explained](#configuration-explained)
4. [Testing the Application](#testing-the-application)
5. [Monitoring in RabbitMQ UI](#monitoring-in-rabbitmq-ui)
6. [Troubleshooting](#troubleshooting)
7. [Key Concepts](#key-concepts)

---

## Prerequisites

- Java 17+
- Maven 3.6+
- Docker (for RabbitMQ)
- curl or Postman (for testing)

---

## Step-by-Step Setup

### Step 1: Start RabbitMQ with Docker Compose

**Use docker-compose-rabbitmq.yml:**

```bash
docker-compose -f docker-compose-rabbitmq.yml up -d
```

**Verify RabbitMQ is running:**
```bash
docker-compose -f docker-compose-rabbitmq.yml ps
```

**Expected Output:**
```
NAME                STATUS              PORTS
rabbitmq            Up                  0.0.0.0:5672->5672/tcp, 0.0.0.0:15672->15672/tcp
```

Access Management UI: http://localhost:15672 (admin/admin)

### Step 2: Update pom.xml

Both Kafka and RabbitMQ binders are included:

```xml
<!-- Kafka Binder -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream-binder-kafka</artifactId>
</dependency>

<!-- RabbitMQ Binder -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream-binder-rabbit</artifactId>
</dependency>
```

**Note**: Having both binders allows profile-based switching without rebuilding.

### Step 3: Create application-rabbitmq.yml

Create `src/main/resources/application-rabbitmq.yml`:

```yaml
spring:
  application:
    name: event-processor
  rabbitmq:
    host: localhost
    port: 5672
    username: admin
    password: admin
  cloud:
    function:
      definition: eventConsumer;processedEventConsumer
    stream:
      default-binder: rabbit
      bindings:
        eventProducer-out-0:
          destination: processed-events
          content-type: application/json
          binder: rabbit
        eventConsumer-in-0:
          destination: input-events
          content-type: application/json
          group: event-processor-group
          binder: rabbit
        processedEventConsumer-in-0:
          destination: processed-events
          content-type: application/json
          group: processed-consumer-group
          binder: rabbit
      rabbit:
        bindings:
          eventConsumer-in-0:
            consumer:
              autoBindDlq: true
              republishToDlq: true

server:
  port: 7070

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,bindings
  endpoint:
    health:
      show-details: always

logging:
  level:
    com.charter.elf: INFO
    org.springframework.cloud.stream: DEBUG
```

### Step 4: Set Default Profile (Optional)

In `application.yml`, set RabbitMQ as default:

```yaml
spring:
  profiles:
    active: rabbitmq
```

### Step 5: Build and Run

```bash
# Clean and build
mvn clean install

# Run with RabbitMQ profile
mvn spring-boot:run -Dspring-boot.run.profiles=rabbitmq

# Or set environment variable
export SPRING_PROFILES_ACTIVE=rabbitmq
mvn spring-boot:run
```

**Expected Startup Logs:**
```
The following 1 profile is active: "rabbitmq"
Channel 'event-processor.eventConsumer-in-0' has 1 subscriber(s).
Channel 'event-processor.processedEventConsumer-in-0' has 1 subscriber(s).
declaring queue for inbound: input-events.event-processor-group, bound to: input-events
declaring queue for inbound: processed-events.processed-consumer-group, bound to: processed-events
started bean 'inbound.input-events.event-processor-group'
started bean 'inbound.processed-events.processed-consumer-group'
```

---

## Configuration Explained

### 1. Function Definition
```yaml
cloud:
  function:
    definition: eventConsumer;processedEventConsumer
```
- **Purpose**: Declares which Consumer functions should auto-start
- **Maps to**: `@Bean` methods in `EventStreamProcessor.java`
- **Note**: `eventProducer` is NOT listed because it uses `StreamBridge` for dynamic publishing

### 2. Default Binder
```yaml
stream:
  default-binder: rabbit
```
- **Purpose**: Sets RabbitMQ as the default message broker
- **Why needed**: When both Kafka and RabbitMQ binders are in classpath, this tells Spring which to use

### 3. Bindings (Message Channels)

#### Output Binding: eventProducer-out-0
```yaml
eventProducer-out-0:
  destination: processed-events    # RabbitMQ Exchange name
  content-type: application/json
  binder: rabbit
```
- **Used by**: `StreamBridge.send("eventProducer-out-0", event)`
- **Creates**: Exchange named `processed-events`
- **Type**: Topic exchange (default)

#### Input Binding: eventConsumer-in-0
```yaml
eventConsumer-in-0:
  destination: input-events              # Exchange to consume from
  group: event-processor-group           # Consumer group (creates queue)
  binder: rabbit
```
- **Maps to**: `eventConsumer()` function
- **Creates**: Queue `input-events.event-processor-group`
- **Binds**: Queue to `input-events` exchange with routing key `#` (all messages)

#### Input Binding: processedEventConsumer-in-0
```yaml
processedEventConsumer-in-0:
  destination: processed-events
  group: processed-consumer-group
  binder: rabbit
```
- **Maps to**: `processedEventConsumer()` function
- **Creates**: Queue `processed-events.processed-consumer-group`
- **Purpose**: Consumes messages published by the application itself

### 4. RabbitMQ-Specific Configuration
```yaml
rabbit:
  bindings:
    eventConsumer-in-0:
      consumer:
        autoBindDlq: true         # Auto-create Dead Letter Queue
        republishToDlq: true      # Send failed messages to DLQ
```
- **DLQ**: Stores messages that fail processing after retries
- **Creates**: `input-events.event-processor-group.dlq` queue
- **Only for**: `eventConsumer-in-0` (not processedEventConsumer)

### Naming Convention
Spring Cloud Stream uses: `<functionName>-<in/out>-<index>`

| Function Bean | Binding Name |
|---------------|--------------|
| `eventConsumer()` | `eventConsumer-in-0` |
| `processedEventConsumer()` | `processedEventConsumer-in-0` |
| StreamBridge with "eventProducer-out-0" | `eventProducer-out-0` |

---

## Testing the Application

### 1. Publish Event via REST API

```bash
curl -X POST http://localhost:7070/api/events/publish \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "USER_CREATED",
    "source": "test-service",
    "payload": {
      "userId": "123",
      "username": "john.doe",
      "email": "john@example.com"
    }
  }'
```

**Expected Response:**
```json
{
  "status": "success",
  "eventId": "9ea84487-43df-4c76-9c86-437b9ab3ee82",
  "message": "Event published successfully"
}
```

**Expected Logs:**
```
Publishing event: 9ea84487-43df-4c76-9c86-437b9ab3ee82 to binding: eventProducer-out-0
Event published successfully: 9ea84487-43df-4c76-9c86-437b9ab3ee82
✅ CONSUMED PROCESSED EVENT: 9ea84487-43df-4c76-9c86-437b9ab3ee82 with type: USER_CREATED
✅ Processed payload: {userId=123, username=john.doe, email=john@example.com}
```

### 2. Message Flow Diagram

```
┌─────────────┐
│  REST API   │
│   (curl)    │
└──────┬──────┘
       │
       ▼
┌─────────────────────┐
│  EventController    │
│  /api/events/publish│
└──────┬──────────────┘
       │
       ▼
┌─────────────────────┐
│  EventPublisher     │
│  StreamBridge.send()│
└──────┬──────────────┘
       │
       ▼
┌─────────────────────────────┐
│  eventProducer-out-0        │
│  (Output Binding)           │
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│  RabbitMQ Exchange          │
│  "processed-events"         │
└──────┬──────────────────────┘
       │
       ▼
┌──────────────────────────────────────┐
│  Queue: processed-events.            │
│         processed-consumer-group     │
└──────┬───────────────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│  processedEventConsumer-in-0│
│  (Input Binding)            │
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│  processedEventConsumer()   │
│  Consumer Function          │
└─────────────────────────────┘
```

### 3. Send Event Directly to RabbitMQ Exchange

Go to RabbitMQ UI → Exchanges → `processed-events` → Publish message:

```json
{
  "eventId": "manual-test-123",
  "eventType": "USER_UPDATED",
  "source": "manual-test",
  "timestamp": "2026-02-20T19:54:56",
  "payload": {
    "userId": "456",
    "action": "profile_updated"
  }
}
```

Check application logs for consumption.

---

## Monitoring in RabbitMQ UI

Access: http://localhost:15672 (admin/admin)

### Exchanges Tab

You'll see these exchanges:

| Exchange Name | Type | Purpose |
|---------------|------|---------|
| `processed-events` | topic | Receives published events |
| `input-events` | topic | For external event sources |
| `input-events.event-processor-group.dlq` | topic | Dead Letter Queue |

**Click on an exchange** to see:
- Message rates (publish/deliver)
- Bindings to queues
- Publish message form (for testing)

### Queues Tab

You'll see these queues:

| Queue Name | Consumers | Purpose |
|------------|-----------|---------|
| `input-events.event-processor-group` | 1 | Consumes from input-events |
| `processed-events.processed-consumer-group` | 1 | Consumes published events |
| `input-events.event-processor-group.dlq` | 0 | Dead letter queue |

**Click on a queue** to see:
- Ready messages count
- Consumer details
- Get messages (view payload)

### Why You Don't See Messages in Queues

Messages are **consumed immediately** by active consumers. To see messages:

**Option 1: Stop the Consumer**

Temporarily disable `processedEventConsumer`:

```yaml
# In application-rabbitmq.yml
function:
  definition: eventConsumer  # Remove processedEventConsumer
```

Restart app, publish event, then check queue in UI.

**Option 2: Check Application Logs**

The consumer logs every message:
```
✅ CONSUMED PROCESSED EVENT: <id> with type: <type>
✅ Processed payload: {...}
```

**Option 3: Use "Get Messages" in UI**

1. Go to Queues → `processed-events.processed-consumer-group`
2. Scroll to **Get messages** section
3. Set **Messages: 1**, **Ackmode: Automatic**
4. Click **Get Message(s)** quickly after publishing

### Understanding Routing Keys

In RabbitMQ UI, you'll see routing key `#`:
- `#` is a wildcard meaning "match all messages"
- Spring Cloud Stream uses this by default for topic exchanges
- Messages are routed to all bound queues

---

## Troubleshooting

### Issue 1: Queues Not Appearing

**Symptoms:**
- No queues in RabbitMQ UI
- No binding logs on startup

**Solution:**
1. Check `spring.cloud.function.definition` includes your consumers
2. Verify profile is active: Look for `The following 1 profile is active: "rabbitmq"`
3. Check startup logs for:
   ```
   declaring queue for inbound: processed-events.processed-consumer-group
   started bean 'inbound.processed-events.processed-consumer-group'
   ```

### Issue 2: Authentication Failed

**Symptoms:**
```
ACCESS_REFUSED - Login was refused using authentication mechanism PLAIN
```

**Solution:**
- Verify RabbitMQ credentials in `application-rabbitmq.yml` match docker-compose
- Default: `admin/admin` (not `guest/guest`)

### Issue 3: Port Already in Use

**Symptoms:**
```
Web server failed to start. Port 7070 was already in use.
```

**Solution:**
```bash
# Kill existing process
lsof -ti:7070 | xargs kill -9

# Or change port in application-rabbitmq.yml
server:
  port: 8080
```

### Issue 4: Multiple Instances Running

**Symptoms:**
- Unexpected behavior
- Multiple consumers on same queue

**Solution:**
```bash
# Kill all instances
pkill -f "event-processor"

# Restart cleanly
mvn spring-boot:run -Dspring-boot.run.profiles=rabbitmq
```

### Issue 5: Not Seeing Consumer Logs

**Symptoms:**
- "Event published successfully" appears
- No "✅ CONSUMED PROCESSED EVENT" logs

**Solution:**
1. Check RabbitMQ UI → Queue → Consumers count should be 1
2. If 0, restart application
3. Verify function definition includes `processedEventConsumer`

### Debug Commands

```bash
# Check active bindings
curl http://localhost:7070/actuator/bindings | jq

# Check health
curl http://localhost:7070/actuator/health | jq

# View RabbitMQ connections
docker exec rabbitmq rabbitmqctl list_connections

# View queues
docker exec rabbitmq rabbitmqctl list_queues
```

---

## Key Concepts

### 1. Messaging-Agnostic Architecture

**The Power:**
- Same Java code works with Kafka, RabbitMQ, or any other binder
- Switch brokers by changing configuration only
- No code changes in business logic

**What Changes:**
- Dependencies in `pom.xml`
- Configuration in `application-*.yml`
- Infrastructure (Kafka vs RabbitMQ)

**What Stays the Same:**
- `EventStreamProcessor.java` (Consumer/Function beans)
- `EventPublisher.java` (StreamBridge usage)
- `EventController.java` (REST API)
- `Event.java` (Domain model)

### 2. Spring Cloud Stream Concepts

**Binder:**
- Abstraction layer between your code and message broker
- Kafka Binder, RabbitMQ Binder, etc.

**Binding:**
- Connection between your function and a destination (topic/exchange)
- Input binding: Consumes messages
- Output binding: Produces messages

**Destination:**
- Kafka: Topic
- RabbitMQ: Exchange

**Consumer Group:**
- Kafka: Consumer group for load balancing
- RabbitMQ: Queue name suffix

### 3. Functional Programming Model

Spring Cloud Stream uses Java functional interfaces:

```java
// Consumer: Input only, no output
Consumer<Event> eventConsumer()

// Function: Input → Transform → Output
Function<Event, Event> eventProcessor()

// Supplier: No input, produces output (scheduled/polling)
Supplier<Event> eventSupplier()
```

### 4. StreamBridge vs Function Beans

**StreamBridge** (Dynamic Publishing):
```java
streamBridge.send("eventProducer-out-0", event);
```
- Use when: Publishing from REST controllers, services
- Flexible: Send to any destination at runtime

**Function Beans** (Reactive Streams):
```java
@Bean
public Function<Event, Event> processor() { ... }
```
- Use when: Continuous stream processing
- Automatic: Spring binds input/output automatically

---

## Comparison: Kafka vs RabbitMQ

| Aspect | Kafka | RabbitMQ |
|--------|-------|----------|
| **Dependency** | `spring-cloud-stream-binder-kafka` | `spring-cloud-stream-binder-rabbit` |
| **Config File** | `application.yml` | `application-rabbitmq.yml` |
| **Port** | 9092 | 5672 |
| **Management UI** | None (use Kafka Tool) | 15672 |
| **Destination** | Topic | Exchange |
| **Consumer Group** | Consumer Group | Queue |
| **Message Retention** | Configurable (days) | Until consumed |
| **Routing** | Partition-based | Routing key-based |
| **Code Changes** | **NONE** | **NONE** |

---

## Switching Back to Kafka

To switch back to Kafka:

```bash
# Start Kafka
docker run -d --name kafka -p 9092:9092 apache/kafka:latest

# Run with default profile (Kafka)
mvn spring-boot:run

# Or explicitly
mvn spring-boot:run -Dspring-boot.run.profiles=default
```

---

## Best Practices

1. **Use Profiles**: Keep broker-specific configs in separate profiles
2. **Consumer Groups**: Always specify groups for load balancing
3. **Dead Letter Queues**: Enable DLQ for critical consumers
4. **Monitoring**: Use actuator endpoints and broker UIs
5. **Testing**: Test with both brokers in CI/CD pipeline
6. **Documentation**: Document which profile to use in each environment

---

## Summary

✅ **NO CODE CHANGES** - Only configuration and dependencies  
✅ Same REST API works with both Kafka and RabbitMQ  
✅ Same business logic in `EventStreamProcessor`  
✅ Profile-based switching for different environments  
✅ This is the power of **messaging-agnostic architecture**!

---

## Additional Resources

- [Spring Cloud Stream Documentation](https://spring.io/projects/spring-cloud-stream)
- [RabbitMQ Management UI Guide](https://www.rabbitmq.com/management.html)
- [Spring Cloud Function](https://spring.io/projects/spring-cloud-function)

---

**Questions?** Check the troubleshooting section or review application logs with `DEBUG` level enabled.
