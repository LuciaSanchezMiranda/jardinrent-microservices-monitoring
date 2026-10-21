#!/usr/bin/env python3
"""Guarda observaciones reales de las APIs y el clúster, sin simular resultados."""
import datetime
import json
import os
import pathlib
import re
import subprocess
import sys
import urllib.parse
import urllib.request

label = sys.argv[1] if len(sys.argv) > 1 else "baseline"
if not re.fullmatch(r"[a-z0-9-]+", label):
    raise SystemExit("Etiqueta inválida")
root = pathlib.Path(__file__).resolve().parent.parent
profile = os.getenv("PROFILE", "jardinrent")
if not profile.startswith("jardinrent"):
    raise SystemExit("Perfil ajeno al laboratorio")

def get(path):
    with urllib.request.urlopen("http://localhost:19090" + path, timeout=10) as response:
        return json.load(response)

queries = {
    "targets_up": 'up{namespace="jardinrent"}',
    "replicas": 'kube_deployment_status_replicas_available{namespace="jardinrent"}',
    "delay": "herramientas_delay_seconds",
    "stock": "herramientas_stock",
    "timeouts": "cotizaciones_timeouts_total",
    "rechazos": "cotizaciones_rechazadas_total",
    "p95": 'histogram_quantile(0.95, sum by (le) (rate(cotizaciones_solicitud_seconds_bucket{operacion="crear"}[1m])))',
    "alertas": 'ALERTS{proyecto="jardinrent"}',
}
data = {"capturedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(), "profile": profile,
        "queries": {name: get("/api/v1/query?" + urllib.parse.urlencode({"query": query})) for name, query in queries.items()}}
data["targets"] = [t for t in get("/api/v1/targets")["data"]["activeTargets"] if t["labels"].get("namespace") == "jardinrent"]
client = pathlib.Path.home() / ".minikube/cache/linux/amd64/v1.34.0/kubectl"
command = [str(client)] if client.is_file() else ["minikube", "-p", profile, "kubectl", "--"]
pods = json.loads(subprocess.check_output(command + ["--context="+profile, "get", "pods", "-A", "-o", "json"], text=True, timeout=30))
data["pods"] = [{"namespace": pod["metadata"]["namespace"], "name": pod["metadata"]["name"],
                 "phase": pod["status"].get("phase"), "containers": [{"name": c["name"], "ready": c["ready"], "restarts": c["restartCount"], "state": c["state"]} for c in pod["status"].get("containerStatuses", [])]}
                for pod in pods["items"] if pod["metadata"]["namespace"] in ("jardinrent", "monitoring")]
folder = root / "docs" / "evidencias"
folder.mkdir(parents=True, exist_ok=True)
(folder / (label + ".json")).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
print("Evidencia real:", label, data["capturedAt"])
