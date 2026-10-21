package pe.jardinrent.cotizaciones;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

@RestController
public class CotizacionesController {
    private final CotizacionesService service;
    public CotizacionesController(CotizacionesService service) { this.service = service; }
    public record NuevaCotizacion(@NotBlank String herramientaId, @NotNull @Min(1) @Max(30) Integer dias) { }

    @GetMapping("/health")
    public Map<String, String> health() { return Map.of("status", "UP", "service", "cotizaciones"); }
    @GetMapping("/cotizaciones")
    public List<Cotizacion> listar() { return service.listar(); }
    @GetMapping("/cotizaciones/{id}")
    public Cotizacion obtener(@PathVariable String id) { return service.obtener(id); }
    @PostMapping("/cotizaciones") @ResponseStatus(HttpStatus.CREATED)
    public Mono<Cotizacion> crear(@Valid @RequestBody NuevaCotizacion input) { return service.crear(input.herramientaId(), input.dias()); }
    @DeleteMapping("/cotizaciones/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String id) { service.eliminar(id); }
}
