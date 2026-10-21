# Tres casuísticas reproducibles

Ejecutar desde Ubuntu, raíz del repositorio, después de `setup.sh`. Abrir Grafana, rango últimos 15 minutos, refresco 5 segundos. Las alertas necesitan el período `for: 30s` más el siguiente ciclo de evaluación; generar tráfico durante 120 segundos. Esperar también la resolución antes del siguiente caso.

## 1. Configuración inválida: Herramientas caído

**Contexto narrativo:** un cambio de despliegue configura texto en lugar de un número para el puerto HTTP del catálogo.

**Síntoma:** no se pueden calcular cotizaciones porque no hay una réplica de Herramientas lista para recibir tráfico; B devuelve 503 si se rechaza la conexión o 504 si la red no responde antes del timeout. Ambos son errores controlados. Las cotizaciones existentes y el health propio de B siguen disponibles.

**Activación:**

```bash
bash scripts/scenario.sh crash
bash load-testing/stress.sh http://localhost:18082 120 2
minikube -p jardinrent kubectl -- --context=jardinrent -n jardinrent get pods
```

**Diagnóstico:** el pod nuevo falla al arrancar por `SERVER_PORT` y entra en `CrashLoopBackOff`. El laboratorio usa una réplica y estrategia `Recreate`, por lo que no queda una réplica anterior ocultando la caída. El panel de réplicas de Herramientas baja a cero y `HerramientasCaido` pasa de pending a FIRING. Consultar los logs y `describe pod` del nuevo pod.

**Resolución:**

```bash
bash scripts/scenario.sh crash-fix
bash scripts/verify.sh
```

**Lección técnica:** readiness controla si el pod recibe tráfico; liveness reinicia un contenedor que falla repetidamente su prueba. En este caso el proceso termina por configuración inválida y Kubernetes lo reinicia: el error inicial no lo provoca el probe. Startup probe protege el arranque normal de Spring Boot. Un proceso vivo no garantiza una dependencia saludable.

## 2. Herramientas lento: timeouts en Cotizaciones

**Contexto narrativo:** el catálogo experimenta una demora de seis segundos, por encima de los dos segundos que Cotizaciones está dispuesto a esperar.

**Síntoma:** solicitudes de cotización reciben HTTP 504 con explicación controlada; el proceso no cae ni queda esperando indefinidamente.

**Activación:**

```bash
bash scripts/scenario.sh slow
bash load-testing/stress.sh http://localhost:18082 120 4
curl -fsS http://localhost:18081/health
```

**Diagnóstico:** `herramientas_delay_seconds` sube a 6; aumentan `cotizaciones_timeouts_total` y la latencia p95 de creación. La alerta `CotizacionesConTimeout` dispara. El health y las métricas de A continúan respondiendo sin esperar los seis segundos.

**Resolución:**

```bash
bash scripts/scenario.sh slow-fix
bash load-testing/stress.sh http://localhost:18082 90 2
bash scripts/verify.sh
```

**Lección técnica:** la latencia se propaga por llamadas HTTP, pero los timeouts limitan cuánto espera B. Se usa `Mono.delay`, no `Thread.sleep`, en producción para no bloquear el event loop. El histograma de B registra también solicitudes fallidas; las tasas en ventanas móviles tardan en volver a cero después de la recuperación.

## 3. Pérdida de oportunidades: podadoras sin disponibilidad

**Contexto narrativo:** la podadora, herramienta más solicitada del vivero, se queda sin unidades disponibles y el equipo comercial pierde solicitudes de alquiler.

**Síntoma:** la creación de cotizaciones para la herramienta 1 responde HTTP 409, mientras las APIs permanecen sanas.

**Activación:**

```bash
bash scripts/scenario.sh stock
bash load-testing/stress.sh http://localhost:18082 120 4
```

**Diagnóstico:** `herramientas_stock{herramienta_id="1"}` vale cero, aumentan las cotizaciones rechazadas y dispara `HerramientaSinStock`. Los targets siguen UP y las réplicas siguen disponibles. La infraestructura está sana, pero el negocio no está atendiendo esa demanda.

**Resolución:**

```bash
bash scripts/scenario.sh stock-fix
bash scripts/verify.sh
```

**Lección técnica:** monitorear sólo CPU o UP/DOWN no detecta oportunidades comerciales perdidas. La API verifica disponibilidad para cotizar; una cotización no es una reserva y no descuenta stock. La resolución del laboratorio repone diez unidades de la herramienta demo 1. Los datos son volátiles: los reinicios de A restauran el catálogo inicial.
