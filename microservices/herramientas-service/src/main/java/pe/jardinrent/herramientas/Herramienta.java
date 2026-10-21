package pe.jardinrent.herramientas;

import java.math.BigDecimal;

public record Herramienta(String id, String nombre, BigDecimal tarifaDiaria, int disponibles) { }
