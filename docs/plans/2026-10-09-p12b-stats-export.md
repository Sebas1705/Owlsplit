# P12b · Resumen del grupo y exportar a CSV/PDF — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Segunda propuesta del [estudio de mercado](../research/2026-10-09-estudio-de-mercado.md), que Splitwise solo ofrece en Pro. Una pantalla de **Resumen** por grupo y la opción de compartir las cuentas como **hoja de cálculo (CSV)** o como **resumen en PDF** para imprimir, sin servidor y sin coste.

**Architecture:**
- `domain`: `GroupStats.of(...)`, que calcula en la moneda base:
  - el total, el número de gastos y la media por día (con las fechas del viaje si las hay);
  - lo que pagó cada persona y lo que le toca;
  - el reparto por categoría y por mes.

  Las transferencias no cuentan como gasto. Los gastos inconsistentes se ignoran, igual que en los saldos. Las funciones `Expense.inBase` y `Expense.sharesInBase` son públicas para el CSV.
- `core:common`: `SimplePdf`, un escritor de PDF mínimo en Kotlin común:
  - páginas A4, Helvetica normal y negrita, y rectángulos para las barras;
  - texto en WinAnsi (español y el resto de Europa occidental, más «€»), con anchos de Helvetica para alinear números a la derecha;
  - sin dependencias, así que da el mismo archivo en Android e iOS.
- `core:ui`:
  - `FileSharer`, que comparte un archivo creado en memoria;
  - `DateTexts`/`rememberDateTexts()`, que formatean fechas y meses fuera de la composición.
- `composeApp`:
  - `AndroidFileSharer`: escribe en `cache/exports` y lo comparte con un `FileProvider` (`${applicationId}.exports`, `res/xml/file_paths.xml`);
  - `IosFileSharer`: escribe en el directorio temporal y abre `UIActivityViewController`.
- `feature:groups`:
  - `StatsScreen`/`StatsViewModel`: cifras, por persona, por categoría y por mes (este solo con más de un mes). El menú «Exportar» ofrece PDF y CSV.
  - `GroupReport.csv`: una fila por gasto y por pago, y una columna por miembro con su parte en la moneda base. Así, lo pagado menos su columna da su saldo. En idiomas con coma decimal se separa con «;». Lleva BOM UTF-8 para Excel.
  - `GroupReport.pdf`: cifras, personas, categorías, meses, lo que falta por saldar y la lista de gastos.
  - Se entra desde «Resumen» en el menú del grupo.

**Platform:** KMP (Android probado en el emulador; iOS compila en CI).

**Depende de:** P12a.

---

## Decisiones

1. **PDF propio en vez de APIs de plataforma.** `PdfDocument` (Android) y `UIGraphicsPDFRenderer` (iOS) exigirían dos implementaciones, y la de iOS no se puede probar aquí. Un informe de texto y barras cabe en unos 200 líneas comunes con tests.
2. **El CSV cuadra con los saldos.** Los pagos aparecen como filas, quien paga en «Pagado por» y quien recibe en su columna. Sumando, cada persona obtiene su saldo, y el archivo sirve para comprobar las cuentas.
3. **Importes truncados al céntimo** en las conversiones del resumen. Puede haber un céntimo de diferencia con los saldos, que reparten el resto, y está documentado en `GroupStats`.
4. **Un archivo cada vez** en `cache/exports`: se borra el anterior al exportar. El `FileProvider` solo expone esa carpeta.

## Tareas

- [x] Task 1: `GroupStats` con tests. Commit.
- [x] Task 2: `SimplePdf` con tests (xref, escapes, anchos). Commit.
- [x] Task 3: `FileSharer` en Android (FileProvider) e iOS. Commit.
- [x] Task 4: `GroupReport` (CSV y PDF) con tests, `StatsScreen`, navegación y textos en es/en. Prueba en el emulador: resumen, compartir PDF y CSV, y vista previa de impresión del PDF. Commit.
