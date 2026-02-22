# Switching to Apache Pulsar

This guide shows how to run the event-processor with Apache Pulsar.

## What is Apache Pulsar?

Apache Pulsar is a cloud-native, distributed messaging and streaming platform:
- Multi-tenancy support
- Geo-replication
- Built-in schema registry
- Tiered storage
- Similar to Kafka but with more features

## Step 1: Start Apache Pulsar

**Use docker-compose-pulsar.yml:**

```bash
docker-compose -f docker-compose-pulsar.yml up -d
```

**Verify Pulsar is running:**
```bash
docker-compose -f docker-compose-pulsar.yml ps
```

**Expected Output:**
```
NAME                STATUS              PORTS
pulsar              Up                  0.0.0.0:6650->6650/tcp, 0.0.0.0:8080->8080/tcp
```

Wait ~30 seconds for Pulsar to fully initialize.

**Ports:**
- `6650` - Pulsar broker (binary protocol)
- `8080` - Pulsar admin API & Web UI

**Access Admin UI:** http://localhost:8080

## Step 2: Verify Pulsar is Running

Wait 30-60 seconds after starting Pulsar, then verify:

```bash
# Check if Pulsar is ready
docker logs pulsar | grep "messaging service is ready"

# Check ports
lsof -i:6650  # Broker port
lsof -i:8080  # Admin/Web UI port
```

## Step 3: Build and Run

```bash
mvn clean install
mvn spring-boot:run -Dspring-boot.run.profiles=pulsar
```

**Expected Startup Logs:**
```
The following 1 profile is active: "pulsar"
Creating binder: pulsar
Connected to server [localhost/127.0.0.1:6650]
Topic 'persistent://public/default/input-events' does not yet exist - will add
Creating topics: persistent://public/default/input-events
Subscribed to topic persistent://public/default/input-events
Topic 'persistent://public/default/processed-events' does not yet exist - will add
Creating topics: persistent://public/default/processed-events
Subscribed to topic persistent://public/default/processed-events
Channel 'event-processor.eventConsumer-in-0' has 1 subscriber(s).
Channel 'event-processor.processedEventConsumer-in-0' has 1 subscriber(s).
Started MessagingStreamApplication in 3.153 seconds
```

## Step 4: Test

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

**Expected Response:**
```json
{
  "status": "success",
  "eventId": "11889297-239e-4bbb-ac1c-7b5042500804",
  "message": "Event published successfully"
}
```

**Expected Application Logs:**
```
Publishing event: 11889297-239e-4bbb-ac1c-7b5042500804 to binding: eventProducer-out-0
Event published successfully: 11889297-239e-4bbb-ac1c-7b5042500804
✅ CONSUMED PROCESSED EVENT: 11889297-239e-4bbb-ac1c-7b5042500804 with type: USER_CREATED
✅ Processed payload: {name=John}
```

## Configuration Explained

File: `src/main/resources/application-pulsar.yml`

```yaml
spring:
  application:
    name: event-processor
  
  # Pulsar client configuration
  pulsar:
    client:
      service-url: pulsar://localhost:6650  # Binary protocol for producers/consumers
    admin:
      service-url: http://localhost:8080    # HTTP API for admin operations
  
  # Spring Cloud Stream configuration
  cloud:
    function:
      definition: eventConsumer;processedEventConsumer  # Auto-start these consumers
    stream:
      default-binder: pulsar  # Use Pulsar binder when multiple binders present
      bindings:
        eventProducer-out-0:
          destination: processed-events  # Pulsar topic name
          content-type: application/json
          binder: pulsar
        eventConsumer-in-0:
          destination: input-events
          content-type: application/json
          group: event-processor-group  # Subscription name
          binder: pulsar
        processedEventConsumer-in-0:
          destination: processed-events
          content-type: application/json
          group: processed-consumer-group
          binder: pulsar

server:
  port: 7070

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,bindings
```

### Key Configuration Points

1. **service-url**: `pulsar://localhost:6650` - Binary protocol for message production/consumption
2. **admin-url**: `http://localhost:8080` - REST API for topic management
3. **default-binder**: `pulsar` - Required when multiple binders in classpath
4. **destination**: Maps to Pulsar topic (format: `persistent://public/default/<topic-name>`)
5. **group**: Maps to Pulsar subscription name

## Monitoring with Pulsar Admin UI

1. Go to http://localhost:8080
2. Navigate to **Topics** section
3. You'll see:
   - `persistent://public/default/processed-events`
   - `persistent://public/default/input-events`
4. Click on a topic to see:
   - Message rates
   - Subscriptions
   - Storage size

## Pulsar CLI Commands

```bash
# List topics
docker exec -it pulsar bin/pulsar-admin topics list public/default

# View topic stats
docker exec -it pulsar bin/pulsar-admin topics stats persistent://public/default/processed-events

# Consume messages
docker exec -it pulsar bin/pulsar-client consume persistent://public/default/processed-events -s test-sub -n 0

# Produce test message
docker exec -it pulsar bin/pulsar-client produce persistent://public/default/input-events -m "test message"
```

## View Messages in Pulsar

```bash
# Consume from processed-events topic
docker exec -it pulsar bin/pulsar-client consume \
  persistent://public/default/processed-events \
  -s test-subscription \
  -n 0

# Consume from input-events topic
docker exec -it pulsar bin/pulsar-client consume \
  persistent://public/default/input-events \
  -s test-subscription \
  -n 0
```


## Comparison: Kafka vs RabbitMQ vs Pulsar vs Pub/Sub

| Feature | Kafka | RabbitMQ | Apache Pulsar | Google Pub/Sub |
|---------|-------|----------|---------------|----------------|
| **Architecture** | Distributed log | Message queue | Unified messaging | Cloud Pub/Sub |
| **Destination** | Topic | Exchange | Topic | Topic |
| **Consumer Group** | Consumer Group | Queue | Subscription | Subscription |
| **Port** | 9092 | 5672 | 6650 | 8085 (emulator) |
| **Admin UI** | ❌ | ✅ (15672) | ✅ (8080) | ✅ (GCP Console) |
| **Multi-tenancy** | ❌ | ❌ | ✅ | ✅ |
| **Geo-replication** | Complex | ❌ | ✅ Built-in | ✅ Built-in |
| **Schema Registry** | Separate | ❌ | ✅ Built-in | ✅ Built-in |
| **Tiered Storage** | ❌ | ❌ | ✅ | ✅ |
| **Message TTL** | ✅ | ✅ | ✅ | ✅ |
| **Local Testing** | Docker | Docker | Docker | Emulator |
| **Code Changes** | **NONE** | **NONE** | **NONE** | **NONE** |

## Pulsar Concepts

### Topics
- Format: `persistent://tenant/namespace/topic`
- Default: `persistent://public/default/<topic-name>`

### Subscriptions (Consumer Groups)
- **Exclusive**: Only one consumer
- **Shared**: Load balanced across consumers
- **Failover**: One active, others standby
- **Key_Shared**: Messages with same key go to same consumer

### Tenants & Namespaces
- **Tenant**: Top-level isolation (like AWS account)
- **Namespace**: Group of topics (like database)
- Default: `public/default`

## Troubleshooting

### Pulsar not starting
```bash
# Check logs
docker logs pulsar

# Restart
docker restart pulsar
```

### Topics not appearing
Topics are auto-created on first publish. Check:
```bash
docker exec -it pulsar bin/pulsar-admin topics list public/default
```

### Connection refused

**Symptoms:**
```
org.apache.pulsar.client.api.PulsarClientException$ConnectException: 
  [localhost/127.0.0.1:6650] Failed to connect to localhost/127.0.0.1:6650
```

**Solution:**
Wait 30-60 seconds after starting Pulsar. It takes time to initialize.

```bash
# Check if Pulsar is ready
docker logs pulsar | tail -20

# Look for: "messaging service is ready"
```

### Port conflicts
```bash
# Check if ports are in use
lsof -i:6650
lsof -i:8080
```

### Application not consuming messages

**Symptoms:**
- "Event published successfully" appears
- No "✅ CONSUMED PROCESSED EVENT" logs

**Solution:**
1. Check function definition includes both consumers:
   ```yaml
   function:
     definition: eventConsumer;processedEventConsumer
   ```
2. Verify subscriptions in Pulsar:
   ```bash
   docker exec -it pulsar bin/pulsar-admin topics stats \
     persistent://public/default/processed-events
   ```
3. Check for errors in application logs

### Topics not appearing

**Solution:**
Topics are auto-created on first publish. Verify:
```bash
docker exec -it pulsar bin/pulsar-admin topics list public/default
```

Expected output:
```
persistent://public/default/input-events
persistent://public/default/processed-events
```

## Production Configuration

For production, use Pulsar cluster:

```yaml
spring:
  pulsar:
    client:
      service-url: pulsar://pulsar-broker-1:6650,pulsar-broker-2:6650,pulsar-broker-3:6650
    admin:
      service-url: http://pulsar-admin:8080
    producer:
      send-timeout: 30s
      batching-enabled: true
    consumer:
      subscription-type: shared  # Load balancing
      ack-timeout: 30s
```

## Why Choose Pulsar?

✅ **Unified messaging** - Streaming + queuing in one platform  
✅ **Multi-tenancy** - Isolate teams/applications with tenants/namespaces  
✅ **Geo-replication** - Built-in cross-datacenter replication  
✅ **Tiered storage** - Offload old data to S3/GCS/Azure automatically  
✅ **Schema evolution** - Built-in schema registry with compatibility checks  
✅ **Serverless functions** - Process messages with Pulsar Functions  
✅ **Better than Kafka** - All Kafka features + more, without complexity  
✅ **Cloud-native** - Designed for Kubernetes and cloud deployments

## Message Flow Diagram

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
│  Spring Cloud Stream        │
│  Pulsar Binder              │
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│  Apache Pulsar              │
│  Topic: processed-events    │
└──────┬──────────────────────┘
       │
       ▼
┌──────────────────────────────────────┐
│  Subscription:                       │
│  processed-consumer-group            │
└──────┬───────────────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│  processedEventConsumer()   │
│  Consumer Function          │
└─────────────────────────────┘
```

## Switching Between Brokers

```bash
# Kafka (default)
mvn spring-boot:run

# RabbitMQ
mvn spring-boot:run -Dspring-boot.run.profiles=rabbitmq

# Apache Pulsar
mvn spring-boot:run -Dspring-boot.run.profiles=pulsar

# Google Pub/Sub
mvn spring-boot:run -Dspring-boot.run.profiles=pubsub
```

**Same Java code, same REST API, different message broker!**

## NO CODE CHANGES REQUIRED!

✅ Same `EventStreamProcessor.java`  
✅ Same `EventPublisher.java`  
✅ Same `EventController.java`  
✅ Same `Event.java`  
✅ **ALL BUSINESS LOGIC UNCHANGED**  

This is the power of **messaging-agnostic architecture** with Spring Cloud Stream! 🚀

## Additional Resources

- [Apache Pulsar Documentation](https://pulsar.apache.org/docs/)
- [Spring Cloud Stream Pulsar Binder](https://docs.spring.io/spring-cloud-stream/reference/pulsar/pulsar_binder.html)
- [Pulsar Concepts](https://pulsar.apache.org/docs/concepts-overview/)
- [Spring Pulsar](https://docs.spring.io/spring-pulsar/reference/)

## Summary

🎯 **Verified Working**: Apache Pulsar successfully integrated  
🔄 **Zero Code Changes**: Same Java code as Kafka/RabbitMQ/Pub/Sub  
⚡ **Auto-Configuration**: Topics and subscriptions created automatically  
📊 **Built-in Monitoring**: Web UI at http://localhost:8080  
🚀 **Production Ready**: Supports clustering, geo-replication, tiered storage
