package pe.jardinrent.cotizaciones;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
class CotizacionesTest {
    static HttpServer upstream;
    static java.util.concurrent.ExecutorService workers = Executors.newCachedThreadPool();
    @Autowired WebTestClient web;
    @DynamicPropertySource static void upstream(DynamicPropertyRegistry properties) throws Exception {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.setExecutor(workers);
        upstream.createContext("/herramientas/", exchange -> {
            String id = exchange.getRequestURI().getPath().substring("/herramientas/".length());
            if (id.equals("slow")) try { Thread.sleep(1500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            int status = id.equals("missing") ? 404 : id.equals("broken") ? 500 : 200;
            String body = status != 200 ? "{}" : "{\"id\":\"" + id + "\",\"nombre\":\"Podadora\",\"tarifaDiaria\":25.00,\"disponibles\":" + (id.equals("empty") ? 0 : 10) + "}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        upstream.start();
        properties.add("jardinrent.herramientas-url", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
        properties.add("jardinrent.timeout-ms", () -> 700);
    }
    @AfterAll static void close() { upstream.stop(0); workers.shutdownNow(); }
    @Test void quoteUsesRealHttpAndSupportsReadDelete() {
        var quote = web.post().uri("/cotizaciones").bodyValue(Map.of("herramientaId", "1", "dias", 3))
            .exchange().expectStatus().isCreated().expectBody(Cotizacion.class).returnResult().getResponseBody();
        assertThat(quote).isNotNull();
        assertThat(quote.total()).isEqualByComparingTo("75.00");
        web.get().uri("/cotizaciones/" + quote.id()).exchange().expectStatus().isOk();
        web.get().uri("/cotizaciones").exchange().expectStatus().isOk();
        web.delete().uri("/cotizaciones/" + quote.id()).exchange().expectStatus().isNoContent();
        web.get().uri("/cotizaciones/" + quote.id()).exchange().expectStatus().isNotFound();
    }
    @Test void missingTool() { create("missing", 404); }
    @Test void noStockIsBusinessRejection() { create("empty", 409); }
    @Test void upstreamFailureIsControlled() { create("broken", 503); }
    @Test void timeoutKeepsHealthIndependent() {
        create("slow", 504);
        web.get().uri("/health").exchange().expectStatus().isOk().expectBody().jsonPath("$.service").isEqualTo("cotizaciones");
        String metrics = web.get().uri("/metrics").exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
        assertThat(metrics).contains("cotizaciones_timeouts_total", "cotizaciones_solicitud_seconds_bucket", "cotizaciones_activas");
    }
    @Test void invalidDays() {
        web.post().uri("/cotizaciones").bodyValue(Map.of("herramientaId", "1", "dias", 0)).exchange().expectStatus().isBadRequest();
    }
    private void create(String id, int expected) {
        web.post().uri("/cotizaciones").bodyValue(Map.of("herramientaId", id, "dias", 2)).exchange().expectStatus().isEqualTo(expected);
    }
}
