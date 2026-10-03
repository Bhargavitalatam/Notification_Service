# Architecture & Design Document: Resilient Notification Service

## 1. Executive Summary
The **Resilient Notification Service** is an enterprise-grade backend microservice designed using **Event-Driven Architecture (EDA)** with **Apache Kafka** and the **Circuit Breaker Pattern** via **Resilience4j**. It decouples notification ingestion from notification processing, guarantees high throughput, and prevents cascading failures when downstream dependencies (such as the User Profile Service) experience degradation or downtime.

---

## 2. System Architecture & Component Diagram

```
+--------------------------------------------------------------------------------------------------------+
|                                        Client / API Consumers                                         |
+--------------------------------------------------------------------------------------------------------+
                                                    |
                                    HTTP POST /api/notifications/send
                                    (Returns 202 Accepted immediately)
                                                    v
+--------------------------------------------------------------------------------------------------------+
|                                    Notification Service Microservice                                   |
|                                                                                                        |
|   +--------------------------+                                 +-----------------------------------+   |
|   |  NotificationController  |                                 |   NotificationConsumerService     |   |
|   +--------------------------+                                 +-----------------------------------+   |
|                 |                                                                ^                     |
|                 v                                                                |                     |
|   +--------------------------+                                                   |                     |
|   | NotificationProducer     |                                                   | (Kafka Listener)    |
|   | Service                  |                                                   |                     |
|   +--------------------------+                                                   |                     |
|                 |                                                                |                     |
+-----------------|----------------------------------------------------------------|---------------------+
                  |                                                                |
                  | Publishes (Topic: notification_events, Key: userId)            |
                  v                                                                |
+----------------------------------------------------------------------------------+---------------------+
|                                           Apache Kafka Broker                                          |
|  Topic: [notification_events]                                                                          |
|  - Key: userId (Guarantees ordering per user)                                                          |
|  - Value: JSON NotificationEvent {eventId, userId, message, notificationType, timestamp}               |
+--------------------------------------------------------------------------------------------------------+
                                                    ^
                                                    | (Cluster Coordination)
                                                    v
                                      +---------------------------+
                                      |     Apache Zookeeper      |
                                      +---------------------------+
                                                    |
                                                    | Consumed by NotificationConsumerService
                                                    v
+--------------------------------------------------------------------------------------------------------+
|   NotificationConsumerService                                                                          |
|      |                                                                                                 |
|      v                                                                                                 |
|   +------------------------------------------------------------------------------------------------+   |
|   | UserProfileServiceClient (Protected by Resilience4j @CircuitBreaker: "userProfileService")     |   |
|   +------------------------------------------------------------------------------------------------+   |
|                 |                                                                  |                   |
|         [Normal / CLOSED]                                                  [OPEN / Fail-Fast]          |
|                 v                                                                  v                   |
|     HTTP GET /mock-users/{userId}                                      Invoke Fallback Method          |
|                 |                                                                  |                   |
+-----------------|------------------------------------------------------------------|-------------------+
                  v                                                                  v
+------------------------------------+                             +-----------------------------------+
|      Mock User Profile Service     |                             | Fallback Profile Generated        |
|  - Returns UserProfile with email  |                             | - email: fallback@example.com     |
|    and phone number                |                             | - phone: +1-FALLBACK              |
|  - Simulates failures / delays     |                             | - Log: Notification Deferred      |
+------------------------------------+                             +-----------------------------------+
                  |                                                                  |
                  +--------------------------------+---------------------------------+
                                                   v
                                   +--------------------------------+
                                   | Dispatch Notification Record   |
                                   | - Console Log Simulation       |
                                   | - NotificationStore In-Memory  |
                                   +--------------------------------+
```

---

## 3. Core Architectural Decisions

### 3.1 Event-Driven Asynchronous Processing with Kafka
- **Decoupling**: The REST endpoint (`POST /api/notifications/send`) decouples API callers from downstream execution. Callers receive an immediate `202 Accepted` acknowledgement with minimal latency.
- **Partitioning Strategy**: Every event published to `notification_events` uses `userId` as the Kafka message key. This ensures all notifications for a specific user land in the same Kafka partition, preserving chronological ordering without requiring a global cluster bottleneck.
- **Idempotence & Reliability**:
  - `acks: all` ensures messages are committed across all in-sync replicas before acknowledgement.
  - `enable.idempotence: true` prevents duplicate event publishing during network hiccups and broker rebalances.
  - Retries are configured with automatic exponential backoff.

### 3.2 Resilience with Resilience4j Circuit Breaker
Downstream dependencies (like User Profile Services) can suffer from network partition, slow thread pools, or process crashes. Without a circuit breaker, worker threads in the notification consumer would block indefinitely, leading to **thread pool exhaustion** and **cascading failures**.

#### Circuit Breaker Configuration:
- **Instance**: `userProfileService`
- **Sliding Window Type**: `COUNT_BASED`
- **Sliding Window Size**: `5` calls
- **Minimum Number of Calls**: `4` calls before evaluating error rate
- **Failure Rate Threshold**: `50%` (if >= 50% of the window calls fail, trip to `OPEN`)
- **Wait Duration in Open State**: `5000ms` (5 seconds cooldown before testing recovery)
- **Permitted Number of Calls in Half-Open State**: `2` probe calls
- **Automatic Transition from Open to Half-Open**: `true`

#### State Machine Flow:
1. **`CLOSED`**: Calls are forwarded directly to the User Profile Service. Response times and status codes are recorded.
2. **`OPEN`**: When failure rate reaches or exceeds 50%, the circuit breaker trips to `OPEN`. All incoming calls immediately fail fast (`CallNotPermittedException`) without making a network request. The fallback method `getUserProfileFallback()` is immediately triggered, logging `"Circuit Breaker open or call failed for user: ... Circuit Breaker open, notification deferred"`.
3. **`HALF_OPEN`**: After 5 seconds in `OPEN`, the circuit breaker enters `HALF_OPEN`. It allows 2 probe requests through. If both succeed, it transitions back to `CLOSED`. If any probe fails, it trips back to `OPEN` for another 5 seconds.

---

## 4. Error Handling and Resilience Matrix

| Failure Mode | Detection Mechanism | System Action & Mitigation |
|---|---|---|
| **User Profile Service Outage (500 Error)** | RestTemplate receives 5xx status code | Resilience4j records failure. Invokes fallback profile (`fallback@example.com`), logs `"Circuit Breaker open, notification deferred"`, and queues notification with `DEFERRED_FALLBACK` status. |
| **User Profile Service High Latency (>2000ms)** | RestTemplate timeout (2000ms connect/read) | Call times out, recorded as error by Resilience4j. Trips circuit to OPEN if threshold reached, protecting notification consumer threads. |
| **Circuit Breaker in OPEN State** | Resilience4j state inspection | Fast-fails instantly. Zero outbound network traffic generated. Invokes fallback profile. |
| **Kafka Deserialization / Malformed Payload** | Spring Kafka `ErrorHandlingDeserializer` | Payload is safely routed to `DefaultErrorHandler` with backoff; invalid payload is logged without crashing consumer. |
| **Temporary Kafka Broker Disconnection** | KafkaProducer retry loop / Consumer reconnection | Producer retries up to 3 times with `enable.idempotence`. Consumer automatically retries reconnection with exponential backoff. |

---

## 5. Observability and Monitoring

1. **Spring Boot Actuator**:
   - `/actuator/health`: Provides holistic status including broker health and circuit breaker status (`UP`).
   - `/actuator/circuitbreakers`: Real-time health, state, failure rate, and call metrics.
   - `/actuator/circuitbreakerevents`: Full chronological stream of transitions (`CLOSED -> OPEN -> HALF_OPEN -> CLOSED`) and error events.
2. **Structured Logging**:
   - Every state transition is published by `ResilienceConfig` to console and logs via `onStateTransition`.
   - Every consumer event logs user ID, email/phone resolution, and final dispatch status (`[NOTIFICATION SENT]` or `[NOTIFICATION DEFERRED]`).
