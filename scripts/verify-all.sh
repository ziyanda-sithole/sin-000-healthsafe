#!/usr/bin/env bash
# One-command verification of the whole HealthSafe project.
#
#   bash scripts/verify-all.sh                  tests, build, start everything, smoke test
#   bash scripts/verify-all.sh --skip-tests     same, without re-running the unit tests
#   bash scripts/verify-all.sh --resilience     also stop and restart the broker (takes ~2 more minutes)
#
# Needs: Java 17+, Maven, curl and Docker (for the ActiveMQ broker in common/).
# Services are started in the background and stopped again when the script ends.
# The broker is left running; stop it with:  (cd common && docker compose stop)
# Every service's output is kept in logs/ (ignored by git).

set -u

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
LOGS="$ROOT/logs"
mkdir -p "$LOGS"

SKIP_TESTS=0
RESILIENCE=0
for arg in "$@"; do
  case "$arg" in
    --skip-tests) SKIP_TESTS=1 ;;
    --resilience) RESILIENCE=1 ;;
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
trap 'exit 130' INT TERM

step() { echo; echo "== $1"; }
die()  { echo; echo "STOPPED: $1" >&2; exit 1; }

# true if something is listening on the port
port_open() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

wait_for_port() { # PORT SECONDS
  local i
  for ((i = 0; i < $2; i++)); do
    port_open "$1" && return 0
    sleep 1
  done
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

step "1. prerequisites"
command -v java >/dev/null || die "java not found"
command -v mvn  >/dev/null || die "mvn not found"
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
echo "java $java_major, $(mvn -v 2>/dev/null | head -1), broker via '$COMPOSE'"

step "2. are the service ports free?"
for m in "${MODULES[@]}"; do
  p=$(port_of "$m")
  if port_open "$p"; then
    die "port $p ($m) is already in use. Stop the old copy first (Ctrl+C in its terminal; to find it: ss -ltnp | grep :$p)"
  fi
done
echo "ports 7030-7034 are free"

if [[ $SKIP_TESTS -eq 0 ]]; then
  step "3. unit tests (every module)"
  total=0
  for m in "${MODULES[@]}"; do
    log="$LOGS/$m-test.log"
    if (cd "$ROOT/$m" && mvn test > "$log" 2>&1); then
      n=$(grep -E "Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$" "$log" | tail -1 | sed -E 's/.*Tests run: ([0-9]+),.*/\1/')
      echo "PASS  $m: ${n:-0} tests"
      total=$((total + ${n:-0}))
    else
      echo "FAIL  $m (full output: $log)"
      grep -E "Tests run:.*(FAIL|ERROR)|\[ERROR\]" "$log" | head -15
      die "unit tests failed in $m"
    fi
  done
  echo "all modules passed: $total tests"
else
  step "3. unit tests: skipped"
fi

step "4. build the jars"
for m in "${MODULES[@]}"; do
  if ! (cd "$ROOT/$m" && mvn -q package -DskipTests > "$LOGS/$m-build.log" 2>&1); then
    tail -20 "$LOGS/$m-build.log"
    die "build failed in $m (full output: $LOGS/$m-build.log)"
  fi
  [[ -f "$ROOT/$m/target/$m.jar" ]] || die "build finished but $m/target/$m.jar is missing"
  echo "built $m"
done

step "5. ActiveMQ broker"
if port_open 61616; then
  echo "something is already listening on 61616: reusing it"
else
  (cd "$ROOT/common" && $COMPOSE up -d > "$LOGS/broker.log" 2>&1) || { tail -20 "$LOGS/broker.log"; die "could not start the broker"; }
  wait_for_port 61616 90 || die "the broker did not open port 61616 within 90 seconds (look at: cd common && $COMPOSE logs)"
  echo "broker is up"
fi

step "6. start the five services (in order)"
for m in "${MODULES[@]}"; do
  p=$(port_of "$m")
  (cd "$ROOT/$m" && exec java -jar "target/$m.jar" > "$LOGS/$m.log" 2>&1) &
  PIDS+=($!)
  if wait_healthy "$p" 40; then
    echo "up    $m (port $p)"
  else
    echo "FAIL  $m did not answer /health within 40 seconds. Its log:"
    tail -25 "$LOGS/$m.log"
    die "$m did not start"
  fi
done

step "7. end-to-end smoke test (stages 1 to 4)"
bash "$ROOT/scripts/smoke-test.sh" --with-mq | tee "$LOGS/smoke-test.log"
smoke_status=${PIPESTATUS[0]}

echo
echo "== evidence from the logs: messages that really crossed the broker"
grep -h -E "Subscribed to topic|Consuming queue|Staffing update for|EQUIPMENT FAILURE" \
  "$LOGS/ward-service.log" "$LOGS/equipment-alert-service.log" | tail -8

resilience_status=0
if [[ $RESILIENCE -eq 1 ]]; then
  step "8. resilience: the broker dies, then comes back"
  r_fail=0
  rcheck() { # NAME WANT_STATUS BODY_FRAGMENT CURL_ARGS...
    local name=$1 want=$2 frag=$3 out status body
    shift 3
    out=$(curl -s -m 10 -w $'\n%{http_code}' "$@")
    status=${out##*$'\n'}
    body=${out%$'\n'*}
    if [[ "$status" == "$want" && "$body" == *"$frag"* ]]; then
      echo "PASS  $name"
    else
      echo "FAIL  $name"
      echo "        expected HTTP $want containing: $frag"
      echo "        got      HTTP $status: $body"
      r_fail=$((r_fail + 1))
    fi
  }
  subs_before=$(grep -c "Subscribed to topic" "$LOGS/ward-service.log")
  cons_before=$(grep -c "Consuming queue" "$LOGS/equipment-alert-service.log")

  (cd "$ROOT/common" && $COMPOSE stop > "$LOGS/broker.log" 2>&1)
  sleep 3
  echo "-- broker stopped. The core must keep working, and the broker features must fail loudly:"
  rcheck "ward-service still serves wards"        200 "\"wardId\":\"W-05\"" "http://localhost:7031/wards/W-05"
  rcheck "staffing-service still builds schedules" 200 "doctorsOnCall"       "http://localhost:7033/schedule/W-05"
  rcheck "publishing a schedule fails loudly"      502 "message broker"      -X POST "http://localhost:7033/schedule/W-05/publish"
  rcheck "an equipment alert is refused, not faked" 502 "Alert NOT sent"     -X POST -H "Content-Type: application/json" -d '{"equipment":"resilience-test"}' "http://localhost:7031/wards/W-05/equipment-failure"

  (cd "$ROOT/common" && $COMPOSE start > "$LOGS/broker.log" 2>&1)
  wait_for_port 61616 90 || die "the broker did not come back on port 61616"
  echo "-- broker restarted. Waiting up to 60s for both services to reconnect on their own..."
  reconnected=0
  for ((i = 0; i < 60; i++)); do
    subs_now=$(grep -c "Subscribed to topic" "$LOGS/ward-service.log")
    cons_now=$(grep -c "Consuming queue" "$LOGS/equipment-alert-service.log")
    if [[ $subs_now -gt $subs_before && $cons_now -gt $cons_before ]]; then reconnected=1; break; fi
    sleep 1
  done
  if [[ $reconnected -eq 1 ]]; then
    echo "PASS  ward-service re-subscribed and equipment-alert-service resumed consuming"
  else
    echo "FAIL  the services did not reconnect within 60s (see logs/ward-service.log and logs/equipment-alert-service.log)"
    r_fail=$((r_fail + 1))
  fi
  echo "-- full smoke test again, now that the broker is back:"
  bash "$ROOT/scripts/smoke-test.sh" --with-mq | tee "$LOGS/smoke-test-after-restart.log" | tail -3
  [[ ${PIPESTATUS[0]} -eq 0 ]] || r_fail=$((r_fail + 1))
  resilience_status=$r_fail
fi

echo
echo "================ SUMMARY ================"
if [[ $smoke_status -eq 0 && $resilience_status -eq 0 ]]; then
  echo "EVERYTHING PASSED"
  exit 0
fi
[[ $smoke_status -ne 0 ]] && echo "smoke test: FAILED (see the FAIL lines above and logs/)"
[[ $resilience_status -ne 0 ]] && echo "resilience: $resilience_status check(s) FAILED"
exit 1