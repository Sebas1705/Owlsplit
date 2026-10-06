# Dominio

Reglas de negocio de Owlsplit. Todo lo que aquí se describe vive en `core:money` y `domain:usecases` (Kotlin común puro) y debe producir **exactamente** el mismo resultado en todos los dispositivos.

## Entidades

| Entidad | Campos | Notas |
|---|---|---|
| `User` | `uid`, `displayName`, `avatar`, `preferredCurrency`, `linkedProviders`, `createdAt` | Uno por cuenta de Firebase Auth |
| `Group` | `id`, `name`, `icon`, `accent`, `baseCurrency`, `startDate?`, `endDate?`, `inviteCode`, `memberUids[]`, `createdBy`, `archivedBy[]`, metadatos de sync | `baseCurrency` no se puede cambiar si hay gastos (MVP) |
| `Member` | `id`, `groupId`, `name`, `avatar`, `defaultWeight`, `userUid?`, `active`, `sortKey`, metadatos de sync | `userUid` nulo = fantasma; un usuario tiene como mucho un miembro por grupo |
| `Expense` | `id`, `groupId`, `title`, `category`, `note?`, `date`, `currency`, `amountMinor`, `fxRateToBase`, `payers`, `split`, `receiptUrl?`, `recurrenceId?`, `createdBy`, metadatos de sync | |
| `Transfer` | `id`, `groupId`, `fromMemberId`, `toMemberId`, `amountMinor`, `currency`, `fxRateToBase`, `date`, `note?`, `createdBy`, metadatos de sync | |
| `ChangeLogEntry` | `id`, `groupId`, `entityType`, `entityId`, `action`, `authorUid`, `diff`, `createdAt` | Solo se añade, nunca se edita |

**Metadatos de sync** (todas las entidades editables): `updatedAt` (hora del servidor), `version` (entero, empieza en 1), `deleted` (booleano), `updatedBy` (uid).

**Identificadores**: UUID v4 generados en el cliente, para poder crear offline sin colisiones.

**Fechas**: `date` es una fecha local (`LocalDate`) sin hora: el gasto “es del 12 de julio” en cualquier zona horaria. `createdAt` y `updatedAt` son instantes UTC.

**Categorías del MVP** (lista fija, con icono): comida, supermercado, transporte, alojamiento, ocio, compras, hogar, suministros, salud, regalos, otros.

## Dinero

- Un importe es `Money(amountMinor: Long, currency: Currency)`. **Nunca** se usan `Float` ni `Double` para dinero.
- `Currency` incluye el código ISO 4217 y sus **decimales** (EUR 2, JPY 0, KWD 3), tomados de una tabla embebida.
- Importe máximo por gasto: 10¹² unidades mínimas (evita desbordes en sumas de grupo).
- Solo se suman importes de la misma divisa; mezclar divisas sin conversión es un error de programación (excepción).

## Reparto

Entrada: `amountMinor` del gasto, lista de beneficiarios ordenada por `sortKey` y luego por `id`, y el tipo de reparto. Salida: `shares: Map<MemberId, Long>` cuya suma es **exactamente** `amountMinor`.

| Tipo | Valor por miembro | Cálculo |
|---|---|---|
| `EQUAL` | — (incluido o no) | `amount / n` a cada uno; resto por restos mayores |
| `WEIGHTS` | entero ≥ 1 | `amount * w_i / Σw` (parte entera); resto por restos mayores |
| `EXACT` | importe en unidades mínimas | Validación: la suma debe ser igual a `amount`; si no, no se puede guardar |
| `PERCENT` | porcentaje en centésimas (0–10000) | Validación: la suma debe ser 10000; luego como `WEIGHTS` con esos valores |

**Asignación del resto (método de restos mayores, determinista)**: tras la división entera, las unidades sobrantes `r` se reparten de una en una a los `r` miembros con **mayor resto fraccionario** (`amount * w_i mod Σw`); a igual resto, gana el primero en el orden (`sortKey`, `id`). Así, 10,00 € entre 3 da 3,34 / 3,33 / 3,33 siempre al mismo miembro en todos los dispositivos.

**Pagadores**: `payers: Map<MemberId, Long>` debe sumar `amountMinor`. Con un solo pagador, el importe completo.

## Divisas

- Cada gasto y transferencia guarda `fxRateToBase`: cuántas unidades de la divisa base vale **una unidad mayor** de la divisa del gasto, como decimal escalado `Long` con **escala 10⁹** (p. ej. 1 USD = 0,923456789 EUR → `923456789`).
- Si la divisa del gasto es la base, `fxRateToBase = 10⁹`.
- El tipo se **congela** al crear el gasto (fuente: Frankfurter, tipos del BCE, caché de 12 h). El usuario puede editarlo. Si no hay tipo disponible (sin red o divisa no cubierta por el BCE), el usuario debe introducirlo; nunca se usa un tipo inventado.
- **Conversión de un gasto a la base, sin romper la suma cero**:
  1. Se calcula el **neto por miembro en la divisa del gasto**: `pagado_i − parte_i` (enteros; suman 0).
  2. Cada neto se convierte: `neto_i × rate × 10^(dec_base − dec_gasto) / 10⁹`, redondeado hacia cero.
  3. El descuadre resultante (suma ≠ 0) se reparte de unidad en unidad por restos mayores, con el mismo desempate determinista. Resultado: netos en base que suman exactamente 0.
- Con aritmética `Long` y valores acotados no hay desbordamiento; si un producto intermedio pudiera desbordar, se usa multiplicación con 128 bits emulada (`multiplyHigh`), cubierta por tests.

## Saldos

`saldo_i = Σ (netos en base de gastos no borrados) + Σ (transferencias: +importe a quien paga, −importe a quien recibe, en base)`.

Invariante: **Σ saldo_i = 0** siempre. Se comprueba en tests de propiedades y con una aserción en debug.

Convención de signo: saldo positivo = **le deben**; negativo = **debe**.

## Simplificación de deudas

Algoritmo voraz determinista (el de Settle Up):

1. Separar acreedores (saldo > 0) y deudores (saldo < 0); descartar saldo 0.
2. Ordenar ambos por importe absoluto descendente; a igual importe, por `sortKey` e `id`.
3. Emparejar el mayor deudor con el mayor acreedor; pago = mínimo de ambos absolutos; restar; reinsertar el que quede con saldo; repetir.

Propiedades garantizadas (y testeadas): conserva exactamente los saldos netos; como mucho `n − 1` pagos; mismo resultado ante cualquier permutación de entrada. No garantiza el mínimo absoluto de pagos (problema NP-difícil); es la misma aproximación que usan las apps del sector.

Opcional del MVP: si el usuario lo desactiva en el grupo, “Cómo saldar” muestra las deudas sin simplificar (cada deudor paga a quien le adelantó).

## Invariantes de validación (cliente y reglas)

- `title`: 1–80 caracteres; `note`: ≤ 500.
- `amountMinor` > 0 y ≤ 10¹².
- `payers` y `shares`: claves = miembros existentes del grupo, valores ≥ 0, sumas exactas.
- Un gasto debe tener al menos un beneficiario con parte > 0.
- `fromMemberId ≠ toMemberId` en transferencias.
- `defaultWeight` entre 1 y 100.
