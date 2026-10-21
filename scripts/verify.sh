#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
kc -n jardinrent get pods
curl -fsS http://localhost:18081/health
curl -fsS http://localhost:18082/health
curl -fsS http://localhost:18081/metrics | grep '^herramientas_disponibles' >/dev/null || exit 1
curl -fsS http://localhost:18082/metrics | grep '^cotizaciones_activas' >/dev/null || exit 1
curl -fsS --max-time 5 -H 'Content-Type: application/json' -d '{"herramientaId":"1","dias":3}' http://localhost:18082/cotizaciones
echo
for attempt in {1..60}; do
  if curl -fsS http://localhost:19090/api/v1/targets | python3 -c '
import json,sys
targets=[t for t in json.load(sys.stdin)["data"]["activeTargets"] if t["labels"].get("namespace")=="jardinrent"]
assert {t["labels"].get("service") for t in targets} >= {"herramientas","cotizaciones"}
assert all(t["health"]=="up" for t in targets)
' 2>/dev/null; then
    echo "Targets Herramientas y Cotizaciones: UP"
    curl -fsS http://localhost:19090/api/v1/rules | python3 -c '
import json,sys
names={r["name"] for g in json.load(sys.stdin)["data"]["groups"] for r in g["rules"]}
assert {"HerramientasCaido","CotizacionesCaido","CotizacionesConTimeout","HerramientaSinStock"} <= names
print("Cuatro reglas de alerta cargadas en Prometheus")
'
    exit 0
  fi
  sleep 5
done
echo "Targets no están UP. Revisa http://localhost:19090/targets" >&2
exit 1
