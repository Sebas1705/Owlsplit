# 0005 · Dinero en enteros de unidades mínimas

- **Estado:** aceptada · 2026-10-07

## Contexto
Varios dispositivos calculan los saldos por separado, sin servidor que arbitre. Cualquier diferencia de redondeo produce saldos distintos según quién mire, y además la suma del grupo dejaría de ser cero.

## Decisión
- Importes como `Long` en **unidades mínimas** según los decimales ISO 4217 de cada divisa.
- Repartos con **restos mayores** y desempate determinista (`sortKey`, `id`).
- Tipos de cambio como `Long` con escala 10⁹, **congelados** en cada gasto; conversión del neto por miembro con corrección determinista del descuadre.
- Invariante: la suma de saldos del grupo es exactamente 0, verificada con tests de propiedades.

Reglas exactas en [DOMAIN.md](../DOMAIN.md).

## Alternativas descartadas
- **`Double`**: errores de representación y resultados distintos según el orden de las operaciones.
- **BigDecimal multiplataforma** (p. ej. ionspin bignum): correcto, pero más pesado e innecesario con valores acotados en `Long`.
