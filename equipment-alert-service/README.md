# EquipmentAlertServiceApp

## Overview

Uses a Queue to guarantee delivery of critical medical equipment failure alerts.

Part of the [HealthSafe](../README.md) project — its alerting service.
Independent Maven module, no parent pom.

Mechanism: ActiveMQ Queue (guaranteed delivery)

Queue: `equipment-failure-queue`

- Producer: `ward-service` (`../ward-service`) — queues an alert when
  `POST /wards/{id}/equipment-failure` reports a failure on one of its wards.
- Consumer: this service.

Broker URL and queue name are shared via a common `co.wethinkcode.healthsafe.mq.MqConfig`
class (`BROKER_URL`, `QUEUE`). It's identical in every participating service's own
source tree — each service here is an independent Maven project with no shared
parent pom, so the common package is duplicated rather than imported from one place.

A queue (not a topic) is used deliberately: each failure alert must be delivered to
exactly one consumer and processed at least once, even if this service is briefly
down — unlike the broadcast `staffing-events-topic` in [`../common/`](../common),
where every subscriber gets every message.

## How delivery is guaranteed

| Concern | What happens |
|---|---|
| Alert sent while this service is down | It waits on the queue (messages are persistent) and is delivered when the consumer connects |
| This service crashes, or the handler throws, mid-processing | The session is transacted and commits only **after** the handler finishes, so the alert stays on the broker and is delivered again |
| An alert keeps failing (for example unreadable JSON) | After the broker's default retries it is moved to the dead-letter queue `ActiveMQ.DLQ` instead of being dropped, where a person can inspect it |
| The same alert arrives twice | Delivery is **at least once**, not exactly once, so duplicates are possible. Each alert has an `alertId` and `AlertLog` recognises a repeat |
| The broker is down when this service starts | The service starts and serves HTTP anyway; the consumer retries in the background every 5 seconds |

## Alert format

JSON text message:

```json
{
  "alertId": "5b0c2e1e-6c52-4d1f-8f43-0d2b8f6a1c11",
  "wardId": "W-05",
  "equipment": "ventilator",
  "details": "no airflow",
  "reportedAtMillis": 1791360000000
}
```

`details` is optional. Anything that is not a valid alert is rejected (and so ends up in the
dead-letter queue) rather than skipped.

## Project structure

```
equipment-alert-service/
├── pom.xml
└── src/
    ├── main/java/co/wethinkcode/healthsafe/
    │   ├── EquipmentAlertServiceApp.java  (routes + startup)
    │   ├── EquipmentFailureAlert.java     (the alert record)
    │   ├── EquipmentAlertParser.java      (strict reading of a message)
    │   ├── EquipmentAlertConsumer.java    (transacted queue consumer with retry)
    │   ├── AlertHandler.java              (what to do with an alert)
    │   ├── AlertLog.java                  (processed alerts, de-duplicated by alertId)
    │   └── mq/
    │       └── MqConfig.java
    └── test/java/co/wethinkcode/healthsafe/
        ├── EquipmentAlertParserTest.java
        ├── AlertLogTest.java
        └── EquipmentAlertConsumerTest.java
```

## Build

```
mvn package
```

## Run

Start the broker first (`docker-compose up -d` in [`../common/`](../common)), then:

```
java -jar target/equipment-alert-service.jar
```

Listens on port `7034`. It prints `Consuming queue equipment-failure-queue ...` once connected.

## Endpoints

| Method | Path | Response |
|---|---|---|
| GET | `/health` | `OK` |
| GET | `/alerts` | JSON array of the distinct alerts processed so far, newest first |

"Raising the alarm" prints a `*** EQUIPMENT FAILURE ...` line and records the alert. There is no
pager or e-mail integration; replacing `EquipmentAlertServiceApp.handle` is where one would go.

## Limitations

- `AlertLog` is in memory and never trimmed: it is lost on restart (so a redelivered alert is
  handled again after a restart) and grows without limit.
- The dead-letter queue is not consumed by anything; someone has to look at `ActiveMQ.DLQ`
  in the broker console (`http://localhost:8161/admin`).
- The Docker broker keeps its data inside the container, so removing the container loses any
  queued alerts. Mount a volume for `/opt/apache-activemq/data` for real durability.
- If the broker is down when ward-service receives a report, the alert is **not** stored anywhere
  (ward-service answers `502`); a production system would keep a local outbox and retry.

## Test

Unit tests (JUnit 5). The consumer tests run against a real broker started inside the test, so
no Docker is needed:

```
mvn test
```

To try it by hand with the broker up, send an alert from the console (Send page, destination
`equipment-failure-queue`, type Queue) or through ward-service:

```
curl -X POST -H "Content-Type: application/json" -d '{"equipment":"ventilator"}' \
     http://localhost:7031/wards/W-05/equipment-failure
curl http://localhost:7034/alerts
```

The whole chain is checked end to end by `scripts/smoke-test.sh --with-mq` in the project root.