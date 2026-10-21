#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
command -v kubectl >/dev/null || { echo "Instala kubectl para port-forward" >&2; exit 1; }
start_forward herramientas jardinrent herramientas 18081:8080
start_forward cotizaciones jardinrent cotizaciones 18082:8080
start_forward grafana monitoring observabilidad-grafana 13000:80
prometheus_service="$(kc -n monitoring get services -o json | python3 -c 'import json,sys; names=[s["metadata"]["name"] for s in json.load(sys.stdin)["items"] if s["metadata"]["name"].endswith("-prometheus")]; assert len(names)==1, names; print(names[0])')"
start_forward prometheus monitoring "$prometheus_service" 19090:9090
for url in http://localhost:18081/health http://localhost:18082/health http://localhost:13000/api/health http://localhost:19090/-/ready; do
  ready=false
  for attempt in {1..40}; do
    if curl -fsS --max-time 3 "$url" >/dev/null; then ready=true; break; fi
    sleep 1
  done
  $ready || { echo "No se pudo abrir $url. Revisa .runtime/*.log" >&2; exit 1; }
done
printf '\nHerramientas: http://localhost:18081/herramientas\nCotizaciones: http://localhost:18082/cotizaciones\nGrafana: http://localhost:13000/d/jardinrent-overview\nPrometheus: http://localhost:19090/targets\nUsuario Grafana: admin\n'
