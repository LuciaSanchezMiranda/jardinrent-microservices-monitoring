package pe.jardinrent.herramientas;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Service
public class HerramientasService {
    private final ConcurrentHashMap<String, Herramienta> herramientas = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(2);
    private final MeterRegistry registry;
    private final long delayMs;

    public HerramientasService(MeterRegistry registry, @Value("${jardinrent.delay-ms:0}") long delayMs) {
        if (delayMs < 0 || delayMs > 60000) throw new IllegalArgumentException("RESPONSE_DELAY_MS debe estar entre 0 y 60000");
        this.registry = registry;
        this.delayMs = delayMs;
        herramientas.put("1", new Herramienta("1", "Podadora eléctrica", new BigDecimal("25.00"), 10));
        herramientas.put("2", new Herramienta("2", "Desbrozadora", new BigDecimal("35.00"), 4));
        Gauge.builder("herramientas.disponibles", herramientas, map -> map.values().stream().mapToInt(Herramienta::disponibles).sum()).register(registry);
        Gauge.builder("herramientas.delay.seconds", this, service -> service.delayMs / 1000.0).register(registry);
        herramientas.keySet().forEach(this::stockGauge);
    }

    private void stockGauge(String id) {
        Gauge.builder("herramientas.stock", herramientas, map -> map.get(id).disponibles())
            .tag("herramienta_id", id).register(registry);
    }

    /** Retraso reactivo: no bloquea el event loop y nunca se utiliza en /health ni /metrics. */
    public <T> Mono<T> consulta(Supplier<T> action) {
        return Mono.delay(Duration.ofMillis(delayMs)).map(ignored -> action.get());
    }

    public List<Herramienta> listar() {
        return herramientas.values().stream().sorted(Comparator.comparing(Herramienta::id)).toList();
    }

    public Herramienta obtener(String id) {
        var tool = herramientas.get(id);
        if (tool == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Herramienta inexistente");
        return tool;
    }

    public Herramienta crear(String nombre, BigDecimal tarifa, int stock) {
        String id = String.valueOf(sequence.incrementAndGet());
        var tool = new Herramienta(id, nombre.trim(), tarifa, stock);
        herramientas.put(id, tool);
        stockGauge(id);
        return tool;
    }

    public Herramienta cambiarStock(String id, int stock) {
        return herramientas.compute(id, (key, previous) -> {
            if (previous == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Herramienta inexistente");
            return new Herramienta(id, previous.nombre(), previous.tarifaDiaria(), stock);
        });
    }
}
