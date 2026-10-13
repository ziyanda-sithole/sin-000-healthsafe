#!/usr/bin/env bash
# End-to-end smoke test for HealthSafe.
#
#   bash scripts/smoke-test.sh             core (stages 1-2): needs the four REST services
#   bash scripts/smoke-test.sh --with-mq   also stages 3 and 4 (topic and queue): needs the ActiveMQ
#                                          broker AND equipment-alert-service (7034) running

# Start the services first, each in its own terminal, in this order:
#   ingestion-service (7030), ward-service (7031),
#   alert-level-service (7032), staffing-service (7033)
#
# NOTE: this test CHANGES the hospital alert level (it raises it to 8 to prove the
# schedule reacts) and sets it back to 0 at the end.

set -u

WITH_MQ=0
if [[ "${1:-}" == "--with-mq" ]]; then
  WITH_MQ=1
fi

INGESTION=${INGESTION_URL:-http://localhost:7030}
WARD=${WARD_URL:-http://localhost:7031}
ALERT=${ALERT_LEVEL_URL:-http://localhost:7032}
STAFFING=${STAFFING_URL:-http://localhost:7033}
EQUIPMENT=${EQUIPMENT_ALERT_URL:-http://localhost:7034}
MQ_WAIT_SECONDS=${MQ_WAIT_SECONDS:-10}   # how long to wait for asynchronous messages to arrive

pass=0
fail=0
status=""
body=""

# fetch CURL_ARGS... : runs curl, leaving the HTTP status in $status and the body in $body.
# curl prints 000 as the status if it cannot connect.
fetch() {
  local out
  out=$(curl -s -w $'\n%{http_code}' "$@")
  status=${out##*$'\n'}
  body=${out%$'\n'*}
}

# matches WANT_STATUS WANT_BODY_FRAGMENT : does the last fetch satisfy both?
matches() {
  [[ "$status" == "$1" && "$body" == *"$2"* ]]
}

ok() {
  echo "PASS  $1"
  pass=$((pass + 1))
}

bad() {
  echo "FAIL  $1"
  echo "        expected HTTP $2 with body containing: $3"
  echo "        got      HTTP $status with body: $body"
  fail=$((fail + 1))
}

# check NAME WANT_STATUS WANT_BODY_FRAGMENT CURL_ARGS...
# Passes only if the HTTP status matches AND the body contains the fragment.
check() {
  local name=$1 want_status=$2 want_body=$3
  shift 3
  fetch "$@"
  if matches "$want_status" "$want_body"; then ok "$name"; else bad "$name" "$want_status" "$want_body"; fi
}

# check_eventually NAME SECONDS WANT_STATUS WANT_BODY_FRAGMENT CURL_ARGS...
# Like check, but keeps retrying for up to SECONDS: for things that arrive asynchronously.
check_eventually() {
  local name=$1 seconds=$2 want_status=$3 want_body=$4
  shift 4
  local deadline=$((SECONDS + seconds))
  while true; do
    fetch "$@"
    if matches "$want_status" "$want_body"; then
      ok "$name"
      return
    fi
    if (( SECONDS >= deadline )); then
      bad "$name (gave up after ${seconds}s)" "$want_status" "$want_body"
      return
    fi
    sleep 0.2
  done
}

put_level() {
  curl -s -X PUT -H "Content-Type: application/json" -d "{\"level\":$1}" "$ALERT/alert-level" > /dev/null
}

echo "== health =="
check "ingestion up"   200 "OK" "$INGESTION/health"
check "ward up"        200 "OK" "$WARD/health"
check "alert-level up" 200 "OK" "$ALERT/health"
check "staffing up"    200 "OK" "$STAFFING/health"

echo "== stage 1: ingestion cleans the CSV =="
check "W-05 duplicate merged into one record" 200 "merged duplicate of rows 5 and 6" "$INGESTION/wards"
check "W-11 Pediatrics spelling unified"      200 "\"wardId\":\"W-11\",\"wing\":\"East Wing\",\"department\":\"Paediatrics\"" "$INGESTION/wards"

echo "== stage 2: ward-service =="
check "lookup ignores case"      200 "\"wardId\":\"W-05\"" "$WARD/wards/w-05"
check "unknown ward is 404"      404 "Unknown ward" "$WARD/wards/W-99"
check "departments list"         200 "Paediatrics" "$WARD/departments"

echo "== stage 2: alert-level-service =="
put_level 0
check "level starts at 0"        200 "{\"level\":0}" "$ALERT/alert-level"
check "level 9 rejected"         400 "between 0 and 8" -X PUT -H "Content-Type: application/json" -d '{"level":9}' "$ALERT/alert-level"
check "malformed body rejected"  400 "error" -X PUT -H "Content-Type: application/json" -d 'not json' "$ALERT/alert-level"

echo "== stage 2: staffing-service (calls ward + alert-level) =="
check "normal level schedule"    200 "\"status\":\"NORMAL\",\"doctorsOnCall\":1" "$STAFFING/schedule/W-05"
put_level 8
check "code blue schedule"       200 "\"status\":\"CODE_BLUE\",\"doctorsOnCall\":9" "$STAFFING/schedule/w-05"
check "unknown ward is 404"      404 "Unknown ward" "$STAFFING/schedule/W-99"
put_level 0

if [[ $WITH_MQ -eq 1 ]]; then
  echo "== stage 3: staffing events over the topic (needs the broker) =="
  put_level 8
  fetch -X POST "$STAFFING/schedule/W-05/publish"
  if matches 200 "\"type\":\"ScheduleUpdated\""; then
    ok "publish broadcasts a ScheduleUpdated event"
  else
    bad "publish broadcasts a ScheduleUpdated event" 200 "\"type\":\"ScheduleUpdated\""
  fi
  # Wait for THIS exact event (matched by its timestamp), so an older one left over from an
  # earlier run can never make the check pass by accident.
  published_at=$(sed -n 's/.*"issuedAtMillis":\([0-9]*\).*/\1/p' <<< "$body")
  if [[ -n "$published_at" ]]; then
    check_eventually "ward-service received that event" "$MQ_WAIT_SECONDS" 200 "\"issuedAtMillis\":$published_at" "$WARD/wards/W-05/staffing"
    check "...and it carries CODE_BLUE, 9 doctors" 200 "\"status\":\"CODE_BLUE\",\"doctorsOnCall\":9" "$WARD/wards/W-05/staffing"
  else
    bad "ward-service received that event (no timestamp to look for)" 200 "\"issuedAtMillis\":<number>"
  fi
  check "publishing for an unknown ward is 404" 404 "Unknown ward" -X POST "$STAFFING/schedule/W-99/publish"
  check "staffing for an unknown ward is 404"   404 "Unknown ward" "$WARD/wards/W-99/staffing"
  put_level 0

  echo "== stage 4: equipment failure alerts over the queue =="
  check "equipment-alert-service up" 200 "OK" "$EQUIPMENT/health"
  # A unique marker, so we can tell THIS report from alerts left by earlier runs.
  marker="smoke-$(date +%s)-$RANDOM"
  fetch -X POST -H "Content-Type: application/json" -d "{\"equipment\":\"$marker\",\"details\":\"smoke test\"}" "$WARD/wards/w-05/equipment-failure"
  if matches 202 "\"equipment\":\"$marker\""; then
    ok "equipment failure accepted (202)"
  else
    bad "equipment failure accepted (202)" 202 "\"equipment\":\"$marker\""
  fi
  alert_id=$(sed -n 's/.*"alertId":"\([^"]*\)".*/\1/p' <<< "$body")
  if [[ -n "$alert_id" ]]; then
    check_eventually "equipment-alert-service processed that alert" "$MQ_WAIT_SECONDS" 200 "\"alertId\":\"$alert_id\"" "$EQUIPMENT/alerts"
  else
    bad "equipment-alert-service processed that alert (no alertId to look for)" 200 "\"alertId\":<id>"
  fi
  check "equipment failure for an unknown ward is 404" 404 "Unknown ward" -X POST -H "Content-Type: application/json" -d '{"equipment":"x"}' "$WARD/wards/W-99/equipment-failure"
  check "equipment failure without equipment is 400"   400 "equipment" -X POST -H "Content-Type: application/json" -d '{}' "$WARD/wards/W-05/equipment-failure"
  check "equipment failure with a malformed body is 400" 400 "error" -X POST -H "Content-Type: application/json" -d 'not json' "$WARD/wards/W-05/equipment-failure"
fi

echo
echo "$pass passed, $fail failed"
[[ $fail -eq 0 ]]