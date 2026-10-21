package pe.jardinrent.herramientas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
class HerramientasTest {
    @Autowired WebTestClient web;
    @Test void healthAndMetrics() {
        web.get().uri("/health").exchange().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("UP").jsonPath("$.service").isEqualTo("herramientas");
        String metrics = web.get().uri("/metrics").exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
        assertThat(metrics).contains("herramientas_disponibles", "herramientas_delay_seconds", "herramientas_stock");
    }
    @Test void createReadAndChangeStock() {
        var tool = web.post().uri("/herramientas").bodyValue(new HerramientasController.NuevaHerramienta("Rastrillo", new java.math.BigDecimal("8.50"), 3))
            .exchange().expectStatus().isCreated().expectBody(Herramienta.class).returnResult().getResponseBody();
        assertThat(tool).isNotNull();
        web.get().uri("/herramientas/" + tool.id()).exchange().expectStatus().isOk().expectBody().jsonPath("$.nombre").isEqualTo("Rastrillo");
        web.patch().uri("/herramientas/" + tool.id() + "/stock").bodyValue(new HerramientasController.Stock(0))
            .exchange().expectStatus().isOk().expectBody().jsonPath("$.disponibles").isEqualTo(0);
        web.get().uri("/herramientas").exchange().expectStatus().isOk();
    }
    @Test void invalidInputAndMissingTool() {
        web.post().uri("/herramientas").bodyValue(java.util.Map.of("nombre", "", "tarifaDiaria", -2, "disponibles", -1)).exchange().expectStatus().isBadRequest();
        web.get().uri("/herramientas/inexistente").exchange().expectStatus().isNotFound();
        web.patch().uri("/herramientas/1/stock").bodyValue(java.util.Map.of("disponibles", -1)).exchange().expectStatus().isBadRequest();
    }
}
