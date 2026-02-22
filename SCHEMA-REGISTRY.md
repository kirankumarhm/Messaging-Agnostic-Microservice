# Confluent Schema Registry Integration (Optional Advanced Feature)

**Note:** This is an optional advanced feature. The event-processor works perfectly with JSON serialization (default). Use Schema Registry only if you need:
- Schema evolution management
- Smaller message sizes (Avro binary)
- Compile-time type safety
- Breaking change prevention

**Current Status:** ⚠️ Requires additional Spring Kafka configuration (not fully integrated with Spring Cloud Stream StreamBridge)

This guide demonstrates how to use Confluent Schema Registry with Kafka for schema evolution and validation.

## What is Schema Registry?

Schema Registry provides:
- **Schema Versioning**: Track schema changes over time
- **Schema Validation**: Ensure messages conform to defined schemas
- **Schema Evolution**: Handle backward/forward compatibility
- **Centralized Management**: Single source of truth for schemas

## Architecture

```
┌─────────────────┐
│   Producer      │
│  (Event App)    │
└────────┬────────┘
         │ 1. Register schema
         ▼
┌──────────────────────────┐
│ Confluent Schema Registry│
│  (Port 8081)             │
└────────┬─────────────────┘
         │ 2. Return schema ID
         ▼
┌─────────────────┐
│  Message Broker │
│  (Kafka/etc)    │
└────────┬────────┘
         │ 3. Message + schema ID
         ▼
┌─────────────────┐
│   Consumer      │
│  (Event App)    │
└─────────────────┘
```

## Setup

### Step 1: Add Dependencies

Already added to `pom.xml`:
```xml
<!-- Confluent Schema Registry Client -->
<dependency>
    <groupId>io.confluent</groupId>
    <artifactId>kafka-avro-serializer</artifactId>
    <version>7.5.0</version>
</dependency>

<!-- Avro Support -->
<dependency>
    <groupId>org.apache.avro</groupId>
    <artifactId>avro</artifactId>
    <version>1.12.1</version>
</dependency>
```

### Step 2: Define Avro Schema

File: `src/main/resources/avro/event.avsc`
```json
{
  "namespace": "com.example.messaging.stream.avro",
  "type": "record",
  "name": "EventAvro",
  "fields": [
    {
      "name": "eventId",
      "type": "string"
    },
    {
      "name": "eventType",
      "type": "string"
    },
    {
      "name": "source",
      "type": "string"
    },
    {
      "name": "timestamp",
      "type": "long",
      "logicalType": "timestamp-millis"
    },
    {
      "name": "payload",
      "type": {
        "type": "map",
        "values": "string"
      }
    }
  ]
}
```

### Step 3: Generate Avro Classes

```bash
mvn clean compile
```

This generates `EventAvro.java` in `target/generated-sources/avro/`

### Step 4: Start Kafka Cluster with Schema Registry

**Use docker-compose.yml (includes 3-node Kafka + Schema Registry)**
```bash
docker-compose up -d
```

**Verify services are running:**
```bash
docker-compose ps
```

**Expected Output:**
```
NAME                STATUS              PORTS
kafka1              Up                  0.0.0.0:9092->9092/tcp
kafka2              Up                  0.0.0.0:9094->9094/tcp
kafka3              Up                  0.0.0.0:9096->9096/tcp
schema-registry     Up                  0.0.0.0:8081->8081/tcp
kafka-ui            Up                  0.0.0.0:8080->8080/tcp
akhq                Up                  0.0.0.0:8082->8082/tcp
```

**Verify Schema Registry is running:**
```bash
curl http://localhost:8081/subjects
# Expected: [] (empty subjects initially)
```

### Step 5: Start Event Processor with Schema Support

**Terminal: Start Event Processor (Port 7070)**

```bash
# With Kafka + Schema
mvn spring-boot:run -Dspring-boot.run.profiles=kafka,schema
```

**Expected Output:**
```
The following 2 profiles are active: "kafka", "schema"
Schema Registry URL: http://localhost:8081
Started MessagingStreamApplication in 3.5 seconds
Tomcat started on port 7070 (http)
```

## Testing

### Complete Test Flow

#### 1. Verify Schema Registry

```bash
# List all subjects (should be empty initially)
curl http://localhost:8081/subjects
```

**Expected Response:**
```json
[]
```

#### 2. Publish Event (Schema Auto-Registered)

Confluent Schema Registry automatically registers schemas on first publish:

```bash
curl -X POST http://localhost:7070/api/events/publish \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "USER_CREATED",
    "source": "user-service",
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

**Expected Application Logs:**
```
Publishing event: 9ea84487-43df-4c76-9c86-437b9ab3ee82
Event published successfully
✅ CONSUMED PROCESSED EVENT: 9ea84487-43df-4c76-9c86-437b9ab3ee82
```

#### 3. Verify Schema Registration

```bash
# List all subjects
curl http://localhost:8081/subjects
```

**Expected Response:**
```json
["processed-events-value", "input-events-value"]
```

#### 4. Get Schema Details

```bash
# Get latest schema for subject
curl http://localhost:8081/subjects/processed-events-value/versions/latest
```

**Expected Response:**
```json
{
  "subject": "processed-events-value",
  "version": 1,
  "id": 1,
  "schema": "{...avro schema...}"
}
```

#### 5. Test Schema Validation (Invalid Event)

```bash
# Try to publish event with missing required field
curl -X POST http://localhost:7070/api/events/publish \
  -H "Content-Type: application/json" \
  -d '{
    "source": "user-service",
    "payload": {}
  }'
```

**Expected Response:**
```json
{
  "status": "error",
  "message": "Schema validation failed: Missing required field 'eventType'"
}
```

## Configuration Files Explained

### application-schema.yml (Event Processor with Schema)

**Purpose**: Configuration for Event Processor with schema validation  
**Port**: 7070  
**Profile**: `schema` (combine with broker profile)

```yaml
server:
  port: 7070

spring:
  application:
    name: event-processor
  
  cloud:
    function:
      definition: eventConsumer;processedEventConsumer
    
    stream:
      kafka:
        binder:
          brokers: localhost:9092,localhost:9094,localhost:9096
          configuration:
            schema.registry.url: http://localhost:8081
      
      bindings:
        eventProducer-out-0:
          destination: processed-events
          producer:
            configuration:
              key.serializer: org.apache.kafka.common.serialization.StringSerializer
              value.serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
        
        eventConsumer-in-0:
          destination: input-events
          group: event-processor-group
          consumer:
            configuration:
              key.deserializer: org.apache.kafka.common.serialization.StringDeserializer
              value.deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
              specific.avro.reader: true
        
        processedEventConsumer-in-0:
          destination: processed-events
          group: processed-consumer-group
          consumer:
            configuration:
              key.deserializer: org.apache.kafka.common.serialization.StringDeserializer
              value.deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
              specific.avro.reader: true
```

## Configuration Explained

```yaml
spring:
  cloud:
    stream:
      kafka:
        binder:
          configuration:
            schema.registry.url: http://localhost:8081  # Confluent Schema Registry
      
      bindings:
        eventProducer-out-0:
          producer:
            configuration:
              value.serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
```

## Quick Start Guide

### Complete Setup in 2 Steps

**Step 1: Start Kafka Cluster + Schema Registry**
```bash
docker-compose up -d
```

**Step 2: Start Event Processor**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=kafka,schema
```

### Test the Setup

```bash
# 1. Publish event (schema auto-registered)
curl -X POST http://localhost:7070/api/events/publish \
  -H "Content-Type: application/json" \
  -d '{"eventType":"USER_CREATED","source":"test","payload":{"userId":"123"}}'

# 2. Verify schema registered
curl http://localhost:8081/subjects

# 3. Check logs
# Should see: "✅ CONSUMED PROCESSED EVENT"
```

## Schema Evolution

### Backward Compatible Changes (Safe)

✅ Add optional field with default:
```json
{
  "name": "email",
  "type": ["null", "string"],
  "default": null
}
```

✅ Remove field (consumers ignore unknown fields)

### Forward Compatible Changes

✅ Add new field (old consumers ignore it)

### Breaking Changes (Avoid)

❌ Remove required field  
❌ Change field type  
❌ Rename field  

## Confluent Schema Registry REST API

### Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/subjects` | List all subjects |
| GET | `/subjects/{subject}/versions` | List versions for subject |
| GET | `/subjects/{subject}/versions/latest` | Get latest schema |
| POST | `/subjects/{subject}/versions` | Register new schema |
| DELETE | `/subjects/{subject}/versions/{version}` | Delete schema version |

### Example: Register Schema Manually

```bash
curl -X POST http://localhost:8081/subjects/my-topic-value/versions \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d '{
    "schema": "{\"type\":\"record\",\"name\":\"MyEvent\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}"
  }'
```

## Benefits

✅ **Type Safety**: Compile-time validation  
✅ **Schema Evolution**: Manage changes safely  
✅ **Smaller Messages**: Binary format (Avro)  
✅ **Documentation**: Schema serves as contract  
✅ **Compatibility Checks**: Prevent breaking changes  

## Comparison: JSON vs Avro

| Feature | JSON | Avro |
|---------|------|------|
| **Size** | Larger | Smaller (binary) |
| **Speed** | Slower | Faster |
| **Schema** | Optional | Required |
| **Evolution** | Manual | Managed |
| **Type Safety** | Runtime | Compile-time |

## Troubleshooting

### Schema Registry not starting

```bash
# Check Docker container
docker-compose logs schema-registry

# Check port
lsof -i:8081

# Restart
docker-compose restart schema-registry
```

### Schema not found

```bash
# Verify schema registered
curl http://localhost:8081/subjects

# Check application logs for schema registration
```

### Serialization errors

Enable debug logging:
```yaml
logging:
  level:
    org.springframework.cloud.stream.schema: DEBUG
```

## Production Configuration

```yaml
spring:
  cloud:
    stream:
      kafka:
        binder:
          configuration:
            schema.registry.url: http://schema-registry.prod:8081
            auto.register.schemas: true
            use.latest.version: true
            max.schemas.per.subject: 1000
```

## Kafka-Specific Feature

Confluent Schema Registry is designed for Kafka:
- ✅ Industry standard for Kafka
- ✅ Production-ready and battle-tested
- ✅ Rich ecosystem (Kafka Connect, ksqlDB)
- ✅ Schema evolution with compatibility checks

For other brokers (RabbitMQ, Pulsar, Pub/Sub), use broker-native schema features or JSON.

## Additional Resources

- [Confluent Schema Registry Docs](https://docs.confluent.io/platform/current/schema-registry/index.html)
- [Apache Avro Documentation](https://avro.apache.org/docs/)
- [Schema Evolution Best Practices](https://docs.confluent.io/platform/current/schema-registry/avro.html)
- [Kafka Avro Serializer](https://docs.confluent.io/platform/current/schema-registry/serdes-develop/serdes-avro.html)

## Summary

🎯 **Confluent Schema Registry is industry standard for Kafka**  
📝 **Avro provides efficient binary serialization**  
🔄 **Schema evolution enables safe changes**  
✅ **Auto-registration simplifies development**  
🐳 **Docker Compose makes setup easy**  

**Kafka + Confluent Schema Registry = Production-ready event-driven architecture!** 🚀
