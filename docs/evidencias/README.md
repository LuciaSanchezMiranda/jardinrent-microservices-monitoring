# Evidencias de ejecución

Laboratorio ejecutado el 21 de octubre de 2026 en Docker Desktop/WSL2, perfil exclusivo de Minikube `jardinrent`, Kubernetes 1.34.0. Los JSON contienen marcas UTC; las capturas del navegador muestran la hora local de Lima.

## Comprobaciones realizadas

- Construcción de las dos imágenes con JDK 17: **9 pruebas aprobadas**, sin errores ni pruebas omitidas (3 en Herramientas, 6 en Cotizaciones).
- `setup.sh` completado: chart oficial 92.11.0, imágenes locales, deployments, servicios, ServiceMonitors, cuatro reglas y dashboard provisionado.
- `check-scenarios.sh` completado: las tres situaciones se activaron, produjeron el HTTP esperado, dispararon su alerta y se recuperaron; las alertas se resolvieron.
- `verify.sh`: salud de ambas APIs, cotización por 75.00, dos targets UP y cuatro reglas cargadas.
- Sintaxis Bash/Python y JSON validada. Las once consultas de Grafana respondieron HTTP 200 con datos: [grafana-queries.json](grafana-queries.json).

## Capturas

| Requisito | Evidencia |
|---|---|
| Seis paneles visibles y funcionando | [dashboard-general.png](dashboard-general.png) |
| Ambos servicios UP en Prometheus | [targets-up.png](targets-up.png) |
| Pods en Running | [pods-running.png](pods-running.png), [salida original](pods-running.txt) |
| Alerta propia en FIRING | [alerta-firing.png](alerta-firing.png) |
| Caso 1: caída | [caso1-grafana.png](caso1-grafana.png), [snapshot](caso1-caida.json) |
| Estado CrashLoopBackOff | [salida kubectl](caso1-crashloop.txt), [JSON de Kubernetes](caso1-crashloop-pods.json) |
| Caso 2: retraso y timeouts | [caso2-grafana.png](caso2-grafana.png), [snapshot](caso2-lentitud.json) |
| Caso 3: stock cero y rechazos | [caso3-grafana.png](caso3-grafana.png), [snapshot](caso3-stock.json) |

La captura de pods es la salida real de `kubectl get pods -A`, guardada como texto y visualizada en el navegador; no es una terminal simulada. El estado CrashLoopBackOff se capturó además en una repetición breve del primer fallo, seguida de su restauración.

## Datos originales y recuperación

[Estado inicial](baseline.json), [recuperación 1](caso1-recuperacion.json), [recuperación 2](caso2-recuperacion.json), [recuperación 3](caso3-recuperacion.json) y [verificación final](final.json). Cada snapshot incluye resultados PromQL, estado de los targets y pods de aplicación/monitoreo. Los snapshots inmediatamente posteriores a una reparación pueden conservar una alerta pendiente de resolver por su ventana temporal; la ejecución esperó su resolución antes de finalizar.

## Límites de la validación

Los scripts de instalación para una máquina nueva y de eliminación están documentados y revisados, pero no se reinstaló Docker Desktop ni se eliminó el clúster al finalizar: se conserva disponible para inspección. CI está configurada; su resultado remoto se consulta en GitHub Actions después del push.

El chart también descubre targets del plano de control (etcd, scheduler y controller-manager) que, con la configuración local predeterminada de Minikube, rechazan el scraping externo. Esos targets pueden aparecer DOWN y generar alertas predeterminadas del chart. Ambos targets de JardínRent están UP; las alertas del trabajo se distinguen por `proyecto="jardinrent"`. Esta limitación del plano de control no se oculta ni se confunde con una caída de las APIs.
