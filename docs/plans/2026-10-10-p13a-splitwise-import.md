# P13a · Importar un grupo de Splitwise — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Tercera propuesta del [estudio de mercado](../research/2026-10-09-estudio-de-mercado.md). Quitar el coste de cambiarse: un grupo de Splitwise entra en Owlsplit con sus personas, gastos y pagos, y **con los mismos saldos**.

**Architecture:**
- `domain`:
  - `SplitwiseCsv.parse` lee la hoja que exporta Splitwise ("Export as spreadsheet").
    - Formato: cinco columnas fijas (fecha, concepto, categoría, coste y moneda, con títulos en cualquier idioma), luego una columna por persona con *pagado − su parte*, y al final una fila de totales sin coste.
    - Las filas de categoría *Payment* con un solo receptor se convierten en pagos.
    - Cada gasto se convierte en un reparto exacto que da los mismos netos:
      - pagan quienes tienen saldo positivo;
      - quienes deben tienen como parte lo que deben;
      - el resto del coste es la parte de quien más pagó.
    - Las categorías se traducen por palabras clave en inglés y español.
    - Se aceptan números con punto o coma decimal.
  - `ImportGroup` crea el grupo en la moneda más usada.
    - Yo entro como la persona que elija o con mi nombre de perfil.
    - El resto entran como miembros sin cuenta.
    - Los gastos y pagos se apuntan en orden de fecha.
    - Las otras monedas usan el tipo de hoy (Frankfurter). Si no hay conexión, esas filas se quedan fuera y se avisa.
- `core:ui`: `FilePicker`/`PickedFile`.
- `composeApp`:
  - `AndroidFilePicker` usa el selector del sistema (SAF), registrado en el `ActivityResultRegistry` para cada elección.
  - `IosFilePicker` usa `UIDocumentPickerViewController` (CSV o texto), con una copia del archivo.
- `feature:groups`: `ImportScreen`/`ImportViewModel`. Explica cómo exportar, deja elegir el archivo y muestra una vista previa con el nombre del grupo, los recuentos, avisos y "¿Quién eres tú?". Después de importar, abre el grupo.
  - Se entra desde "¿Vienes de Splitwise? Importa un grupo" en la pantalla de grupo nuevo.

**Platform:** KMP (Android probado en el emulador; iOS compila en CI).

**Depende de:** P12.

---

## Decisiones

1. **Saldos exactos antes que el reparto original.** El CSV solo da netos por persona. El reparto que se reconstruye puede no coincidir con el que se apuntó en Splitwise (quién pagó qué parte), pero los saldos siempre cuadran, y eso es lo que importa al cambiar de app.
2. **Tipo de cambio de hoy** para las filas en otra moneda, porque Splitwise no exporta el tipo que usó. Se dice en la pantalla cuando no hay conexión.
3. **Sin formato de Tricount por ahora.** Tricount no ofrece una exportación estable y documentada. Si aparece, se añade otro lector junto a `SplitwiseCsv`.

## Tareas

- [x] Task 1: `SplitwiseCsv` (CSV RFC 4180, números, categorías, pagos) con tests. Commit.
- [x] Task 2: `ImportGroup` con tests (saldos iguales a los de Splitwise, monedas, yo). Commit.
- [x] Task 3: `FilePicker` en Android e iOS, pantalla, navegación y textos en es/en. Prueba en el emulador con un CSV: vista previa, importación y saldos. Commit.
