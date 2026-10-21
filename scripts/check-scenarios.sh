#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
cd "$ROOT"
traffic_pid=''
cleanup() {
  [[ -z "$traffic_pid" ]] || kill "$traffic_pid" 2>/dev/null || true
  kc -n jardinrent set env deployment/herramientas SERVER_PORT=8080 RESPONSE_DELAY_MS=0 >/dev/null || true
  curl -fsS --max-time 3 -X PATCH -H 'Content-Type: application/json' -d '{"disponibles":10}' http://localhost:18081/herramientas/1/stock >/dev/null || true
}
trap cleanup EXIT
wait_alert() {
  local alert="$1"
  for attempt in {1..60}; do
    if curl -fsSG --data-urlencode "query=ALERTS{alertname=\"$alert\",alertstate=\"firing\"}" http://localhost:19090/api/v1/query |
      python3 -c 'import json,sys; assert json.load(sys.stdin)["data"]["result"]' 2>/dev/null; then echo "$alert FIRING"; return 0; fi
    sleep 5
  done
  echo "No se activó $alert" >&2; return 1
}
wait_clear_alert() {
  local alert="$1"
  for attempt in {1..45}; do
    if curl -fsSG --data-urlencode "query=ALERTS{alertname=\"$alert\",alertstate=\"firing\"}" http://localhost:19090/api/v1/query |
      python3 -c 'import json,sys; assert not json.load(sys.stdin)["data"]["result"]' 2>/dev/null; then echo "$alert resuelta"; return 0; fi
    sleep 5
  done
  echo "La alerta $alert no se resolvió" >&2; return 1
}
expect_quote() {
  local expected="$1" actual
  actual="$(curl -sS --max-time 10 -o "$RUNTIME/last-response.json" -w '%{http_code}' -H 'Content-Type: application/json' -d '{"herramientaId":"1","dias":3}' http://localhost:18082/cotizaciones)"
  [[ "$actual" =~ ^($expected)$ ]] || { echo "HTTP esperado $expected, recibido $actual" >&2; return 1; }
  echo "Cotización HTTP $actual"
}
bash load-testing/stress.sh http://localhost:18082 900 3 > "$RUNTIME/scenarios-traffic.log" 2>&1 & traffic_pid=$!
python3 scripts/capture-evidence.py baseline
bash scripts/scenario.sh crash
crash_seen=false
for attempt in {1..60}; do
  if kc -n jardinrent get pods -l app=herramientas -o json | tee docs/evidencias/caso1-crashloop-pods.json | python3 -c 'import json,sys; assert any(c.get("state",{}).get("waiting",{}).get("reason")=="CrashLoopBackOff" for p in json.load(sys.stdin)["items"] for c in p["status"].get("containerStatuses",[]))' 2>/dev/null; then crash_seen=true; break; fi
  sleep 3
done
$crash_seen || { echo "No se observó CrashLoopBackOff" >&2; exit 1; }
wait_alert HerramientasCaido
expect_quote '503|504'
python3 scripts/capture-evidence.py caso1-caida
sleep "${EVIDENCE_PAUSE_SECONDS:-0}"
bash scripts/scenario.sh crash-fix
expect_quote 201
sleep 20
python3 scripts/capture-evidence.py caso1-recuperacion
wait_clear_alert HerramientasCaido
wait_clear_alert CotizacionesConTimeout
bash scripts/scenario.sh slow
wait_alert CotizacionesConTimeout
expect_quote 504
curl -fsS --max-time 1 http://localhost:18081/health
python3 scripts/capture-evidence.py caso2-lentitud
sleep "${EVIDENCE_PAUSE_SECONDS:-0}"
bash scripts/scenario.sh slow-fix
expect_quote 201
sleep 20
python3 scripts/capture-evidence.py caso2-recuperacion
wait_clear_alert CotizacionesConTimeout
bash scripts/scenario.sh stock
wait_alert HerramientaSinStock
expect_quote 409
python3 scripts/capture-evidence.py caso3-stock
sleep "${EVIDENCE_PAUSE_SECONDS:-0}"
bash scripts/scenario.sh stock-fix
expect_quote 201
sleep 20
python3 scripts/capture-evidence.py caso3-recuperacion
wait_clear_alert HerramientaSinStock
bash scripts/verify.sh
echo "Las tres casuísticas se activaron, alertaron y recuperaron."
