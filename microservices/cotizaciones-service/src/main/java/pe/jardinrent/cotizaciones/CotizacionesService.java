package pe.jardinrent.cotizaciones;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

@Service
public class CotizacionesService {
    private static final Logger log = LoggerFactory.getLogger(CotizacionesService.class);
    private final ConcurrentHashMap<String, Cotizacion> cotizaciones = new ConcurrentHashMap<>();
    private final WebClient client;
    private final Duration timeout;
    private final Counter rechazadas;
    private final Counter timeouts;
    private final Counter unavailable;
    public record Herramienta(String id, String nombre, BigDecimal tarifaDiaria, int disponibles) { }

    public CotizacionesService(WebClient.Builder builder, MeterRegistry registry,
        @Value("${jardinrent.herramientas-url:http://localhost:8081}") String url,
        @Value("${jardinrent.timeout-ms:2000}") long timeoutMs) {
        if (timeoutMs < 1) throw new IllegalArgumentException("HERRAMIENTAS_TIMEOUT_MS debe ser positivo");
        client = builder.baseUrl(url).build();
        timeout = Duration.ofMillis(timeoutMs);
        rechazadas = registry.counter("cotizaciones.rechazadas");
        timeouts = registry.counter("cotizaciones.timeouts");
        unavailable = registry.counter("cotizaciones.dependencia.errores");
        Gauge.builder("cotizaciones.activas", cotizaciones, ConcurrentHashMap::size).register(registry);
    }

    public Mono<Cotizacion> crear(String herramientaId, int dias) {
        return client.get().uri("/herramientas/{id}", herramientaId).retrieve()
            .onStatus(status -> status.value() == 404, response -> Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "La herramienta no existe")))
            .bodyToMono(Herramienta.class)
            .timeout(timeout)
            .onErrorMap(TimeoutException.class, error -> {
                timeouts.increment();
                log.warn("Timeout consultando Herramientas; limite={}ms", timeout.toMillis());
                return new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Herramientas tardó demasiado; intenta nuevamente");
            })
            .onErrorMap(WebClientRequestException.class, error -> dependenciaCaida(error))
            .onErrorMap(WebClientResponseException.class, error -> dependenciaCaida(error))
            .map(tool -> {
                if (tool.disponibles() < 1) {
                    rechazadas.increment();
                    log.info("Cotización rechazada por falta de stock; herramienta={}", herramientaId);
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "La herramienta no tiene unidades disponibles");
                }
                var quote = new Cotizacion(UUID.randomUUID().toString(), tool.id(), tool.nombre(), dias,
                    tool.tarifaDiaria().multiply(BigDecimal.valueOf(dias)).setScale(2, RoundingMode.HALF_UP), Instant.now());
                cotizaciones.put(quote.id(), quote);
                return quote;
            });
    }

    private ResponseStatusException dependenciaCaida(Exception error) {
        unavailable.increment();
        log.warn("Herramientas no disponible: {}", error.getClass().getSimpleName());
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se puede consultar Herramientas en este momento");
    }

    public List<Cotizacion> listar() { return cotizaciones.values().stream().sorted(Comparator.comparing(Cotizacion::creadaEn)).toList(); }
    public Cotizacion obtener(String id) {
        var quote = cotizaciones.get(id);
        if (quote == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cotización inexistente");
        return quote;
    }
    public void eliminar(String id) {
        if (cotizaciones.remove(id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cotización inexistente");
    }
}
