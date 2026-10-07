# common — Asynchronous Decoupling (MQ)

## Overview

Topic: `staffing-events-topic`

Staffing updates are broadcast as Events via the broker to decouple the frontend from the Staffing Service.

Part of the [HealthSafe](../README.md) project. Holds the ActiveMQ broker shared
by the services below — not a service itself, so it has no port of its own.

- Producer: `staffing-service` (`../staffing-service`)
- Consumer(s): ward-service

Broker URL and topic name are shared via a common `co.wethinkcode.healthsafe.mq.MqConfig` class
(`BROKER_URL`, `TOPIC`). It's identical in every participating service's own source
tree — each service here is an independent Maven project with no shared parent pom,
so the common package is duplicated rather than imported from one place.

## Event format

Status: stage 3, in progress. The topic carries JSON text messages. Today there is one
event type:

```json
{
  "type": "ScheduleUpdated",
  "wardId": "W-05",
  "alertLevel": 8,
  "status": "CODE_BLUE",
  "doctorsOnCall": 9,
  "issuedAtMillis": 1791360000000
}
```

| Field | Meaning |
|---|---|
| `type` | Kind of event, always `ScheduleUpdated` for now. Consumers ignore types they do not know |
| `wardId` | Normalised ward id, e.g. `W-05` |
| `alertLevel` | Emergency Status (0-8) the schedule was sized from |
| `status` | Band for that level: `NORMAL`, `ELEVATED`, `CRITICAL` or `CODE_BLUE` |
| `doctorsOnCall` | Headcount required for the ward |
| `issuedAtMillis` | When staffing-service issued it, in epoch milliseconds |

Rules both sides follow:

- **Full state, not a change.** Each event describes the ward's whole current schedule, so a
  subscriber that missed an earlier one is repaired by the next.
- **Newest wins.** A subscriber keeps only the event with the highest `issuedAtMillis` per ward,
  so an old event arriving late cannot overwrite a newer one.
- **Be tolerant when reading.** Ignore fields you do not know and event types you do not handle.
- **A topic does not remember.** A subscriber that is down when an event is published does not
  receive it later. That is accepted here because the next event carries the full state; the
  stage 4 queue is the part of the system that guarantees delivery.

## Project structure

```
common/
├── docker-compose.yml
└── README.md
```

This folder holds the broker config and notes only — the actual publish/subscribe
code belongs in the producer/consumer services listed above (their poms already
depend on `activemq-client`, and each already has
`src/main/java/co/wethinkcode/healthsafe/mq/MqConfig.java`).

## Build

Nothing to build here directly — this folder just brings up the broker used by the
services listed above.

## Run

```
docker compose up -d
```

- Broker URL for clients: `tcp://localhost:61616`
- Web console: http://localhost:8161 (default admin/admin)

Then start the producer/consumer services as usual (`mvn package && java -jar ...`
from their own directories at the project root).

## Test

```
docker compose ps          # confirm the broker container is healthy
```

Once the TODOs below are implemented, verify end-to-end by publishing a message from
`staffing-service` and confirming the consumer(s) receive it — e.g. via logs, or by
watching the topic in the web console.

## TODO

- Add `activemq-client` publish logic to `staffing-service` on its stage/state-change endpoint.
- Add `activemq-client` subscriber logic to consumer service(s) above, replacing any
  direct synchronous calls to `staffing-service`.
