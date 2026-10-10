# Marca Owlsplit

Todo lo que identifica a Owlsplit sale de un solo sitio: **[`brand/tokens.json`](../brand/tokens.json)**. Con ese archivo se rigen:

- **La app**: los colores de `core/ui` [`Tokens.kt`](../core/ui/src/commonMain/kotlin/es/sebas1705/owlsplit/core/ui/theme/Tokens.kt) y el logotipo `OwlsplitLogo`. `BrandTokensTest` falla si `Tokens.kt` y `tokens.json` no coinciden.
- **La web** (owlsplit.web.app): `firebase/hosting/brand.css` y `favicon.svg` se generan a partir de él.
- **Los iconos de la app**: el icono adaptativo y monocromo de Android, el de Android 7 y el de iOS se generan a partir de él.

## Logotipo

![Owlsplit](../brand/logo.svg)

Búho geométrico sobre una retícula de 24 unidades:

- un cuadrado de `accent` con esquinas de 7 unidades;
- dos anillos blancos por ojos, de radio 3,2 y trazo 1,8, centrados en (8,5, 11) y (15,5, 11);
- un pico triangular de `brandBeak`, de (10,8, 15,6) a (13,2, 15,6) con la punta en (12, 18).

Archivos de referencia:

| Archivo | Uso |
|---|---|
| [`brand/logo.svg`](../brand/logo.svg) | El logotipo con sus esquinas: web, documentos y favicon |
| [`brand/icon.svg`](../brand/icon.svg) | Cuadrado completo, sin esquinas, para las tiendas y para iOS (que redondea solo) |

Iconos de la app:

- **Android 8 y posteriores**: icono adaptativo. El fondo es `accent`, y los ojos y el pico ocupan la zona segura de 66 dp, dibujados a escala 3 en el centro del lienzo de 108 dp. Android 12 lo usa también en la pantalla de arranque.
- **Android 13 y posteriores**: versión monocroma para los iconos temáticos.
- **Android 7**: el logotipo redondeado.
- **iOS**: `AppIcon` de 1024 px, opaco.

Reglas de uso:

- El logotipo no se usa como mascota ilustrada dentro de las pantallas de trabajo; solo aparece en el icono, el arranque, los estados vacíos y la bienvenida.
- `brandBeak` se usa exclusivamente para el pico.
- No se cambian sus proporciones ni su color.

## Colores

Los valores están en `tokens.json`, en tema claro y oscuro. Los roles de cada uno se explican en [DESIGN_SYSTEM](DESIGN_SYSTEM.md#color). En resumen:

- **Azul Owlsplit** `#3B4BF5` (`accent`): botones, selección y el fondo del logotipo. En texto sobre fondo oscuro se usa `#8C96FF` (`accentText`).
- **Ámbar del pico** `#E8B04B` (`brandBeak`): solo en el logotipo.
- **Verde de saldo** `#087A54` (claro) y `#34C38F` (oscuro) (`positive`): lo que te deben.
- **Neutros**: fondo `#F2F3F7` y `#0B0C10`, superficie `#FFFFFF` y `#16181F`, texto `#0B0C10` y `#F5F6FA`.

En la web, `brand.css` define estos colores como variables CSS, por ejemplo `--os-accent` o `--os-text-primary`. Los nombres son los de `tokens.json` en kebab-case, y `style.css` les pone los nombres que usan las páginas.

## Tipografía

- **App**: Inter, con cifras tabulares en los importes.
- **Web**: Inter para el texto, Bricolage Grotesque para los titulares e IBM Plex Mono para datos como el SHA-256.

## Cambiar la marca

1. Edita `brand/tokens.json`.
2. Ejecuta `node scripts/brand.mjs` para regenerar la web y los iconos de Android. Con `--png` también regenera el icono de iOS; necesita Edge o Chrome.
3. Si cambiaste colores de la app, actualiza `Tokens.kt`; `BrandTokensTest` te dirá cuáles faltan. Si cambiaste la geometría del logotipo, actualiza `OwlsplitLogo`.

La CI ejecuta `node scripts/brand.mjs --check` y falla si algún archivo generado no está al día.
