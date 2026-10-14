# Owlsplit brand

Everything that identifies Owlsplit comes from a single place: **[`brand/tokens.json`](../brand/tokens.json)**. That file drives:

- **The app**: the colors in `core/ui` [`Tokens.kt`](../core/ui/src/commonMain/kotlin/es/sebas1705/owlsplit/core/ui/theme/Tokens.kt) and the `OwlsplitLogo` logo. `BrandTokensTest` fails if `Tokens.kt` and `tokens.json` don't match.
- **The website** (owlsplit.web.app): `firebase/hosting/brand.css` and `favicon.svg` are generated from it.
- **The app icons**: the Android adaptive and monochrome icon, the Android 7 icon and the iOS icon are generated from it.

## Logo

![Owlsplit](../brand/logo.svg)

A geometric owl on a 24-unit grid:

- an `accent` square with 7-unit corners;
- two white rings for eyes, radius 3.2 and stroke 1.8, centered at (8.5, 11) and (15.5, 11);
- a triangular `brandBeak` beak, from (10.8, 15.6) to (13.2, 15.6) with its tip at (12, 18).

Reference files:

| File | Use |
|---|---|
| [`brand/logo.svg`](../brand/logo.svg) | The logo with its rounded corners: web, documents and favicon |
| [`brand/icon.svg`](../brand/icon.svg) | Full square, no corners, for the stores and for iOS (which rounds it itself) |

App icons:

- **Android 8 and later**: adaptive icon. The background is `accent`, and the eyes and beak fill the 66 dp safe zone, drawn at 3x scale in the center of the 108 dp canvas. Android 12 also uses it on the splash screen.
- **Android 13 and later**: monochrome version for themed icons.
- **PNG at each density** (`mipmap-mdpi` … `xxxhdpi`): the rounded logo. Used by Android 7, which has no adaptive icons, and by Firebase App Distribution (App Tester), which can't read adaptive icons: without the PNGs it would show a generic icon.
- **iOS**: 1024 px `AppIcon`, opaque.

Usage rules:

- The logo is not used as an illustrated mascot inside working screens; it only appears in the icon, the splash screen, empty states and the welcome screen.
- `brandBeak` is used exclusively for the beak.
- Its proportions and color are not changed.

## Colors

The values live in `tokens.json`, in light and dark themes. The role of each one is explained in [DESIGN_SYSTEM](DESIGN_SYSTEM.md#color). In short:

- **Owlsplit blue** `#3B4BF5` (`accent`): buttons, selection and the logo background. For text on a dark background, `#8C96FF` (`accentText`) is used.
- **Beak amber** `#E8B04B` (`brandBeak`): logo only.
- **Balance green** `#087A54` (light) and `#34C38F` (dark) (`positive`): what you are owed.
- **Neutrals**: background `#F2F3F7` and `#0B0C10`, surface `#FFFFFF` and `#16181F`, text `#0B0C10` and `#F5F6FA`.

On the web, `brand.css` defines these colors as CSS variables, for example `--os-accent` or `--os-text-primary`. The names are the `tokens.json` names in kebab-case, and `style.css` maps them to the names the pages use.

## Typography

- **App**: Inter, with tabular figures for amounts.
- **Web**: Inter for body text, Bricolage Grotesque for headlines and IBM Plex Mono for data such as the SHA-256.

## Changing the brand

1. Edit `brand/tokens.json`.
2. Run `node scripts/brand.mjs --png` to regenerate the website, the Android icons (vector and PNG) and the iOS icon. The PNGs require Edge or Chrome; without `--png` it only regenerates the text files.
3. If you changed app colors, update `Tokens.kt`; `BrandTokensTest` will tell you which ones are missing. If you changed the logo geometry, update `OwlsplitLogo`.

CI runs `node scripts/brand.mjs --check` and fails if any generated file is out of date.
