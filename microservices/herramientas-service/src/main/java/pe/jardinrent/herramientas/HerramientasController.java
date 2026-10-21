package pe.jardinrent.herramientas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
public class HerramientasController {
    private final HerramientasService service;
    public HerramientasController(HerramientasService service) { this.service = service; }
    public record NuevaHerramienta(@NotBlank @Size(max = 100) String nombre,
        @NotNull @DecimalMin("0.01") BigDecimal tarifaDiaria, @NotNull @Min(0) Integer disponibles) { }
    public record Stock(@NotNull @Min(0) Integer disponibles) { }

    @GetMapping("/health")
    public Map<String, String> health() { return Map.of("status", "UP", "service", "herramientas"); }

    @GetMapping("/herramientas")
    public Mono<List<Herramienta>> listar() { return service.consulta(service::listar); }

    @GetMapping("/herramientas/{id}")
    public Mono<Herramienta> obtener(@PathVariable String id) { return service.consulta(() -> service.obtener(id)); }

    @PostMapping("/herramientas") @ResponseStatus(HttpStatus.CREATED)
    public Mono<Herramienta> crear(@Valid @RequestBody NuevaHerramienta input) {
        return Mono.fromSupplier(() -> service.crear(input.nombre(), input.tarifaDiaria(), input.disponibles()));
    }

    @PatchMapping("/herramientas/{id}/stock")
    public Mono<Herramienta> stock(@PathVariable String id, @Valid @RequestBody Stock input) {
        return Mono.fromSupplier(() -> service.cambiarStock(id, input.disponibles()));
    }
}
