# 0004 · Cuenta anónima vinculable y miembros fantasma

- **Estado:** aceptada · 2026-10-07

## Contexto
La fricción de registro es lo que más frena que un grupo adopte una app de gastos. Además, siempre hay alguien en el grupo que no quiere instalar nada.

## Decisión
- Cada instalación arranca con **Firebase Auth anónimo**, sin pantalla de registro. Desde Perfil se **vincula** Google o Apple (`linkWithCredential`), conservando uid y datos.
- Los **miembros** son entidades del grupo, independientes de los usuarios: pueden ser **fantasma** (solo nombre) y un usuario los **reclama** al unirse por invitación.
- El borrado de cuenta convierte sus miembros en fantasmas, de modo que la contabilidad del grupo no cambia.

## Consecuencias
- Riesgo de perder la cuenta anónima si se desinstala sin vincular: se mitiga con avisos para vincular en momentos clave.
- Las reglas deben distinguir uid (acceso) de miembro (contabilidad).
- Hay un flujo para entrar en una cuenta existente trayendo los grupos de la anónima ([FIREBASE.md](../FIREBASE.md#autenticación)).

## Alternativas descartadas
- **Registro obligatorio**: más simple de asegurar, pero más fricción.
- **Solo miembros con cuenta**: inviable para una cena con alguien sin la app.
