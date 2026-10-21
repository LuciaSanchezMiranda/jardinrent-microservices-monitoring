# Informe técnico · JardínRent

**Autora:** Lucía Sánchez Miranda. **Modalidad:** individual.

JardínRent cotiza el alquiler de herramientas de jardinería. El catálogo conoce la tarifa y las unidades disponibles; Cotizaciones consulta ese catálogo por HTTP antes de decidir si puede emitir una oferta y calcular el importe. No se utiliza una base de datos ni se reutiliza código de los proyectos anteriores o del taller. Una cotización no reserva inventario; los mapas de ambos procesos se reinician cuando sus contenedores arrancan nuevamente.

## A. Conceptos explicados con el código del proyecto

### 1. Micrometer

Micrometer proporciona una API de instrumentación para Java. En `HerramientasService` uso `MeterRegistry`, sin escribir directamente el formato de texto que entiende Prometheus. `micrometer-registry-prometheus` adapta estas mediciones al backend de monitoreo. Así la lógica de negocio no queda acoplada a la librería cliente de un proveedor concreto.

### 2. Counter, Gauge y Timer

- **Counter:** acumula eventos, no disminuye durante la vida del proceso. En `CotizacionesService`, `cotizaciones.timeouts` se incrementa cuando la llamada a Herramientas excede el límite. Si el pod reinicia, el contador comienza nuevamente; `rate` e `increase` manejan esos reinicios.
- **Gauge:** muestra un valor actual, que puede aumentar o disminuir. `herramientas.disponibles` suma las unidades del catálogo; `herramientas.stock` muestra las unidades por herramienta. `herramientas.delay.seconds` muestra el retraso artificial configurado.
- **Timer:** registra duración y cantidad de operaciones. `RequestMetrics` empieza una muestra al recibir una petición y la termina en `doFinally`, incluyendo respuestas fallidas. Se publican histogramas para calcular p95, porque promediar latencias ocultaría solicitudes excepcionalmente lentas.

Los nombres Java con puntos se normalizan en Prometheus: `cotizaciones.timeouts` se expone como `cotizaciones_timeouts_total`; el Timer `cotizaciones.solicitud` genera `_seconds_count`, `_seconds_sum` y `_seconds_bucket`. Las etiquetas son operaciones finitas (`crear`, `listar`, `consultar`, `eliminar`, `stock`), no URLs con UUID de cada cliente. Los identificadores de herramientas se usan únicamente en el gauge de inventario del dominio.

### 3. ServiceMonitor y descubrimiento

Un ServiceMonitor es un recurso de la API del Prometheus Operator que describe qué Services raspar y en qué ruta/puerto. En `k8s/monitoring/herramientas-servicemonitor.yaml`, el selector coincide con las etiquetas del **Service** `app: herramientas` y `proyecto: jardinrent`. El Service, a su vez, selecciona pods con `app: herramientas`. La cadena es Prometheus → ServiceMonitor → Service → endpoints/pods.

El campo `endpoints.port` es `http`, el nombre del puerto del Service, y `path` es `/metrics`. No se coloca el número del NodePort en el ServiceMonitor. Prometheus usa la red interna; los NodePorts y port-forwards son para acceder desde fuera.

### 4. Liveness, readiness y startup

Liveness responde si Kubernetes debe reiniciar el contenedor; readiness responde si puede enviarle tráfico. Startup permite que Spring Boot termine de arrancar antes de activar las otras pruebas. Ambos servicios tienen `/health` propio, rápido y sin consultar dependencias. El retraso artificial sólo afecta consultas del catálogo: nunca bloquea salud ni métricas.

La primera casuística usa una configuración inválida de puerto. El proceso cae por ese error y el kubelet lo reinicia; no se atribuye incorrectamente el fallo inicial al probe. Con `Recreate` y una réplica, la caída queda visible. Es una decisión deliberada para el laboratorio, no un diseño de alta disponibilidad para producción.

### 5. Daemon Docker de Minikube

Docker Desktop y el runtime Docker del nodo son entornos de imágenes diferentes. En `setup.sh`, un subshell ejecuta `eval "$(minikube -p "$PROFILE" docker-env --shell bash)"` antes de ambos builds. Las imágenes quedan dentro del nodo que las ejecutará. `imagePullPolicy: Never` evita buscar esos tags locales en Docker Hub. El subshell impide dejar cambiado el daemon de otras terminales o proyectos.

### 6. Selectores del chart

Sin configurar descubrimiento, el chart puede generar un selector limitado a sus etiquetas de Helm y Prometheus no descubre un ServiceMonitor propio. En este proyecto `serviceMonitorSelectorNilUsesHelmValues: false`, `serviceMonitorSelector: {}` y `serviceMonitorNamespaceSelector: {}` permiten seleccionar monitores de todos los namespaces. Se configuran igualmente `ruleSelectorNilUsesHelmValues: false`, `ruleSelector: {}` y `ruleNamespaceSelector: {}` para descubrir `jardinrent-alerts`. No basta con aplicar un YAML si Prometheus no lo selecciona.

## B. PromQL del sistema

### Solicitudes por minuto de B en los últimos cinco minutos

```promql
sum(rate(cotizaciones_solicitudes_total[5m])) * 60
```

`rate` devuelve solicitudes por segundo; multiplicar por sesenta expresa el resultado por minuto. El contador incluye operaciones exitosas y fallidas.

### Latencia p95 del endpoint POST /cotizaciones

```promql
histogram_quantile(
  0.95,
  sum by (le) (
    rate(cotizaciones_solicitud_seconds_bucket{operacion="crear"}[5m])
  )
)
```

El resultado está en segundos y requiere histogramas y al menos dos muestras de scraping. El dashboard usa una ventana de un minuto para que la demostración local responda más rápido; el informe conserva los cinco minutos solicitados.

### UP/DOWN de ambos servicios

```promql
up{namespace="jardinrent",service=~"herramientas|cotizaciones"}
```

UP vale 1 si el scraping funciona y 0 si falla. Si un target desaparece completamente, su serie puede desaparecer; por eso las alertas de caída utilizan también el estado de réplicas del Deployment, que permanece observable mediante kube-state-metrics.

### Pregunta de negocio: oportunidades perdidas por falta de disponibilidad

```promql
sum(increase(cotizaciones_rechazadas_total[10m]))
```

Este contador se incrementa cuando una herramienta sin stock impide emitir una cotización. No confunde rechazos de negocio con timeouts de infraestructura.

## C. Casuísticas y evidencias

Los seis apartados completos de cada caso y sus comandos están en [casuisticas.md](casuisticas.md). Ejecutar una situación a la vez, generar tráfico, esperar el `for` de la alerta y registrar la recuperación antes de continuar.

1. Configuración inválida y `CrashLoopBackOff`: panel de réplicas en cero y alerta `HerramientasCaido`.
2. Retraso de seis segundos frente a timeout de dos: gauge de retraso, HTTP 504 y `CotizacionesConTimeout`.
3. Stock de podadoras agotado: APIs UP, HTTP 409, rechazos comerciales y `HerramientaSinStock`.

Las capturas y los resultados de la ejecución se indexan en [evidencias/README.md](evidencias/README.md). No se sustituyen capturas reales por imágenes simuladas ni se presentan pruebas pendientes como aprobadas.

## D. Evidencias del sistema

- Prometheus `/targets`: Herramientas y Cotizaciones deben estar UP.
- Dashboard Grafana: once paneles configurados con consultas reales. Las series de alerta pueden estar vacías cuando no hay ninguna activa.
- `kubectl get pods`: pods de aplicación y monitoreo funcionando.
- Alertas: al menos una del proyecto en FIRING durante una casuística.
- Dashboard JSON: [dashboard.json](dashboard.json), provisionado mediante ConfigMap por `setup.sh` e importable en Grafana.

Consultar el registro de validación y evidencias para distinguir código compilado, pruebas automáticas, integración desplegada y casuísticas efectivamente ejecutadas.

## Limitaciones del laboratorio

Datos en memoria, una réplica por servicio y sin autenticación de APIs; no es un sistema de reservas ni una implementación para producción. Los datos demo se restauran al reiniciar Herramientas. El dashboard se provisiona desde el JSON original; si se edita en Grafana, exportar la nueva versión al repositorio antes de reconstruir el ConfigMap. Las URLs NodePort dependen del driver; `access.sh` proporciona accesos locales estables para Windows/WSL.

## Fuentes oficiales

- Spring Boot 3.5: https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html
- Micrometer: https://docs.micrometer.io/micrometer/reference/concepts.html
- Probes: https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
- Prometheus Operator: https://prometheus-operator.dev/docs/api-reference/api/
- Chart y selectores: https://github.com/prometheus-community/helm-charts/blob/main/charts/kube-prometheus-stack/values.yaml
- PromQL: https://prometheus.io/docs/prometheus/latest/querying/functions/
