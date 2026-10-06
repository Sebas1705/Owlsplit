# Sistema de diseño

Dirección **fintech limpia** (estilo Revolut/Monzo), decidida en [ADR 0006](adr/0006-fintech-visual-direction.md). Mockup de referencia: [mockups/owlo-fintech-v3.html](mockups/owlo-fintech-v3.html).

## Principios

1. **Las cifras mandan.** El importe o saldo relevante es siempre lo más grande de la pantalla.
2. **Un solo acento.** El azul se reserva para la acción principal y la selección. Todo lo demás es neutro.
3. **Verde para lo bueno, nunca rojo para las deudas.** Lo que te deben va en verde; lo que debes, en el color de texto normal con signo “−”. Sin alarmismo.
4. **Contenedores, no tarjetas sueltas.** Listas en filas dentro de contenedores redondeados sobre fondo gris claro (o grafito sobre negro).
5. **Claro y oscuro son de primera.** Toda pantalla se diseña y prueba en ambos.

Se implementa sobre **Material 3** de Compose Multiplatform con un `OwloTheme` propio (`core:ui`): los componentes de M3 reciben los tokens de abajo y los componentes propios solo usan tokens, nunca colores literales.

## Color

| Token | Claro | Oscuro | Uso |
|---|---|---|---|
| `background` | `#F2F3F7` | `#0B0C10` | Fondo de pantalla |
| `surface` | `#FFFFFF` | `#16181F` | Contenedores, barra inferior |
| `surfaceVariant` | `#E4E6EE` | `#1C1E26` | Campo de búsqueda, control segmentado, chips |
| `divider` | `#ECEEF3` | `#22252E` | Separadores y pistas de barras |
| `textPrimary` | `#0B0C10` | `#F5F6FA` | Texto e importes; también deudas |
| `textSecondary` | `#5B5F6B` | `#8A8E99` | Subtítulos, etiquetas |
| `textMuted` | `#8A8E99` | `#5B5F6B` | Pistas y metadatos, solo ≥ 12 sp o no esencial |
| `accent` | `#3B4BF5` | `#3B4BF5` | Botón principal, selección, avatares incluidos |
| `onAccent` | `#FFFFFF` | `#FFFFFF` | Texto sobre `accent` (contraste 6:1) |
| `accentText` | `#3B4BF5` | `#8C96FF` | Enlaces y texto de acento sobre superficies |
| `accentContainer` | `#E8EBFF` | `#232750` | Fondo de iconos de acción y de grupo por defecto |
| `positive` | `#087A54` | `#34C38F` | Saldos a favor (contraste ≥ 4.5:1 en ambos temas) |
| `positiveFill` | `#0E9F6E` | `#34C38F` | Barras de saldo a favor (elemento gráfico) |
| `negativeFill` | `#0B0C10` | `#F5F6FA` | Barras de saldo en contra |
| `warning` | `#B26A00` | `#F2B84B` | Pendiente de sincronizar, conflictos |
| `error` | `#C4372B` | `#FF7A6E` | Solo errores de validación o de sistema, nunca deudas |
| `brandBeak` | `#E8B04B` | `#E8B04B` | Exclusivo del logotipo |

**Acentos de grupo** (fondo del icono / color del icono), en claro: azul `#E8EBFF/#3B4BF5`, naranja `#FFF1E5/#D9661A`, violeta `#F3E8FF/#8B3FD9`, verde `#E3F5EE/#087A54`, rosa `#FDE8F0/#C2306A`, ámbar `#FFF4D6/#9A6400`, cian `#E0F4FA/#0A7893`, grafito `#ECEEF3/#3A3E4A`. En oscuro, fondo con el color al 18 % sobre `surface` y el icono un tono más claro. Solo tiñen el icono del grupo, nunca la pantalla.

## Tipografía

**Inter** (OFL, embebida en `core:ui`) con cifras tabulares (`fontFeatureSettings = "tnum"`) en todo importe.

| Estilo | Tamaño / peso | Uso |
|---|---|---|
| `amountDisplay` | 44 sp / 700, tracking −3 % | Importe del editor, balance de Inicio |
| `amountHeadline` | 30 sp / 700, tracking −3 % | Saldo en la cabecera del grupo |
| `title` | 17 sp / 600 | Títulos de pantalla |
| `bodyStrong` | 14 sp / 600 | Nombre en filas, importes en filas |
| `body` | 14 sp / 400 | Texto general |
| `label` | 12 sp / 500 | Botones secundarios, chips, pestañas |
| `caption` | 11 sp / 400 | Metadatos (“5 personas · hace 2 h”) |

En los importes grandes la parte decimal se muestra al 60 % del tamaño (y en `textSecondary` en el editor), como en el mockup.

## Forma y espaciado

- Radios: contenedores **16 dp**, controles segmentados y chips **12 dp**, botones principales en **píldora**, botones de acción rápida y avatares **circulares**.
- Escala de espaciado: 4 · 8 · 12 · 16 · 24 · 32 dp. Margen lateral de pantalla 16 dp; contenedores a 12 dp del borde.
- Filas de lista: 56 dp de alto mínimo; avatar o icono de 36 dp.
- Áreas táctiles ≥ 48 dp aunque el elemento visible sea menor.
- Sin sombras: la profundidad viene del contraste entre `background` y `surface`.

## Iconografía

**Tabler Icons** en estilo contorno (MIT), vía el port KMP `compose-icons`. Trazo 1.75–2 px, tamaños 20 y 24 dp. Las categorías de gasto y los iconos de grupo salen del mismo set.

## Logotipo

Búho geométrico: cuadrado redondeado en `accent` con dos anillos blancos por ojos y un pico en `brandBeak`. Usos: icono de la app, splash, estados vacíos y pantalla de bienvenida. **No** aparece como mascota ilustrada dentro de las pantallas de trabajo.

## Componentes (`core:ui`)

| Componente | Descripción |
|---|---|
| `BalanceHero` | Balance grande centrado, etiqueta y chip de estado de sync |
| `QuickActionButton` | Botón circular de 48 dp sobre `surface` con etiqueta debajo |
| `OwloContainer` | Contenedor `surface` de 16 dp con título opcional |
| `GroupRow` | Icono de grupo, nombre, metadatos y saldo coloreado |
| `SegmentedControl` | Píldora `surfaceVariant` con la opción activa en `surface` |
| `MemberBalanceRow` | Avatar, nombre, barra fina de 4 dp proporcional y saldo |
| `SettleSuggestionRow` | “X te debe Y” con botón píldora “Saldar” |
| `AmountDisplay` | Importe con decimales reducidos y selector de divisa |
| `AmountKeypad` | Teclado numérico propio con separador decimal local y borrar |
| `AvatarToggle` | Avatar que alterna incluido (`accent`) / excluido (`surfaceVariant`) |
| `SyncStatusChip` | Sincronizado · N pendientes · Sin conexión · Error |
| `PrimaryPillButton` | Botón principal ancho en píldora, `accent` |
| `EmptyState` | Logotipo, titular, una línea y una acción |
| `Avatar` | Foto o iniciales sobre tono neutro; 24, 30, 36 y 48 dp |

Cada componente tiene preview en claro y oscuro y test de captura.

## Movimiento

- Saldos que cambian: el número **rueda** hasta el nuevo valor (300 ms, desaceleración).
- Saldar: la fila se desliza y desaparece y el saldo de la cabecera rueda a su nuevo valor; confirmación háptica ligera.
- Transiciones de pantalla estándar de la plataforma (gesto atrás nativo en iOS).
- Con “reducir movimiento” activo: cambios instantáneos, sin rodar ni deslizar.

## Tono del texto

Cercano y directo, en segunda persona y sin tecnicismos: “Te deben 23,50 €”, “Luis te debe”, “Todo saldado”, “1 cambio pendiente”. Sin signos de exclamación en mensajes del sistema. Errores: qué pasó y qué hacer (“Los importes no suman el total. Faltan 3,20 €.”).
