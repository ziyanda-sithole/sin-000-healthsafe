#!/usr/bin/env bash
# Starts the ActiveMQ broker and all five services and LEAVES THEM RUNNING, so you can use the
# system in a browser or with curl. Press Ctrl+C to stop the services again.
#
#   bash scripts/start-all.sh             builds a service only if its jar is missing
#   bash scripts/start-all.sh --rebuild   rebuilds every jar first (use after changing code)
#
# Needs: Java 17+, curl, Docker (for the broker), and Maven if a jar has to be built.
# Each service's output is kept in logs/ (ignored by git).

set -u

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
LOGS="$ROOT/logs"
mkdir -p "$LOGS"

REBUILD=0
for arg in "$@"; do
  case "$arg" in
    --rebuild) REBUILD=1 ;;
    *) echo "unknown option: $arg"; exit 2 ;;
  esac
done

MODULES=(ingestion-service ward-service alert-level-service staffing-service equipment-alert-service)

port_of() {
  case "$1" in
    ingestion-service)       echo 7030 ;;
    ward-service)            echo 7031 ;;
    alert-level-service)     echo 7032 ;;
    staffing-service)        echo 7033 ;;
    equipment-alert-service) echo 7034 ;;
  esac
}

PIDS=()
cleanup() {
  for pid in "${PIDS[@]:-}"; do
    [[ -n "$pid" ]] && kill "$pid" 2>/dev/null
  done
}
trap cleanup EXIT
trap 'echo; echo "Stopping the services (the broker keeps running)..."; exit 0' INT TERM

die() { echo; echo "STOPPED: $1" >&2; exit 1; }
port_open() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

wait_for_port() { # PORT SECONDS
  local i
  for ((i = 0; i < $2; i++)); do port_open "$1" && return 0; sleep 1; done
  return 1
}

wait_healthy() { # PORT SECONDS
  local i
  for ((i = 0; i < $2; i++)); do
    [[ "$(curl -s -m 2 "http://localhost:$1/health")" == "OK" ]] && return 0
    sleep 1
  done
  return 1
}

command -v java >/dev/null || die "java not found"
command -v curl >/dev/null || die "curl not found"
java_major=$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)
[[ "${java_major:-0}" -ge 17 ]] || die "Java 17 or newer is required (found: $(java -version 2>&1 | head -1))"
if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE="docker-compose"
else
  die "docker compose not found (needed for the ActiveMQ broker)"
fi

for m in "${MODULES[@]}"; do
  p=$(port_of "$m")
  port_open "$p" && die "port $p ($m) is already in use. Stop the old copy first (Ctrl+C in its terminal; to find it: ss -ltnp | grep :$p)"
done

for m in "${MODULES[@]}"; do
  if [[ $REBUILD -eq 1 || ! -f "$ROOT/$m/target/$m.jar" ]]; then
    command -v mvn >/dev/null || die "mvn not found, and $m has to be built"
    echo "building $m ..."
    (cd "$ROOT/$m" && mvn -B -q package -DskipTests > "$LOGS/$m-build.log" 2>&1) \
      || { tail -20 "$LOGS/$m-build.log"; die "build failed in $m (full output: $LOGS/$m-build.log)"; }
  fi
done

if port_open 61616; then
  echo "broker: already listening on 61616, reusing it"
else
  echo "broker: starting ..."
  (cd "$ROOT/common" && $COMPOSE up -d > "$LOGS/broker.log" 2>&1) || { tail -20 "$LOGS/broker.log"; die "could not start the broker"; }
  wait_for_port 61616 90 || die "the broker did not open port 61616 within 90 seconds"
fi

for m in "${MODULES[@]}"; do
  p=$(port_of "$m")
  (cd "$ROOT/$m" && exec java -jar "target/$m.jar" > "$LOGS/$m.log" 2>&1) &
  PIDS+=($!)
  if wait_healthy "$p" 40; then
    echo "up: $m (port $p)"
  else
    tail -25 "$LOGS/$m.log"
    die "$m did not answer /health within 40 seconds"
  fi
done

cat <<'MSG'

Everything is running. Try these in a browser (they show JSON):

  http://localhost:7030/wards              the cleaned ward list (ingestion-service)
  http://localhost:7031/wards              the same list, held by ward-service
  http://localhost:7031/wards/W-05         one ward
  http://localhost:7031/wards/W-05/staffing   latest staffing update (404 until one is published)
  http://localhost:7031/departments        departments
  http://localhost:7032/alert-level        the hospital emergency status (0-8)
  http://localhost:7033/schedule/W-05      the on-call schedule for a ward
  http://localhost:7034/alerts             equipment alerts processed so far
  http://localhost:8161/admin              the ActiveMQ console (admin / admin)

A browser can only GET. To change things, use curl in another terminal:

  curl -X PUT  -H "Content-Type: application/json" -d '{"level":8}' http://localhost:7032/alert-level
  curl -X POST http://localhost:7033/schedule/W-05/publish
  curl -X POST -H "Content-Type: application/json" -d '{"equipment":"ventilator","details":"no airflow"}' \
       http://localhost:7031/wards/W-05/equipment-failure

Watch a service's output with:  tail -f logs/ward-service.log
Press Ctrl+C here to stop the services. (The broker keeps running; stop it with: cd common && docker compose stop)
MSG

wait