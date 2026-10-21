#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
cd "$ROOT"
eval "$(minikube -p "$PROFILE" docker-env --shell bash)"
docker build --progress=plain -t jardinrent/herramientas:1.0.0 -f microservices/herramientas-service/Dockerfile .
docker build --progress=plain -t jardinrent/cotizaciones:1.0.0 -f microservices/cotizaciones-service/Dockerfile .
