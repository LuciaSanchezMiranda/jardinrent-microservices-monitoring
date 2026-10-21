package pe.jardinrent.herramientas;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class RequestMetrics implements WebFilter {
    private final MeterRegistry registry;
    public RequestMetrics(MeterRegistry registry) { this.registry = registry; }
    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/herramientas")) return chain.filter(exchange);
        return Mono.defer(() -> {
            String method = exchange.getRequest().getMethod().name();
            String operation = method.equals("POST") ? "crear" : method.equals("PATCH") ? "stock" : method.equals("DELETE") ? "eliminar" : path.equals("/herramientas") ? "listar" : "consultar";
            var sample = Timer.start(registry);
            registry.counter("herramientas.solicitudes", "operacion", operation).increment();
            return chain.filter(exchange).doFinally(signal -> sample.stop(Timer.builder("herramientas.solicitud")
                .tag("operacion", operation).publishPercentileHistogram().register(registry)));
        });
    }
}
