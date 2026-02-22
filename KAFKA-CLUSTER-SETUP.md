# Kafka Cluster with Schema Registry - Docker Compose Setup

This guide shows how to run a 3-node Kafka cluster with Spring Cloud Stream Schema Registry.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Kafka Cluster                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐             │
│  │ Kafka-1  │  │ Kafka-2  │  │ Kafka-3  │             │
│  │ :9092    │  │ :9094    │  │ :9096    │             │
│  └──────────┘  └──────────┘  └──────────┘             │
└─────────────────────────────────────────────────────────┘
           │                    │
           ▼                    ▼
┌──────────────────┐  ┌──────────────────┐
│ Schema Registry  │  │  Event Processor │
│    :8990         │  │     :7070        │
└──────────────────┘  └──────────────────┘
           │                    │
           ▼                    ▼
┌──────────────────┐  ┌──────────────────┐
│   Kafka UI       │  │      AKHQ        │
│    :8080         │  │     :8082        │
└──────────────────┘  └──────────────────┘
```

## Services

| Service | Port | Description |
|---------|------|-------------|
| kafka-1 | 9092, 9093 | Kafka broker 1 |
| kafka-2 | 9094, 9095 | Kafka broker 2 |
| kafka-3 | 9096, 9097 | Kafka broker 3 |
| schema-registry | 8990 | Schema Registry Server |
| kafka-ui | 8080 | Kafka UI (Provectus) |
| akhq | 8082 | AKHQ Kafka Manager |

## Quick Start

### 1. Start All Services

```bash
# Start Kafka cluster + Schema Registry
docker-compose up -d

# Check all services are running
docker-compose ps
```

**Expected Output:**
```
NAME              STATUS    PORTS
kafka-1           Up        0.0.0.0:9092->9092/tcp, 0.0.0.0:9093->9093/tcp
kafka-2           Up        0.0.0.0:9094->9092/tcp, 0.0.0.0:9095->9093/tcp
kafka-3           Up        0.0.0.0:9096->9092/tcp, 0.0.0.0:9097->9093/tcp
schema-registry   Up        0.0.0.0:8990->8990/tcp
kafka-ui          Up        0.0.0.0:8080->8080/tcp
akhq              Up        0.0.0.0:8082->8080/tcp
```

### 2. Verify Services

```bash
# Check Kafka cluster
curl http://localhost:8080

# Check Schema Registry
curl http://localhost:8990/schemas

# Check AKHQ
curl http://localhost:8082
```

### 3. Start Event Processor

```bash
# Update application.yml to use cluster
mvn spring-boot:run -Dspring-boot.run.profiles=kafka,schema
```

## Configuration

### Application Configuration for Kafka Cluster

Update `src/main/resources/application.yml`:

```yaml
spring:
  cloud:
    stream:
      kafka:
        binder:
          brokers: localhost:9092,localhost:9094,localhost:9096  # All 3 brokers
          replication-factor: 3
          min-partition-count: 3
          auto-create-topics: true
          auto-add-partitions: true
```

### Schema Registry Configuration

Update `src/main/resources/application-schema.yml`:

```yaml
spring:
  cloud:
    schema-registry-client:
      endpoint: http://localhost:8990  # Schema Registry in Docker
      cached: true
```

## Testing

### 1. Register Schema

```bash
curl -X POST http://localhost:8990/schemas \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "event",
    "format": "avro",
    "definition": "{\"namespace\":\"com.example.messaging.stream.avro\",\"type\":\"record\",\"name\":\"EventAvro\",\"fields\":[{\"name\":\"eventId\",\"type\":\"string\"},{\"name\":\"eventType\",\"type\":\"string\"},{\"name\":\"source\",\"type\":\"string\"},{\"name\":\"timestamp\",\"type\":\"long\"},{\"name\":\"payload\",\"type\":{\"type\":\"map\",\"values\":\"string\"}}]}"
  }'
```

### 2. Publish Event

```bash
curl -X POST http://localhost:7070/api/events/publish \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "USER_CREATED",
    "source": "user-service",
    "payload": {
      "userId": "123",
      "username": "john.doe"
    }
  }'
```

### 3. Verify in Kafka UI

Open http://localhost:8080 and check:
- Topics: `processed-events`, `input-events`
- Messages: View published events
- Consumer Groups: `event-processor-group`

### 4. Verify in AKHQ

Open http://localhost:8082 and check:
- Cluster health
- Topic partitions (should be 3)
- Replication factor (should be 3)

## Monitoring

### Kafka Cluster Health

```bash
# Check cluster status
docker exec kafka-1 kafka-broker-api-versions --bootstrap-server localhost:9092

# List topics
docker exec kafka-1 kafka-topics --list --bootstrap-server localhost:9092

# Describe topic
docker exec kafka-1 kafka-topics --describe --topic processed-events \
  --bootstrap-server localhost:9092
```

**Expected Output:**
```
Topic: processed-events
PartitionCount: 3
ReplicationFactor: 3
Partition: 0  Leader: 1  Replicas: 1,2,3  Isr: 1,2,3
Partition: 1  Leader: 2  Replicas: 2,3,1  Isr: 2,3,1
Partition: 2  Leader: 3  Replicas: 3,1,2  Isr: 3,1,2
```

### Schema Registry Health

```bash
# List all schemas
curl http://localhost:8990/schemas

# Check specific schema
curl http://localhost:8990/schemas/event/avro/v1
```

## Logs

```bash
# View Schema Registry logs
docker logs schema-registry -f

# View Kafka broker logs
docker logs kafka-1 -f
docker logs kafka-2 -f
docker logs kafka-3 -f

# View all logs
docker-compose logs -f
```

## Stopping Services

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v

# Remove data directories
rm -rf data/ logs/
```

## Production Considerations

### 1. Persistent Storage

Data is stored in:
- `./data/kafka-1/` - Kafka 1 data
- `./data/kafka-2/` - Kafka 2 data
- `./data/kafka-3/` - Kafka 3 data
- `./logs/kafka-*/` - Kafka logs

### 2. Resource Limits

Add to docker-compose.yml:

```yaml
services:
  kafka-1:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G
```

### 3. Security

For production, add:
- SSL/TLS encryption
- SASL authentication
- ACLs for authorization

### 4. Monitoring

Add Prometheus + Grafana:

```yaml
  prometheus:
    image: prom/prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
  
  grafana:
    image: grafana/grafana
    ports:
      - "3000:3000"
```

## Troubleshooting

### Schema Registry not starting

```bash
# Check logs
docker logs schema-registry

# Rebuild image
docker-compose build schema-registry
docker-compose up -d schema-registry
```

### Kafka cluster not forming

```bash
# Check controller quorum
docker exec kafka-1 kafka-metadata-quorum --bootstrap-server localhost:9092 describe --status

# Check broker logs
docker logs kafka-1 | grep -i "controller"
```

### Connection refused

```bash
# Check if services are running
docker-compose ps

# Check network
docker network inspect event-processor_kafka-network

# Restart services
docker-compose restart
```

## Alternative: Run Schema Registry Locally

If you prefer not to use Docker for Schema Registry:

```bash
# Terminal 1: Start Kafka cluster only
docker-compose up -d kafka-1 kafka-2 kafka-3 kafka-ui akhq

# Terminal 2: Run Schema Registry locally
mvn spring-boot:run \
  -Dspring-boot.run.main-class=com.example.messaging.schema.SchemaRegistryServerApplication \
  -Dspring-boot.run.profiles=schema-server

# Terminal 3: Run Event Processor
mvn spring-boot:run -Dspring-boot.run.profiles=kafka,schema
```

## Summary

✅ **3-node Kafka cluster** with replication factor 3  
✅ **Schema Registry** for schema management  
✅ **Kafka UI** for visual monitoring  
✅ **AKHQ** for advanced Kafka management  
✅ **Production-ready** configuration  

**All services in one command: `docker-compose up -d`** 🚀
