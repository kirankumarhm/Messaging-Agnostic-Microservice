# Switching to Google Pub/Sub

This guide shows how to run the event-processor with Google Pub/Sub using the local emulator.

## Prerequisites

- Java 17+
- Maven
- Docker
- gcloud CLI (optional, for production)

## Step 1: Start Pub/Sub Emulator

**Option 1: Using docker-compose-pubsub.yml (Recommended)**

```bash
docker-compose -f docker-compose-pubsub.yml up -d
```

**Verify emulator is running:**
```bash
docker-compose -f docker-compose-pubsub.yml ps
```

**Expected Output:**
```
NAME                STATUS              PORTS
pubsub-emulator     Up                  0.0.0.0:8085->8085/tcp
```

**Option 2: Using Docker directly**

```bash
docker run -d --name pubsub-emulator \
  -p 8085:8085 \
  gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators \
  gcloud beta emulators pubsub start --host-port=0.0.0.0:8085
```

**Option 3: Using gcloud CLI**

```bash
gcloud beta emulators pubsub start --host-port=localhost:8085
```

## Step 2: Build and Run

```bash
mvn clean install
mvn spring-boot:run -Dspring-boot.run.profiles=pubsub
```

## Step 3: Test

```bash
# Publish to processed-events topic
curl -X POST http://localhost:7070/api/events/publish \
  -H "Content-Type: application/json" \
  -d '{"eventType": "USER_CREATED", "source": "test", "payload": {"name": "John"}}'

# Publish to input-events topic
curl -X POST http://localhost:7070/api/events/input \
  -H "Content-Type: application/json" \
  -d '{"eventType": "ORDER_CREATED", "source": "test", "payload": {"orderId": "789"}}'
```

## Expected Logs

```
The following 1 profile is active: "pubsub"
Channel 'event-processor.eventConsumer-in-0' has 1 subscriber(s).
Channel 'event-processor.processedEventConsumer-in-0' has 1 subscriber(s).
✅ CONSUMED PROCESSED EVENT: <id> with type: USER_CREATED
Received event: <id> with type: ORDER_CREATED
```

## Configuration

### Emulator (Local Development)
```yaml
spring:
  cloud:
    gcp:
      project-id: local-project
      pubsub:
        emulator-host: localhost:8085
```

### Production (Real GCP)
```yaml
spring:
  cloud:
    gcp:
      project-id: your-gcp-project-id
      credentials:
        location: classpath:gcp-credentials.json
```

## Key Differences from Kafka/RabbitMQ

| Feature | Kafka | RabbitMQ | Google Pub/Sub |
|---------|-------|----------|----------------|
| Destination | Topic | Exchange | Topic |
| Consumer Group | Consumer Group | Queue | Subscription |
| Local Testing | Docker | Docker | Emulator |
| Port | 9092 | 5672 | 8085 (emulator) |
| Management UI | None | 15672 | GCP Console |

## Troubleshooting

### Emulator not starting
```bash
# Check if port 8085 is available
lsof -i:8085

# View emulator logs
docker logs pubsub-emulator
```

### Topics not created
Topics and subscriptions are auto-created by Spring Cloud Stream on first use.

### Authentication errors
Make sure `emulator-host` is set in application-pubsub.yml for local testing.

## NO CODE CHANGES REQUIRED!

Same Java code works with Kafka, RabbitMQ, and Google Pub/Sub! 🚀
