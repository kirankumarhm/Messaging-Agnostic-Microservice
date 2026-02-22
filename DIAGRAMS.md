# Architecture Diagrams

This document contains Mermaid diagrams for the Event Processor messaging-agnostic microservice.

## 1. High-Level Architecture Diagram

```mermaid
graph TB
    subgraph "Client Layer"
        REST[REST API Client]
    end
    
    subgraph "Application Layer"
        Controller[EventController<br/>REST Endpoints]
        Publisher[EventPublisher<br/>StreamBridge]
        Processor[EventStreamProcessor<br/>Consumer Functions]
    end
    
    subgraph "Spring Cloud Stream Layer"
        Binder[Spring Cloud Stream Binder<br/>Abstraction Layer]
    end
    
    subgraph "Message Brokers"
        Kafka[Apache Kafka<br/>Port 9092]
        RabbitMQ[RabbitMQ<br/>Port 5672]
        Pulsar[Apache Pulsar<br/>Port 6650]
        PubSub[Google Pub/Sub<br/>Port 8085]
    end
    
    REST -->|HTTP POST| Controller
    Controller --> Publisher
    Publisher --> Binder
    Binder -.->|Profile: default| Kafka
    Binder -.->|Profile: rabbitmq| RabbitMQ
    Binder -.->|Profile: pulsar| Pulsar
    Binder -.->|Profile: pubsub| PubSub
    
    Kafka --> Binder
    RabbitMQ --> Binder
    Pulsar --> Binder
    PubSub --> Binder
    Binder --> Processor
    
    style Binder fill:#e1f5ff
    style Kafka fill:#ffe1e1
    style RabbitMQ fill:#ffe1e1
    style Pulsar fill:#ffe1e1
    style PubSub fill:#ffe1e1
```

## 2. Component Diagram

```mermaid
graph LR
    subgraph "Event Processor Application"
        subgraph "Controller Layer"
            EC[EventController]
        end
        
        subgraph "Service Layer"
            EP[EventPublisher]
        end
        
        subgraph "Stream Layer"
            ESP[EventStreamProcessor]
            EC_FUNC[eventConsumer]
            PEC_FUNC[processedEventConsumer]
        end
        
        subgraph "Model Layer"
            EVENT[Event Model]
        end
        
        subgraph "Spring Cloud Stream"
            SB[StreamBridge]
            BINDER[Message Binder]
        end
    end
    
    EC -->|uses| EP
    EP -->|uses| SB
    EP -->|uses| EVENT
    SB -->|sends to| BINDER
    
    BINDER -->|consumes from| EC_FUNC
    BINDER -->|consumes from| PEC_FUNC
    
    EC_FUNC -->|part of| ESP
    PEC_FUNC -->|part of| ESP
    ESP -->|uses| EVENT
    
    BINDER -.->|connects to| MB[Message Broker]
    
    style EC fill:#b3d9ff
    style EP fill:#b3d9ff
    style ESP fill:#b3d9ff
    style EVENT fill:#d9f2d9
    style SB fill:#ffe6cc
    style BINDER fill:#ffe6cc
```

## 3. Sequence Diagram - Publish Event

```mermaid
sequenceDiagram
    participant Client
    participant EventController
    participant EventPublisher
    participant StreamBridge
    participant Binder
    participant Broker as Message Broker<br/>(Kafka/RabbitMQ/Pulsar/PubSub)
    
    Client->>+EventController: POST /api/events/publish
    Note over Client,EventController: JSON Event Payload
    
    EventController->>+EventPublisher: publishEvent(event)
    EventPublisher->>EventPublisher: Generate UUID
    EventPublisher->>EventPublisher: Set timestamp
    
    EventPublisher->>+StreamBridge: send("eventProducer-out-0", event)
    StreamBridge->>+Binder: serialize & route
    
    alt Kafka Profile
        Binder->>Broker: Publish to Kafka Topic
    else RabbitMQ Profile
        Binder->>Broker: Publish to RabbitMQ Exchange
    else Pulsar Profile
        Binder->>Broker: Publish to Pulsar Topic
    else Pub/Sub Profile
        Binder->>Broker: Publish to Pub/Sub Topic
    end
    
    Binder-->>-StreamBridge: ACK
    StreamBridge-->>-EventPublisher: Success
    EventPublisher-->>-EventController: Event ID
    EventController-->>-Client: 200 OK + Event ID
    
    Note over Client,Broker: Event published successfully
```

## 4. Sequence Diagram - Consume Event

```mermaid
sequenceDiagram
    participant Broker as Message Broker<br/>(Kafka/RabbitMQ/Pulsar/PubSub)
    participant Binder
    participant Container as Message Listener<br/>Container
    participant Processor as EventStreamProcessor
    participant Logger
    
    Broker->>+Binder: New message available
    
    alt Kafka
        Note over Broker,Binder: Poll from Kafka Topic
    else RabbitMQ
        Note over Broker,Binder: Consume from Queue
    else Pulsar
        Note over Broker,Binder: Receive from Subscription
    else Pub/Sub
        Note over Broker,Binder: Pull from Subscription
    end
    
    Binder->>Binder: Deserialize message
    Binder->>+Container: Dispatch to consumer
    
    Container->>+Processor: eventConsumer(event)
    Processor->>Processor: Validate event
    Processor->>+Logger: Log event details
    Logger-->>-Processor: Logged
    Processor->>Processor: Process business logic
    Processor-->>-Container: Processing complete
    
    Container-->>-Binder: ACK message
    Binder-->>-Broker: Commit/ACK
    
    Note over Broker,Logger: Event consumed successfully
```

## 5. Sequence Diagram - With Schema Registry

```mermaid
sequenceDiagram
    participant Client
    participant App as Event Processor
    participant Registry as Schema Registry<br/>(Port 8990)
    participant Broker as Message Broker
    participant Consumer
    
    Note over Client,Consumer: First Time - Schema Registration
    
    Client->>+App: POST /api/events/publish
    App->>+Registry: Check schema exists
    
    alt Schema not found
        Registry-->>App: 404 Not Found
        App->>Registry: Register schema
        Registry->>Registry: Store schema v1
        Registry-->>App: Schema ID: 1
    else Schema exists
        Registry-->>-App: Schema ID: 1
    end
    
    App->>App: Serialize with Avro<br/>(Schema ID: 1)
    App->>+Broker: Publish message<br/>(binary + schema ID)
    Broker-->>-App: ACK
    App-->>-Client: 200 OK
    
    Note over Client,Consumer: Consumer Side
    
    Broker->>+Consumer: New message<br/>(binary + schema ID: 1)
    Consumer->>+Registry: Get schema by ID: 1
    Registry-->>-Consumer: Schema definition
    Consumer->>Consumer: Deserialize with Avro
    Consumer->>Consumer: Process event
    Consumer-->>-Broker: ACK
    
    Note over Client,Consumer: Schema cached for future use
```

## 6. Deployment Diagram

```mermaid
graph TB
    subgraph "Docker Host"
        subgraph "Message Brokers (Choose One)"
            K[Kafka Container<br/>Port 9092]
            R[RabbitMQ Container<br/>Port 5672, 15672]
            P[Pulsar Container<br/>Port 6650, 8080]
            PS[Pub/Sub Emulator<br/>Port 8085]
        end
        
        subgraph "Schema Registry (Optional)"
            SR[Schema Registry Server<br/>Port 8990]
        end
    end
    
    subgraph "Application Server"
        APP[Event Processor<br/>Port 7070<br/>Spring Boot App]
    end
    
    subgraph "Monitoring"
        ACT[Actuator Endpoints<br/>/actuator/health<br/>/actuator/metrics]
        UI_R[RabbitMQ UI<br/>Port 15672]
        UI_P[Pulsar UI<br/>Port 8080]
    end
    
    APP -.->|Profile: kafka| K
    APP -.->|Profile: rabbitmq| R
    APP -.->|Profile: pulsar| P
    APP -.->|Profile: pubsub| PS
    
    APP -->|Schema validation| SR
    APP --> ACT
    R --> UI_R
    P --> UI_P
    
    style K fill:#ffe6e6
    style R fill:#ffe6e6
    style P fill:#ffe6e6
    style PS fill:#ffe6e6
    style SR fill:#e6f3ff
    style APP fill:#e6ffe6
```

## 7. State Diagram - Message Processing

```mermaid
stateDiagram-v2
    [*] --> Received: Message arrives
    
    Received --> Deserializing: Binder processes
    
    Deserializing --> SchemaValidation: With Schema Registry
    Deserializing --> Validated: Without Schema Registry
    
    SchemaValidation --> Validated: Schema valid
    SchemaValidation --> Failed: Schema invalid
    
    Validated --> Processing: Consumer function
    
    Processing --> Processed: Success
    Processing --> Failed: Exception thrown
    
    Processed --> Acknowledged: ACK to broker
    Failed --> DeadLetterQueue: DLQ enabled
    Failed --> Redelivery: Retry enabled
    
    Redelivery --> Processing: Retry attempt
    DeadLetterQueue --> [*]
    Acknowledged --> [*]
    
    note right of SchemaValidation
        Schema Registry validates
        message against registered schema
    end note
    
    note right of DeadLetterQueue
        Failed messages sent to DLQ
        for manual inspection
    end note
```

## 8. Class Diagram

```mermaid
classDiagram
    class Event {
        -String eventId
        -String eventType
        -String source
        -LocalDateTime timestamp
        -Map~String,Object~ payload
        +getEventId() String
        +setEventId(String)
    }
    
    class EventController {
        -EventPublisher eventPublisher
        +publishEvent(Event) ResponseEntity
        +publishToInputEvents(Event) ResponseEntity
    }
    
    class EventPublisher {
        -StreamBridge streamBridge
        +publishEvent(Event) String
        +publishToInputEvents(Event) String
    }
    
    class EventStreamProcessor {
        +eventConsumer() Consumer~Event~
        +processedEventConsumer() Consumer~Event~
        +eventProcessor() Function~Event,Event~
    }
    
    class StreamBridge {
        <<Spring Cloud Stream>>
        +send(String, Object) boolean
    }
    
    class MessageBinder {
        <<Interface>>
        +bindProducer()
        +bindConsumer()
    }
    
    class KafkaBinder {
        +bindProducer()
        +bindConsumer()
    }
    
    class RabbitBinder {
        +bindProducer()
        +bindConsumer()
    }
    
    class PulsarBinder {
        +bindProducer()
        +bindConsumer()
    }
    
    EventController --> EventPublisher: uses
    EventController --> Event: uses
    EventPublisher --> StreamBridge: uses
    EventPublisher --> Event: uses
    EventStreamProcessor --> Event: consumes
    StreamBridge --> MessageBinder: delegates to
    MessageBinder <|-- KafkaBinder: implements
    MessageBinder <|-- RabbitBinder: implements
    MessageBinder <|-- PulsarBinder: implements
```

## 9. Data Flow Diagram

```mermaid
flowchart LR
    subgraph "Input"
        HTTP[HTTP Request<br/>JSON Payload]
    end
    
    subgraph "Application Processing"
        Controller[EventController<br/>Validate & Route]
        Publisher[EventPublisher<br/>Enrich Event]
        Bridge[StreamBridge<br/>Send to Binding]
    end
    
    subgraph "Spring Cloud Stream"
        Binding[Output Binding<br/>eventProducer-out-0]
        Binder[Message Binder<br/>Serialize & Convert]
    end
    
    subgraph "Message Broker"
        Topic[Topic/Exchange/Queue<br/>processed-events]
    end
    
    subgraph "Consumer Processing"
        InBinding[Input Binding<br/>processedEventConsumer-in-0]
        Deserialize[Deserialize<br/>JSON/Avro to Event]
        Consumer[Consumer Function<br/>Business Logic]
    end
    
    subgraph "Output"
        Log[Application Logs<br/>Event Processed]
    end
    
    HTTP --> Controller
    Controller --> Publisher
    Publisher -->|Add UUID<br/>Add Timestamp| Bridge
    Bridge --> Binding
    Binding --> Binder
    Binder -->|Serialize| Topic
    Topic -->|Poll/Consume| InBinding
    InBinding --> Deserialize
    Deserialize --> Consumer
    Consumer --> Log
    
    style HTTP fill:#e1f5ff
    style Topic fill:#ffe1e1
    style Log fill:#e1ffe1
```

## 10. Profile Selection Flow

```mermaid
flowchart TD
    Start([Application Startup]) --> CheckProfile{Check Active Profile}
    
    CheckProfile -->|default| Kafka[Load Kafka Binder<br/>Port 9092]
    CheckProfile -->|rabbitmq| RabbitMQ[Load RabbitMQ Binder<br/>Port 5672]
    CheckProfile -->|pulsar| Pulsar[Load Pulsar Binder<br/>Port 6650]
    CheckProfile -->|pubsub| PubSub[Load Pub/Sub Binder<br/>Port 8085]
    CheckProfile -->|schema| Schema{Schema + Broker?}
    
    Schema -->|kafka,schema| KafkaSchema[Kafka + Schema Registry]
    Schema -->|rabbitmq,schema| RabbitSchema[RabbitMQ + Schema Registry]
    Schema -->|pulsar,schema| PulsarSchema[Pulsar + Schema Registry]
    
    Kafka --> InitBinder[Initialize Binder]
    RabbitMQ --> InitBinder
    Pulsar --> InitBinder
    PubSub --> InitBinder
    KafkaSchema --> InitBinder
    RabbitSchema --> InitBinder
    PulsarSchema --> InitBinder
    
    InitBinder --> CreateBindings[Create Bindings<br/>eventProducer-out-0<br/>eventConsumer-in-0]
    CreateBindings --> StartConsumers[Start Consumer Functions]
    StartConsumers --> Ready([Application Ready])
    
    style Kafka fill:#ffe6e6
    style RabbitMQ fill:#ffe6e6
    style Pulsar fill:#ffe6e6
    style PubSub fill:#ffe6e6
    style Ready fill:#e6ffe6
```

## Usage in README

These diagrams are already integrated into README.md. GitHub and most Markdown viewers support Mermaid rendering natively.

### Diagrams in README.md:

1. ✅ **High-Level Architecture** - Shows client, application, binder, and broker layers
2. ✅ **Publish Event Sequence** - Complete message publishing flow
3. ✅ **Consume Event Sequence** - Message consumption flow
4. ✅ **Component Diagram** - Application components and relationships
5. ✅ **Deployment Diagram** - Docker containers and infrastructure

### Additional Diagrams (Available Here):

6. **Schema Registry Sequence** - Schema validation flow
7. **State Diagram** - Message processing states
8. **Class Diagram** - Java class relationships
9. **Data Flow Diagram** - Data transformation flow
10. **Profile Selection Flow** - Profile-based configuration

### How to View:

**On GitHub:**
```bash
git add .
git commit -m "Add architecture diagrams"
git push
# View on GitHub - Mermaid renders automatically
```

**Locally:**
- VS Code: Install "Markdown Preview Mermaid Support" extension
- IntelliJ IDEA: Built-in Mermaid support
- Browser: Use Mermaid Live Editor (https://mermaid.live)

### Copy Individual Diagrams:

To add any diagram to another document, copy the entire mermaid code block:

```markdown
```mermaid
[paste diagram code here]
```
```

All diagrams are production-ready and demonstrate the messaging-agnostic architecture with schema registry support.
