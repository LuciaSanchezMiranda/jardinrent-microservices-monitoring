package pe.jardinrent.cotizaciones;

import java.math.BigDecimal;
import java.time.Instant;

public record Cotizacion(String id, String herramientaId, String herramienta, int dias, BigDecimal total, Instant creadaEn) { }
